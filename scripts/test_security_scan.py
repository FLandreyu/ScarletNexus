from datetime import datetime, timedelta, timezone
import unittest
from unittest.mock import patch
from types import SimpleNamespace
import io
import zipfile
import security_scan


class SecurityScanTest(unittest.TestCase):
    def tomcat_jar(self, versions):
        output = io.BytesIO()
        with zipfile.ZipFile(output, 'w') as archive:
            for component, version in versions.items():
                archive.writestr(f'BOOT-INF/lib/tomcat-embed-{component}-{version}.jar', component.encode())
        return SimpleNamespace(stdout=output.getvalue())

    def test_actual_tomcat11_inventory_preserves_versions_and_hashes(self):
        jar = self.tomcat_jar(dict.fromkeys(['core', 'el', 'websocket'], '11.0.27'))
        with patch.object(security_scan, 'run', return_value=jar):
            components = security_scan.tomcat_sbom('test-image')['components']
        self.assertEqual(len(components), 3)
        self.assertEqual({item['version'] for item in components}, {'11.0.27'})
        self.assertTrue(all(len(item['hashes'][0]['content']) == 64 for item in components))

    def test_missing_or_mixed_tomcat_inventory_cannot_pass(self):
        for versions in [{'core': '11.0.27'}, {'core': '11.0.27', 'el': '11.0.26', 'websocket': '11.0.27'}]:
            with patch.object(security_scan, 'run', return_value=self.tomcat_jar(versions)):
                with self.assertRaisesRegex(ValueError, 'incomplete'):
                    security_scan.tomcat_sbom('test-image')

    def test_boot4_scan_requires_both_jackson_generations(self):
        report = {'ArtifactType': 'container_image', 'Results': [{'Packages': [
            {'Name': name} for name in ['org.springframework:spring-webmvc',
                                       'com.fasterxml.jackson.core:jackson-databind',
                                       'org.postgresql:postgresql']]}]}
        with self.assertRaisesRegex(ValueError, 'coverage'):
            security_scan.inspect_report(report, backend=True)
        report['Results'][0]['Packages'].append({'Name': 'tools.jackson.core:jackson-databind'})
        self.assertEqual(security_scan.inspect_report(report, backend=True), (4, {}))

    def test_unpatched_effective_artifact_still_blocks_and_upstream_is_preserved(self):
        names = ['base-go', 'effective-maven', 'effective-node', 'effective-postgres', 'effective-ryuk',
                 'app-backend', 'app-web', 'app-tomcat', 'npm-all', 'npm-production']
        targets = {name: {'gated': True, 'counts': {}} for name in names}
        targets['base-maven'] = {'gated': False, 'counts': {'HIGH': 6}}
        targets['effective-maven']['counts'] = {'HIGH': 1}
        self.assertEqual(security_scan.gate_counts(targets), (7, 1))
        targets['effective-maven']['counts'] = {}
        self.assertEqual(security_scan.gate_counts(targets), (6, 0))
        del targets['effective-maven']
        with self.assertRaises(ValueError):
            security_scan.gate_counts(targets)

    def test_effective_artifact_cannot_be_demoted_out_of_the_gate(self):
        targets = {name: {'gated': True, 'counts': {}} for name in
                   ['base-go', 'effective-maven', 'effective-node', 'effective-postgres', 'effective-ryuk',
                    'app-backend', 'app-web', 'app-tomcat', 'npm-all', 'npm-production']}
        targets['effective-postgres']['gated'] = False
        with self.assertRaises(ValueError):
            security_scan.gate_counts(targets)

    def test_empty_or_package_free_reports_cannot_mean_clean(self):
        for report in ({}, {'ArtifactType': 'container_image', 'Results': []},
                       {'ArtifactType': 'container_image', 'Results': [{'Packages': []}]}):
            with self.subTest(report=report), self.assertRaises(ValueError):
                security_scan.inspect_report(report)

    def test_os_only_report_cannot_pass_as_backend_java_coverage(self):
        report = {'ArtifactType': 'container_image',
                  'Results': [{'Packages': [{'Name': 'busybox'}]}]}
        with self.assertRaisesRegex(ValueError, 'coverage'):
            security_scan.inspect_report(report, backend=True)

    def test_all_severities_survive_summary_without_filtering(self):
        report = {'ArtifactType': 'container_image',
                  'Results': [{'Packages': [{'Name': 'example'}],
                               'Vulnerabilities': [{'Severity': level} for level in
                                                   ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'UNKNOWN']]}]}
        packages, counts = security_scan.inspect_report(report)
        self.assertEqual(packages, 1)
        self.assertEqual(counts, {level: 1 for level in ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'UNKNOWN']})

    def test_stale_missing_and_future_databases_are_not_accepted(self):
        now = datetime(2026, 11, 16, tzinfo=timezone.utc)
        fresh = {'VulnerabilityDB': {'UpdatedAt': now.isoformat()}, 'JavaDB': {'UpdatedAt': now.isoformat()}}
        security_scan.validate_databases(fresh, now)
        for key, offset in [('VulnerabilityDB', -49), ('JavaDB', -97), ('VulnerabilityDB', 1)]:
            database = {name: dict(value) for name, value in fresh.items()}
            database[key]['UpdatedAt'] = (now + timedelta(hours=offset)).isoformat()
            with self.subTest(key=key, offset=offset), self.assertRaises(ValueError):
                security_scan.validate_databases(database, now)
        with self.assertRaises(KeyError):
            security_scan.validate_databases({}, now)


if __name__ == '__main__':
    unittest.main()
