from datetime import datetime, timedelta, timezone
import unittest
import security_scan


class SecurityScanTest(unittest.TestCase):
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
