"""Synthetic evidence fixtures test the reviewer; they do not prove a real scan clean."""
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import review_security_report as reviewer
import security_images
import security_scan


class SecurityReviewTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / 'security').mkdir()
        for name in ['images.lock.json', 'tools.json']:
            (self.root / 'security' / name).write_bytes((reviewer.ROOT / 'security' / name).read_bytes())
        self.directory = self.root / 'reports'
        self.directory.mkdir()
        self.now = datetime(2026, 11, 16, 12, tzinfo=timezone.utc)
        self.image = 'sha256:' + 'a' * 64
        images = json.loads((self.root / 'security/images.lock.json').read_text())['images']
        tools = json.loads((self.root / 'security/tools.json').read_text())['trivy']
        components = [{'name': 'tomcat-embed-' + name, 'version': '11.0.27'} for name in ['core', 'el', 'websocket']]
        sbom = self.write('backend-tomcat.cdx', {'components': components})
        targets = {}
        names = ['base-' + name for name in images]
        names += ['effective-' + name for name in security_images.specification()['images']]
        names += ['app-backend', 'app-web', 'app-tomcat', 'npm-all', 'npm-production']
        for name in names:
            entry = {'gated': not name.startswith('base-') or name == 'base-go'}
            if name.startswith('npm-'):
                data = {'vulnerabilities': {}, 'metadata': {'vulnerabilities':
                        dict(info=0, low=0, moderate=0, high=0, critical=0, total=0)}}
                entry['counts'] = data['metadata']['vulnerabilities']
            else:
                entry['image'] = images[name[5:]]['reference'] if name.startswith('base-') else self.image
                packages = [{'Name': 'fixture-package', 'Version': '1'}]
                target = 'fixture'
                if name == 'app-backend':
                    packages = [{'Name': n, 'Version': '1'} for n in ['org.springframework:spring-webmvc',
                                'com.fasterxml.jackson.core:jackson-databind', 'tools.jackson.core:jackson-databind',
                                'org.postgresql:postgresql']]
                if name in ('effective-postgres', 'effective-ryuk'):
                    packages = [{'Name': 'stdlib', 'Version': 'v1.27.4'}]
                    target = 'usr/local/bin/gosu' if name.endswith('postgres') else 'bin/ryuk'
                if name == 'app-tomcat':
                    packages = [{'Name': 'org.apache.tomcat.embed:' + c['name'], 'Version': c['version']} for c in components]
                    entry['sbomSha256'] = security_scan.sha(sbom)
                vulnerabilities = [{'Severity': 'HIGH'}] * (2 if name == 'effective-ryuk' else 1 if name == 'base-maven' else 0)
                data = {'ArtifactName': entry['image'], 'ArtifactType': 'container_image',
                        'Metadata': {'ImageID': self.image},
                        'Results': [{'Target': target, 'Packages': packages, 'Vulnerabilities': vulnerabilities}]}
                entry['packages'], entry['counts'] = security_scan.inspect_report(data)
            path = self.write(name, data)
            entry['reportSha256'] = security_scan.sha(path)
            targets[name] = entry
        total, gated = security_scan.gate_counts(targets)
        self.summary = {'format': 'scarlet-nexus-security-scan/v2', 'complete': True,
                        'startedAt': (self.now - timedelta(minutes=2)).isoformat(),
                        'finishedAt': (self.now - timedelta(minutes=1)).isoformat(),
                        'imageLockSha256': security_scan.sha(self.root / 'security/images.lock.json'),
                        'toolRecipeSha256': 'd' * 64, 'scannerVersion': tools['version'],
                        'scannerBinarySha256': tools['binarySha256'],
                        'gatePolicy': 'effective-artifacts-v1; all upstream findings retained',
                        'migrationImage': self.image, 'targets': targets,
                        'database': {'Version': tools['version'], 'VulnerabilityDB': {'UpdatedAt': self.now.isoformat()},
                                     'JavaDB': {'UpdatedAt': self.now.isoformat()}},
                        'highOrCritical': gated, 'allTargetsHighOrCritical': total}
        self.write('summary', self.summary)
        self.addCleanup(patch.stopall)
        patch.object(reviewer, 'ROOT', self.root).start()
        patch.object(security_images, 'recipe_hash', return_value='d' * 64).start()

    def write(self, name, data):
        path = self.directory / (name + '.json')
        path.write_text(json.dumps(data), encoding='utf8')
        return path

    def check(self):
        self.write('summary', self.summary)
        return reviewer.review(self.directory, self.now)

    def test_complete_evidence_preserves_red_gate_and_parent_counts(self):
        result = self.check()
        self.assertTrue(result['evidenceVerified'])
        self.assertFalse(result['securityGatePassed'])
        self.assertEqual((result['allTargetsHighOrCritical'], result['highOrCritical']), (3, 2))
        self.assertEqual(result['p6Acceptance'], 'not-established-by-security-review')

    def test_raw_report_tampering_is_rejected(self):
        path = self.directory / 'effective-ryuk.json'
        path.write_text(path.read_text() + ' ')
        with self.assertRaisesRegex(ValueError, 'checksum'):
            self.check()

    def test_clean_effective_artifacts_do_not_hide_parent_findings_or_establish_p6(self):
        path = self.directory / 'effective-ryuk.json'
        data = json.loads(path.read_text())
        data['Results'][0]['Vulnerabilities'] = []
        path = self.write('effective-ryuk', data)
        entry = self.summary['targets']['effective-ryuk']
        entry['reportSha256'], entry['counts'] = security_scan.sha(path), {}
        self.summary['highOrCritical'], self.summary['allTargetsHighOrCritical'] = 0, 1
        result = self.check()
        self.assertTrue(result['securityGatePassed'])
        self.assertEqual(result['allTargetsHighOrCritical'], 1)
        self.assertEqual(result['p6Acceptance'], 'not-established-by-security-review')

    def test_npm_findings_cannot_be_hidden_by_zero_metadata(self):
        path = self.directory / 'npm-all.json'
        data = json.loads(path.read_text())
        data['vulnerabilities'] = {'fixture': {'severity': 'high'}}
        path = self.write('npm-all', data)
        self.summary['targets']['npm-all']['reportSha256'] = security_scan.sha(path)
        with self.assertRaisesRegex(ValueError, 'package findings'):
            self.check()

    def test_missing_parent_target_and_demoted_effective_target_are_rejected(self):
        original = dict(self.summary['targets'])
        del self.summary['targets']['base-node']
        with self.assertRaisesRegex(ValueError, 'coverage'):
            self.check()
        self.summary['targets'] = original
        original['effective-ryuk']['gated'] = False
        with self.assertRaisesRegex(ValueError, 'classification'):
            self.check()

    def test_fabricated_green_counter_is_rejected(self):
        self.summary['highOrCritical'] = 0
        with self.assertRaisesRegex(ValueError, 'severity gate'):
            self.check()

    def test_wrong_image_identity_even_with_updated_checksum_is_rejected(self):
        path = self.directory / 'app-backend.json'
        data = json.loads(path.read_text())
        data['Metadata']['ImageID'] = 'sha256:' + 'b' * 64
        path = self.write('app-backend', data)
        self.summary['targets']['app-backend']['reportSha256'] = security_scan.sha(path)
        with self.assertRaisesRegex(ValueError, 'image identity'):
            self.check()

    def test_incomplete_stale_and_future_evidence_are_rejected(self):
        self.summary['complete'] = False
        with self.assertRaises(ValueError): self.check()
        self.summary['complete'] = True
        for offset in [-49, 1]:
            self.summary['finishedAt'] = (self.now + timedelta(hours=offset)).isoformat()
            self.summary['startedAt'] = (self.now + timedelta(hours=offset, minutes=-1)).isoformat()
            with self.subTest(offset=offset), self.assertRaises(ValueError): self.check()

    def test_stale_vulnerability_database_and_wrong_recipe_are_rejected(self):
        self.summary['database']['VulnerabilityDB']['UpdatedAt'] = (self.now - timedelta(hours=49)).isoformat()
        with self.assertRaisesRegex(ValueError, 'database'):
            self.check()
        self.summary['database']['VulnerabilityDB']['UpdatedAt'] = self.now.isoformat()
        self.summary['toolRecipeSha256'] = 'stale'
        with self.assertRaisesRegex(ValueError, 'identity'):
            self.check()


if __name__ == '__main__':
    unittest.main()
