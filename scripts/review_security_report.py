"""Recheck archived scan evidence without Docker, modifying findings or accepting risk exceptions."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import sys
import security_images
import security_scan

ROOT = Path(__file__).resolve().parent.parent


def read_json(path):
    if path.is_symlink() or not path.is_file():
        raise ValueError('Missing or linked evidence file: ' + path.name)
    return json.loads(path.read_text(encoding='utf8'))


def timestamp(value):
    result = datetime.fromisoformat(value.replace('Z', '+00:00'))
    if result.tzinfo is None:
        raise ValueError('Evidence timestamps must include a timezone')
    return result


def review(directory, now=None):
    now = now or datetime.now(timezone.utc)
    summary = read_json(directory / 'summary.json')
    if summary.get('format') != 'scarlet-nexus-security-scan/v2' or summary.get('complete') is not True:
        raise ValueError('Scan evidence is incomplete or unsupported')
    started, finished = timestamp(summary['startedAt']), timestamp(summary['finishedAt'])
    if not started <= finished <= now or (now - finished).total_seconds() > 48 * 3600:
        raise ValueError('Scan evidence is old, reversed or from the future')
    images = read_json(ROOT / 'security/images.lock.json')
    tools = read_json(ROOT / 'security/tools.json')['trivy']
    if (summary['imageLockSha256'] != security_scan.sha(ROOT / 'security/images.lock.json')
            or summary['toolRecipeSha256'] != security_images.recipe_hash()
            or summary['scannerVersion'] != tools['version']
            or summary['scannerBinarySha256'] != tools['binarySha256']
            or summary['database']['Version'] != tools['version']
            or summary['gatePolicy'] != 'effective-artifacts-v1; all upstream findings retained'):
        raise ValueError('Scan source identity or policy differs from the current locks')
    security_scan.validate_databases(summary['database'], now)
    expected = {'base-' + name for name in images['images']}
    expected |= {'effective-' + name for name in security_images.specification()['images']}
    expected |= {'app-backend', 'app-web', 'app-tomcat', 'npm-all', 'npm-production'}
    targets = summary['targets']
    if set(targets) != expected:
        raise ValueError('Scan target coverage differs from the reviewed inventory')
    for name, entry in targets.items():
        if not re.fullmatch(r'[a-z0-9-]+', name):
            raise ValueError('Unsafe evidence target name')
        gated = not name.startswith('base-') or name == 'base-go'
        if entry.get('gated') is not gated:
            raise ValueError('Target gate classification differs: ' + name)
        path = directory / (name + '.json')
        data = read_json(path)
        if security_scan.sha(path) != entry['reportSha256']:
            raise ValueError('Raw report checksum differs: ' + name)
        if name.startswith('npm-'):
            if 'error' in data or 'vulnerabilities' not in data:
                raise ValueError('npm audit evidence is incomplete')
            counts = data['metadata']['vulnerabilities']
            if any(type(v) is not int or v < 0 for v in counts.values()):
                raise ValueError('Invalid npm audit counters')
            if counts['total'] != sum(counts[k] for k in ('info', 'low', 'moderate', 'high', 'critical')):
                raise ValueError('npm audit totals differ')
            findings = data['vulnerabilities']
            if (any(item['severity'] not in counts or item['severity'] == 'total' for item in findings.values())
                    or counts['total'] != len(findings)
                    or any(counts[level] != sum(item['severity'] == level for item in findings.values())
                           for level in ('info', 'low', 'moderate', 'high', 'critical'))):
                raise ValueError('npm audit metadata differs from its package findings')
        else:
            packages, counts = security_scan.inspect_report(data, backend=name == 'app-backend')
            if packages != entry['packages']:
                raise ValueError('Package coverage counter differs: ' + name)
            if name != 'app-tomcat':
                if data.get('ArtifactName') != entry['image']:
                    raise ValueError('Artifact name differs from the scanned target')
                if name.startswith('base-'):
                    if entry['image'] != images['images'][name.removeprefix('base-')]['reference']:
                        raise ValueError('Parent image reference differs')
                elif (not re.fullmatch(r'sha256:[0-9a-f]{64}', entry['image'])
                      or data.get('Metadata', {}).get('ImageID') != entry['image']):
                    raise ValueError('Effective image identity differs')
            if name in ('effective-postgres', 'effective-ryuk'):
                suffix = 'usr/local/bin/gosu' if name.endswith('postgres') else 'bin/ryuk'
                versions = {p['Version'] for r in data['Results'] if r.get('Target', '').endswith(suffix)
                            for p in r.get('Packages', []) if p['Name'] == 'stdlib'}
                if versions != {'v1.27.4'}:
                    raise ValueError('Rebuilt Go binary coverage differs')
        if counts != entry['counts']:
            raise ValueError('Finding counters differ from raw evidence: ' + name)
    sbom = directory / 'backend-tomcat.cdx.json'
    inventory = read_json(sbom)
    components = inventory['components']
    if (security_scan.sha(sbom) != targets['app-tomcat']['sbomSha256'] or len(components) != 3
            or {c['name'] for c in components} != {'tomcat-embed-core', 'tomcat-embed-el', 'tomcat-embed-websocket'}
            or len({c['version'] for c in components}) != 1):
        raise ValueError('Actual Tomcat SBOM evidence differs')
    expected_tomcat = {'org.apache.tomcat.embed:' + c['name']: c['version'] for c in components}
    tomcat_report = read_json(directory / 'app-tomcat.json')
    observed_tomcat = {p['Name']: p['Version'] for r in tomcat_report['Results'] for p in r.get('Packages', [])}
    if observed_tomcat != expected_tomcat:
        raise ValueError('Tomcat report did not cover the inventoried versions')
    if summary.get('migrationImage') not in (None, targets['app-backend']['image']):
        raise ValueError('Migration artifact differs from backend coverage')
    total, gated = security_scan.gate_counts(targets)
    if total != summary['allTargetsHighOrCritical'] or gated != summary['highOrCritical']:
        raise ValueError('Recomputed severity gate differs from the summary')
    return {'format': 'scarlet-nexus-security-review/v1', 'evidenceVerified': True,
            'verificationScope': 'archived-report-consistency',
            'reviewedAt': now.isoformat(), 'scanFinishedAt': finished.isoformat(),
            'summarySha256': security_scan.sha(directory / 'summary.json'), 'targets': len(targets),
            'highOrCritical': gated, 'allTargetsHighOrCritical': total,
            'securityGatePassed': gated == 0, 'p6Acceptance': 'not-established-by-security-review'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report', required=True, type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    directory = args.report.resolve(strict=True)
    allowed = (ROOT / '.runtime').resolve()
    if not directory.is_relative_to(allowed) or not directory.is_dir():
        raise ValueError('Review an existing report inside workspace .runtime')
    result = review(directory)
    rendered = json.dumps(result, indent=2)
    if args.output:
        output = args.output.resolve()
        if not output.is_relative_to(allowed) or args.output.is_symlink():
            raise ValueError('Write a new review inside workspace .runtime')
        with output.open('x', encoding='utf8') as handle:
            handle.write(rendered + '\n')
    print(rendered)
    return 0 if result['securityGatePassed'] else 2


if __name__ == '__main__':
    try:
        sys.exit(main())
    except Exception as error:
        print('Security evidence review failed: ' + str(error), file=sys.stderr)
        sys.exit(1)
