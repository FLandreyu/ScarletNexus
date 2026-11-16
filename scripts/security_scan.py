"""Scan locked images and an isolated running Compose build, retaining every severity."""
import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import io
import re
import zipfile
import backup_restore as docker_tools
import verify_image_lock
import security_images

ROOT = Path(__file__).resolve().parent.parent


def run(args, cwd=None, allowed=(0,)):
    result = subprocess.run(args, cwd=cwd or ROOT, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode not in allowed:
        raise RuntimeError('Scan command failed: ' + args[0] + '; no clean result can be inferred')
    return result


def sha(path):
    with path.open('rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()


def inspect_report(data, backend=False):
    if not data.get('Results') or not data.get('ArtifactType'):
        raise ValueError('Incomplete image report')
    packages = [p for r in data['Results'] for p in r.get('Packages', [])]
    if not packages:
        raise ValueError('No packages detected')
    if backend:
        names = {p['Name'] for p in packages}
        required = {'org.springframework:spring-webmvc', 'com.fasterxml.jackson.core:jackson-databind',
                    'org.postgresql:postgresql'}
        if not required <= names:
            raise ValueError('Backend JAR transitive dependency coverage is incomplete')
    findings = [v for r in data['Results'] for v in r.get('Vulnerabilities', [])]
    return len(packages), dict(Counter(v['Severity'] for v in findings))


def validate_databases(database, now):
    for key, hours in [('VulnerabilityDB', 48), ('JavaDB', 96)]:
        updated = datetime.fromisoformat(database[key]['UpdatedAt'].replace('Z', '+00:00'))
        if not 0 <= (now - updated).total_seconds() <= hours * 3600:
            raise ValueError('Vulnerability database too old or from the future: ' + key)


def tomcat_sbom(image):
    # Tomcat's own JAR metadata may be absent from Trivy's Java index for a new
    # patch. Inventory the actual built fat JAR rather than declaring it clean.
    content = run(['docker', 'run', '--rm', '--network', 'none', '--read-only', '--cap-drop', 'ALL',
                   '--entrypoint', 'cat', image, '/app/app.jar']).stdout
    components = []
    with zipfile.ZipFile(io.BytesIO(content)) as archive:
        for name in archive.namelist():
            match = re.fullmatch(r'BOOT-INF/lib/(tomcat-embed-(?:core|el|websocket))-(10\.1\.[0-9]+)\.jar', name)
            if match:
                artifact, version = match.groups()
                components.append({'type': 'library', 'group': 'org.apache.tomcat.embed',
                                   'name': artifact, 'version': version,
                                   'purl': f'pkg:maven/org.apache.tomcat.embed/{artifact}@{version}',
                                   'hashes': [{'alg': 'SHA-256', 'content': hashlib.sha256(archive.read(name)).hexdigest()}]})
    if {p['name'] for p in components} != {'tomcat-embed-core', 'tomcat-embed-el', 'tomcat-embed-websocket'}:
        raise ValueError('Built backend Tomcat inventory is incomplete')
    return {'bomFormat': 'CycloneDX', 'specVersion': '1.5', 'version': 1, 'components': components}


def gate_counts(targets):
    required = {'base-go', 'effective-maven', 'effective-node', 'effective-postgres', 'effective-ryuk',
                'app-backend', 'app-web', 'app-tomcat', 'npm-all', 'npm-production'}
    if not required <= targets.keys() or any(targets[name].get('gated') is not True for name in required):
        raise ValueError('Effective artifact coverage is incomplete')
    count = lambda value: sum(value['counts'].get(level, 0) for level in ('HIGH', 'CRITICAL', 'high', 'critical'))
    return (sum(count(value) for value in targets.values()),
            sum(count(value) for value in targets.values() if value['gated']))


def scan(project, output):
    output = output.resolve()
    if not output.is_relative_to((ROOT / '.runtime').resolve()):
        raise ValueError('Reports must be inside the ignored workspace .runtime directory')
    verify_image_lock.verify()
    spec = json.loads((ROOT / 'security/tools.json').read_text(encoding='utf8'))['trivy']
    tool = ROOT / '.tools' / ('trivy-' + spec['version'] + '-linux') / 'trivy'
    if sha(tool) != spec['binarySha256']:
        raise ValueError('Scanner binary differs from the verified tool lock')
    lock = json.loads((ROOT / 'security/images.lock.json').read_text(encoding='utf8'))
    # Only identifiers are inspected; runtime container env/credentials are never copied.
    targets = {'base-' + key: (entry['reference'], entry['scope'], key == 'go')
               for key, entry in lock['images'].items()}
    for role in security_images.specification()['images']:
        targets['effective-' + role] = (security_images.resolve(role), 'effective tool/runtime', True)
    db = docker_tools.inspect(docker_tools.source(project, 'db'))
    if db['Image'] != targets['effective-postgres'][0]:
        raise ValueError('Running database does not use the verified patched image')
    for service in ('backend', 'web'):
        info = docker_tools.inspect(docker_tools.source(project, service))
        targets['app-' + service] = (info['Image'], 'runtime', True)
    output.mkdir(parents=True, exist_ok=False)
    (output / 'empty-ignore').write_text('', encoding='utf8')
    (output / 'empty-config.yaml').write_text('{}\n', encoding='utf8')
    manifest = {'format': 'scarlet-nexus-security-scan/v2', 'complete': False,
                'startedAt': datetime.now(timezone.utc).isoformat(),
                'scannerVersion': spec['version'], 'scannerBinarySha256': spec['binarySha256'],
                'imageLockSha256': sha(ROOT / 'security/images.lock.json'),
                'toolRecipeSha256': security_images.recipe_hash(),
                'gatePolicy': 'effective-artifacts-v1; all upstream findings retained', 'targets': {}}
    (output / 'summary.json').write_text(json.dumps(manifest, indent=2), encoding='utf8')
    try:
        for name, (image, scope, gated) in targets.items():
            report = output / (name + '.json')
            args = [str(tool), '--config', str(output / 'empty-config.yaml'), 'image',
                    '--image-src', 'docker,remote', '--scanners', 'vuln', '--format', 'json',
                    '--list-all-pkgs', '--timeout', '10m', '--ignorefile', str(output / 'empty-ignore'),
                    '--cache-dir', str(ROOT / '.tools/trivy-cache'), '--output', str(report), image]
            result = subprocess.run(args, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            (output / (name + '.log')).write_bytes(result.stderr)
            if result.returncode:
                raise RuntimeError('Image scan failed: ' + name)
            data = json.loads(report.read_text(encoding='utf8'))
            packages, counts = inspect_report(data, backend=name == 'app-backend')
            if name in ('effective-postgres', 'effective-ryuk'):
                suffix = 'usr/local/bin/gosu' if name.endswith('postgres') else 'bin/ryuk'
                versions = {p['Version'] for r in data['Results'] if r.get('Target', '').endswith(suffix)
                            for p in r.get('Packages', []) if p['Name'] == 'stdlib'}
                if versions != {'v1.27.4'}:
                    raise ValueError('Rebuilt Go binary identification is incomplete: ' + name)
            manifest['targets'][name] = {'image': image, 'scope': scope, 'gated': gated, 'packages': packages,
                                          'counts': counts, 'reportSha256': sha(report)}
            print(json.dumps({'target': name, 'scope': scope, 'counts': counts}), flush=True)
        bom = output / 'backend-tomcat.cdx.json'
        bom.write_text(json.dumps(tomcat_sbom(targets['app-backend'][0]), indent=2), encoding='utf8')
        report = output / 'app-tomcat.json'
        result = run([str(tool), '--config', str(output / 'empty-config.yaml'), 'sbom',
                      '--scanners', 'vuln', '--format', 'json', '--list-all-pkgs', '--timeout', '10m',
                      '--ignorefile', str(output / 'empty-ignore'), '--cache-dir', str(ROOT / '.tools/trivy-cache'),
                      '--output', str(report), str(bom)])
        (output / 'app-tomcat.log').write_bytes(result.stderr)
        data = json.loads(report.read_text(encoding='utf8'))
        packages, counts = inspect_report(data)
        manifest['targets']['app-tomcat'] = {'scope': 'runtime', 'gated': True, 'packages': packages, 'counts': counts,
                                             'reportSha256': sha(report), 'sbomSha256': sha(bom),
                                             'note': 'actual built nested JAR inventory supplements Java DB coverage'}
        node = targets['effective-node'][0]
        for name, extra in [('npm-all', []), ('npm-production', ['--omit=dev'])]:
            args = ['docker', 'run', '--rm', '--network', 'bridge',
                    '--mount', f'type=bind,src={ROOT / "frontend/package.json"},dst=/audit/package.json,readonly',
                    '--mount', f'type=bind,src={ROOT / "frontend/package-lock.json"},dst=/audit/package-lock.json,readonly',
                    '-w', '/audit', node, 'npm', 'audit', '--json', *extra]
            result = run(args, allowed=(0, 1))
            data = json.loads(result.stdout)
            if 'error' in data or 'metadata' not in data or 'vulnerabilities' not in data['metadata']:
                raise ValueError('npm audit failed: ' + name)
            report = output / (name + '.json')
            report.write_bytes(result.stdout)
            manifest['targets'][name] = {'scope': 'runtime' if extra else 'development', 'gated': True,
                                          'counts': data['metadata']['vulnerabilities'],
                                          'reportSha256': sha(report)}
        database = json.loads(run([str(tool), '--cache-dir', str(ROOT / '.tools/trivy-cache'),
                                   'version', '--format', 'json']).stdout)
        now = datetime.now(timezone.utc)
        validate_databases(database, now)
        manifest['database'] = database
        manifest['allTargetsHighOrCritical'], manifest['highOrCritical'] = gate_counts(manifest['targets'])
        manifest['complete'] = True
        manifest['finishedAt'] = now.isoformat()
    finally:
        (output / 'summary.json').write_text(json.dumps(manifest, indent=2), encoding='utf8')
    print(json.dumps({'complete': True, 'highOrCritical': manifest['highOrCritical'],
                      'reportDirectory': str(output)}))
    return 2 if manifest['highOrCritical'] else 0


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--project', required=True)
    parser.add_argument('--output', type=Path, required=True)
    arguments = parser.parse_args()
    try:
        sys.exit(scan(arguments.project, arguments.output))
    except Exception as error:
        print('Security scan incomplete: ' + str(error), file=sys.stderr)
        sys.exit(1)
