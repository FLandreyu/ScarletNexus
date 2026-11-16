"""Read-only local Docker monitoring and attachment inventory. No delete action exists."""
import argparse
import json
import os
from pathlib import Path
import tempfile
import backup_restore as backup


def classify(files, references):
    missing, corrupt = [], []
    keys = set()
    for row in references:
        keys.add(row['storage_key'])
        if row['status'] == 'QUARANTINED':
            continue
        actual = files.get(row['storage_key'])
        if actual is None:
            missing.append(row['id'])
        elif actual != {'size': row['size_bytes'], 'sha256': row['checksum']}:
            corrupt.append(row['id'])
    return {'missing': missing, 'corrupt': corrupt,
            'orphans': sorted(set(files) - keys)}


def monitor(project):
    try:
        services = {name: backup.source(project, name) for name in ('db', 'backend', 'web')}
        result = {name: backup.inspect(cid)['State'].get('Health', {}).get('Status',
                  backup.inspect(cid)['State']['Status']) for name, cid in services.items()}
        backend = services['backend']
        checks = {}
        for check in ('liveness', 'readiness'):
            data = backup.docker('exec', backend, 'wget', '-qO-',
                                 'http://127.0.0.1:8080/actuator/health/' + check)
            checks[check] = json.loads(data)['status']
        gateway = json.loads(backup.docker('exec', services['web'], 'wget', '-qO-',
                             'http://127.0.0.1/api/v1/system/status'))
        checks['gateway'] = 'UP' if gateway.get('status') == 'UP' and gateway.get('database') == 'UP' else 'DOWN'
        healthy = all(v in ('healthy', 'running') for v in result.values()) and all(v == 'UP' for v in checks.values())
        return {'healthy': healthy, 'services': result, 'checks': checks}
    except (ValueError, RuntimeError, KeyError, json.JSONDecodeError):
        return {'healthy': False, 'reason': 'service-unavailable-or-unready'}


def audit(project):
    db, app = backup.source(project, 'db'), backup.source(project, 'backend')
    info = backup.inspect(db)
    env = dict(item.split('=', 1) for item in info['Config']['Env'])
    rows = backup.sql(db, env['POSTGRES_USER'], env['POSTGRES_DB'],
                      "SELECT json_build_object('id',id,'storage_key',storage_key,'checksum',checksum,'size_bytes',size_bytes,'status',status)::text FROM attachments ORDER BY id")
    references = [json.loads(row) for row in rows.decode().splitlines()]
    volume = next(m['Name'] for m in backup.inspect(app)['Mounts']
                  if m['Destination'] == '/var/lib/scarlet/attachments' and m['Type'] == 'volume')
    with tempfile.TemporaryDirectory(prefix='scarlet-audit-') as folder:
        path = Path(folder) / 'inventory.tar'
        with path.open('xb') as out:
            backup.docker('run', '--rm', '--network', 'none', '--mount',
                          f'type=volume,src={volume},dst=/files,readonly',
                          info['Image'], 'tar', '-cf', '-', '-C', '/files', '.', output=out)
        files = backup.attachment_manifest(path)
    result = classify(files, references)
    return {'format': 'scarlet-nexus-attachment-audit/v1', 'readOnly': True,
            'consistentSnapshot': False, 'references': len(references), 'files': len(files),
            **result}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['monitor', 'attachments'])
    parser.add_argument('--project', required=True)
    parser.add_argument('--report-file', type=Path)
    args = parser.parse_args()
    os.umask(0o077)
    result = monitor(args.project) if args.action == 'monitor' else audit(args.project)
    if args.report_file:
        with args.report_file.open('x', encoding='utf8') as out:
            json.dump(result, out, ensure_ascii=False, indent=2)
    if args.action == 'attachments':
        print(json.dumps({'readOnly': True, 'consistentSnapshot': False,
                          'references': result['references'], 'files': result['files'],
                          **{key: len(result[key]) for key in ('missing','corrupt','orphans')}}))
        return 2 if any(result[key] for key in ('missing','corrupt','orphans')) else 0
    print(json.dumps(result))
    return 0 if result['healthy'] else 2


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except Exception:
        raise SystemExit('Operational check failed; details suppressed') from None
