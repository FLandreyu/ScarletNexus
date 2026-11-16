import unittest
from unittest.mock import patch
import operations


class OperationsTest(unittest.TestCase):
    def test_missing_corrupt_and_orphan_are_distinct(self):
        files = {'good': {'size': 1, 'sha256': 'ok'}, 'bad': {'size': 2, 'sha256': 'bad'},
                 'orphan': {'size': 1, 'sha256': 'ok'}}
        refs = [dict(id=k, storage_key=k, size_bytes=1, checksum='ok', status='READY')
                for k in ('good','bad','missing')]
        refs.append(dict(id='q', storage_key='quarantine', status='QUARANTINED'))
        before = dict(files)
        self.assertEqual(operations.classify(files, refs),
                         {'missing':['missing'], 'corrupt':['bad'], 'orphans':['orphan']})
        self.assertEqual(files, before)

    def test_unavailable_service_is_unhealthy_without_exception_detail(self):
        with patch('operations.backup.source', side_effect=RuntimeError('sensitive connection')):
            result = operations.monitor('test')
        self.assertFalse(result['healthy'])
        self.assertNotIn('sensitive', str(result))


if __name__ == '__main__':
    unittest.main()
