"""Quiesced Docker backup and non-overwriting isolated restore. Python 3 stdlib + OpenSSL."""
import argparse
import hashlib
import hmac
import http.cookiejar
import json
import os
import re
from pathlib import Path
import secrets
import subprocess
import tarfile
import tempfile
import time
import urllib.request
import uuid

MAGIC = b'SNBACK1\n'


def run(args, *, data=None, output=None):
    result = subprocess.run(args, input=data, stdout=output or subprocess.PIPE,
                            stderr=subprocess.PIPE, check=False)
    if result.returncode:
        raise RuntimeError(f'Command failed: {args[0]} {args[1]} (exit {result.returncode}); details suppressed')
    return result.stdout


def docker(*args, **kwargs):
    return run(['docker', *args], **kwargs)


def inspect(container):
    return json.loads(docker('inspect', container))[0]


def source(project, service):
    ids = docker('ps', '-q', '--filter', f'label=com.docker.compose.project={project}',
                 '--filter', f'label=com.docker.compose.service={service}').decode().split()
    if len(ids) != 1:
        raise ValueError(f'Expected exactly one running {service} in named project')
    return ids[0]


def sql(cid, user, database, statement, output=None):
    return docker('exec', '-e', 'PGOPTIONS=-c timezone=UTC', cid, 'psql', '-X', '-qAt',
                  '-v', 'ON_ERROR_STOP=1', '-U', user, '-d', database, '-c', statement, output=output)


def database_manifest(cid, user, database):
    names = sql(cid, user, database,
                "SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename").decode().splitlines()
    result = {}
    for name in names:
        if not name.replace('_', '').isalnum():
            raise ValueError('Unexpected table name')
        with tempfile.TemporaryFile() as content:
            sql(cid, user, database,
                f'COPY (SELECT to_jsonb(t)::text FROM public."{name}" t ORDER BY to_jsonb(t)::text) TO STDOUT', output=content)
            content.seek(0)
            digest = hashlib.file_digest(content, 'sha256').hexdigest()
        result[name] = {'rows': int(sql(cid, user, database, f'SELECT count(*) FROM public."{name}"')),
                        'sha256': digest}
    return result


def sha(path):
    with open(path, 'rb') as handle:
        return hashlib.file_digest(handle, 'sha256').hexdigest()


def attachment_manifest(path):
    result = {}
    with tarfile.open(path) as archive:
        for member in archive:
            name = member.name.removeprefix('./')
            if name in ('', '.') and member.isdir():
                continue
            if not member.isfile() or '/' in name or '\\' in name or name in ('..', '') or name in result:
                raise ValueError('Attachment archive contains unsafe or duplicate entries')
            with archive.extractfile(member) as handle:
                result[name] = {'size': member.size,
                                'sha256': hashlib.file_digest(handle, 'sha256').hexdigest()}
    return result


def password(path):
    original = Path(path)
    if original.is_symlink():
        raise ValueError('Password file cannot be a link')
    p = original.resolve(strict=True)
    value = p.read_bytes().rstrip(b'\r\n')
    if not 32 <= len(value) <= 1024 or b'\n' in value or b'\r' in value:
        raise ValueError('Use a private single-line password file with 32..1024 bytes')
    return p, value


def check_references(cid, user, database, files):
    rows = sql(cid, user, database, "SELECT json_build_object('key',storage_key,'sha256',checksum,'size',size_bytes)::text FROM attachments WHERE status IN ('READY','ARCHIVED')").decode().splitlines()
    for row in rows:
        reference = json.loads(row)
        if files.get(reference['key']) != {'size': reference['size'], 'sha256': reference['sha256']}:
            raise ValueError('A stored attachment is missing or differs from its database checksum')


def mac_file(key, header, path):
    digest = hmac.new(key, header, hashlib.sha256)
    with open(path, 'rb') as handle:
        while chunk := handle.read(1024 * 1024):
            digest.update(chunk)
    return digest.digest()


def seal(raw, target, secret_file, secret, work):
    cipher = work / 'cipher.bin'
    run(['openssl', 'enc', '-aes-256-cbc', '-pbkdf2', '-iter', '200000', '-salt',
         '-pass', f'file:{secret_file}', '-in', str(raw), '-out', str(cipher)])
    salt = secrets.token_bytes(32)
    header = MAGIC + salt
    key = hashlib.pbkdf2_hmac('sha256', secret, salt, 200000, 32)
    tag = mac_file(key, header, cipher)
    # Exclusive creation: never replace a previous backup.
    with open(target, 'xb') as out, open(cipher, 'rb') as incoming:
        os.chmod(target, 0o600)
        out.write(header + tag)
        while chunk := incoming.read(1024 * 1024):
            out.write(chunk)


def unseal(source_file, secret_file, secret, work):
    cipher = work / 'cipher.bin'
    with open(source_file, 'rb') as incoming, open(cipher, 'xb') as out:
        header = incoming.read(len(MAGIC) + 32)
        tag = incoming.read(32)
        if not header.startswith(MAGIC) or len(header) != len(MAGIC) + 32 or len(tag) != 32:
            raise ValueError('Invalid backup header')
        while chunk := incoming.read(1024 * 1024):
            out.write(chunk)
    key = hashlib.pbkdf2_hmac('sha256', secret, header[len(MAGIC):], 200000, 32)
    if not hmac.compare_digest(tag, mac_file(key, header, cipher)):
        raise ValueError('Backup authentication failed; no restore resources created')
    raw = work / 'payload.tar'
    run(['openssl', 'enc', '-d', '-aes-256-cbc', '-pbkdf2', '-iter', '200000',
         '-pass', f'file:{secret_file}', '-in', str(cipher), '-out', str(raw)])
    with tarfile.open(raw) as archive:
        members = archive.getmembers()
        if {m.name for m in members} != {'database.dump', 'attachments.tar', 'manifest.json'} or len(members) != 3:
            raise ValueError('Unexpected backup payload')
        for member in members:
            if not member.isfile():
                raise ValueError('Backup payload must contain regular files')
            with archive.extractfile(member) as src, open(work / member.name, 'xb') as dst:
                while chunk := src.read(1024 * 1024):
                    dst.write(chunk)
    manifest = json.loads((work / 'manifest.json').read_text())
    if manifest['format'] != 'scarlet-nexus-full-backup/v1':
        raise ValueError('Unsupported backup format')
    for name in ('database.dump', 'attachments.tar'):
        if sha(work / name) != manifest['files'][name]:
            raise ValueError('Backup checksum mismatch')
    if attachment_manifest(work / 'attachments.tar') != manifest['attachments']:
        raise ValueError('Attachment manifest mismatch')
    return manifest


def backup(args, work, secret_file, secret):
    db, app, web = (source(args.project, s) for s in ('db', 'backend', 'web'))
    db_info, app_info = inspect(db), inspect(app)
    env = dict(item.split('=', 1) for item in db_info['Config']['Env'])
    user, database = env['POSTGRES_USER'], env['POSTGRES_DB']
    volume = next(m['Name'] for m in app_info['Mounts']
                  if m['Destination'] == '/var/lib/scarlet/attachments' and m['Type'] == 'volume')
    stopped = []
    try:
        for cid in (web, app):
            docker('stop', '--time', '60', cid)
            stopped.append(cid)
        running = sql(db, user, database, "SELECT (SELECT count(*) FROM ai_generation_jobs WHERE state='RUNNING') + (SELECT count(*) FROM tutor_turns WHERE state='RUNNING')")
        if int(running):
            raise ValueError('Resolve interrupted AI runs before creating a full backup')
        if int(sql(db, user, database, "SELECT count(*) FROM learning_sessions WHERE state='RUNNING'")):
            raise ValueError('Pause or finish active learning timers before backup')
        with open(work / 'database.dump', 'xb') as out:
            docker('exec', db, 'pg_dump', '-U', user, '-d', database, '-Fc', '--no-owner', '--no-acl', output=out)
        with open(work / 'attachments.tar', 'xb') as out:
            docker('run', '--rm', '--network', 'none', '--mount', f'type=volume,src={volume},dst=/files,readonly',
                   db_info['Image'], 'tar', '-cf', '-', '-C', '/files', '.', output=out)
        manifest = {'format': 'scarlet-nexus-full-backup/v1',
                    'createdAt': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()),
                    'postgresImage': db_info['Image'], 'backendImage': app_info['Image'],
                    'database': database, 'user': user,
                    'tables': database_manifest(db, user, database),
                    'attachments': attachment_manifest(work / 'attachments.tar'),
                    'files': {n: sha(work / n) for n in ('database.dump', 'attachments.tar')}}
        check_references(db, user, database, manifest['attachments'])
        (work / 'manifest.json').write_text(json.dumps(manifest), encoding='utf8')
        with tarfile.open(work / 'payload.tar', 'w') as archive:
            for name in ('database.dump', 'attachments.tar', 'manifest.json'):
                archive.add(work / name, arcname=name, recursive=False)
        seal(work / 'payload.tar', args.output, secret_file, secret, work)
        print(json.dumps({'backup': str(args.output), 'tables': len(manifest['tables']),
                          'attachments': len(manifest['attachments']), 'sha256': sha(args.output)}))
    finally:
        # Restore exactly the services stopped here, even after dump/encryption failure.
        for cid in reversed(stopped):
            docker('start', cid)


def restore(args, work, secret_file, secret):
    manifest = unseal(args.input, secret_file, secret, work)
    if args.command == 'verify':
        print(json.dumps({'authenticated': True, 'tables': len(manifest['tables']),
                          'attachments': len(manifest['attachments'])}))
        return
    backend_image = getattr(args, 'backend_image', None) or manifest['backendImage']
    if not re.fullmatch(r'sha256:[0-9a-f]{64}', backend_image):
        raise ValueError('Restore application must use a local immutable image ID')
    subprocess.run(['docker', 'image', 'inspect', backend_image], check=True,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    prefix = 'scarlet-restore-' + uuid.uuid4().hex[:16]
    network, db, app = prefix, prefix + '-db', prefix + '-app'
    volumes = [prefix + '-pg', prefix + '-files']
    created_containers, created_volumes = [], []
    created_network = False
    try:
        docker('network', 'create', '--internal', network)
        created_network = True
        for volume in volumes:
            if subprocess.run(['docker', 'volume', 'inspect', volume], stdout=subprocess.DEVNULL,
                              stderr=subprocess.DEVNULL).returncode == 0:
                raise ValueError('Refusing an existing restore volume')
            docker('volume', 'create', volume)
            created_volumes.append(volume)
        db_password = secrets.token_urlsafe(40)
        env = work / 'db.env'
        env.write_text(f'POSTGRES_USER={manifest["user"]}\nPOSTGRES_DB={manifest["database"]}\nPOSTGRES_PASSWORD={db_password}\n')
        docker('run', '-d', '--name', db, '--network', network, '--env-file', str(env),
               '--mount', f'type=volume,src={volumes[0]},dst=/var/lib/postgresql/data', manifest['postgresImage'])
        created_containers.append(db)
        for _ in range(60):
            if subprocess.run(['docker','exec',db,'pg_isready','-h','127.0.0.1','-U',manifest['user'],'-d',manifest['database']], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0:
                break
            time.sleep(1)
        else:
            raise RuntimeError('Restored database did not become ready')
        with open(work / 'database.dump', 'rb') as incoming:
            result = subprocess.run(['docker','exec','-i',db,'pg_restore','--exit-on-error','--no-owner','--no-acl','-U',manifest['user'],'-d',manifest['database']], stdin=incoming, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
            if result.returncode:
                raise RuntimeError('pg_restore failed; details suppressed')
        if database_manifest(db, manifest['user'], manifest['database']) != manifest['tables']:
            raise ValueError('Restored database rows/UUID/content do not match')
        with open(work / 'attachments.tar', 'rb') as incoming:
            result = subprocess.run(['docker','run','--rm','-i','--network','none','--mount',f'type=volume,src={volumes[1]},dst=/files',manifest['postgresImage'],'tar','-xf','-','-C','/files'], stdin=incoming, stderr=subprocess.PIPE)
            if result.returncode:
                raise RuntimeError('Attachment restore failed')
        check = work / 'restored-files.tar'
        with open(check, 'xb') as out:
            docker('run','--rm','--network','none','--mount',f'type=volume,src={volumes[1]},dst=/files,readonly',manifest['postgresImage'],'tar','-cf','-','-C','/files','.',output=out)
        if attachment_manifest(check) != manifest['attachments']:
            raise ValueError('Restored attachments differ')
        check_references(db, manifest['user'], manifest['database'], manifest['attachments'])
        env.write_text(f'DATABASE_URL=jdbc:postgresql://{db}:5432/{manifest["database"]}\nDATABASE_USERNAME={manifest["user"]}\nDATABASE_PASSWORD={db_password}\nSERVER_ADDRESS=0.0.0.0\nAPI_DOCS_ENABLED=false\n')
        docker('run','-d','--name',app,'--network',network,'--env-file',str(env),
               '--mount',f'type=volume,src={volumes[1]},dst=/var/lib/scarlet/attachments',backend_image)
        created_containers.append(app)
        address = inspect(app)['NetworkSettings']['Networks'][network]['IPAddress']
        url = f'http://{address}:8080'
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}),
                    urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        for _ in range(60):
            try:
                with opener.open(url + '/actuator/health/readiness', timeout=2) as response:
                    if response.status == 200: break
            except (OSError, ValueError):
                pass
            time.sleep(1)
        else: raise RuntimeError('Restored application did not become ready')
        images_read = 0
        if args.login_password_file:
            csrf = json.load(opener.open(url + '/api/v1/auth/csrf'))
            login = json.dumps({'email':args.login_email,'password':Path(args.login_password_file).read_text().strip()}).encode()
            opener.open(urllib.request.Request(url+'/api/v1/auth/login', data=login, headers={'Content-Type':'application/json',csrf['headerName']:csrf['token']})).close()
            snapshot = json.load(opener.open(url+'/api/v1/data/snapshot'))
            if not snapshot['tables']['learning_tasks']:
                raise ValueError('Acceptance account has no restored learning tasks')
            for item in snapshot['tables']['attachments']:
                if item['status'] == 'READY':
                    content = opener.open(url+'/api/v1/attachments/'+item['id']+'/content').read()
                    if hashlib.sha256(content).hexdigest() != item['checksum']:
                        raise ValueError('Authenticated restored image differs')
                    images_read += 1
        print(json.dumps({'isolatedRestore':True,'allTablesMatched':True,'allFilesMatched':True,
                          'applicationReady':True,'authenticatedRead':bool(args.login_password_file),
                          'authenticatedImagesRead':images_read,
                          'backupBackendImage':manifest['backendImage'], 'testedBackendImage':backend_image,
                          'tables':len(manifest['tables']),'attachments':len(manifest['attachments'])}))
    finally:
        # Names are freshly generated here; never accept a user-supplied restore target.
        for cid in reversed(created_containers): docker('rm','-f',cid)
        for volume in reversed(created_volumes): docker('volume','rm',volume)
        if created_network: docker('network','rm',network)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['backup','verify','drill'])
    parser.add_argument('--password-file', required=True)
    parser.add_argument('--project')
    parser.add_argument('--output', type=Path)
    parser.add_argument('--input', type=Path)
    parser.add_argument('--login-email')
    parser.add_argument('--login-password-file')
    parser.add_argument('--backend-image', help='Optional local sha256 image ID for an isolated upgrade drill')
    args = parser.parse_args()
    if args.backend_image and args.command != 'drill':
        parser.error('--backend-image only applies to isolated drill')
    if args.command == 'backup' and (not args.project or not args.output or args.output.exists()):
        parser.error('backup requires a source project and a new output path')
    if args.command != 'backup' and not args.input:
        parser.error('verify/drill requires --input')
    if bool(args.login_email) != bool(args.login_password_file):
        parser.error('login email and password file must be specified together')
    os.umask(0o077)
    secret_file, secret = password(args.password_file)
    with tempfile.TemporaryDirectory(prefix='scarlet-backup-') as folder:
        work = Path(folder)
        if args.command == 'backup': backup(args, work, secret_file, secret)
        else: restore(args, work, secret_file, secret)


if __name__ == '__main__':
    try: main()
    except Exception as error:
        raise SystemExit(f'{type(error).__name__}: {error}') from None
