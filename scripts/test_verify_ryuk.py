import subprocess
import unittest
from unittest.mock import patch
import verify_ryuk


class RyukRunnerTest(unittest.TestCase):
    def test_fixture_is_loaded_into_engine_before_tests(self):
        with patch.object(verify_ryuk.Path, 'is_socket', return_value=True), \
                patch.object(verify_ryuk.security_images, 'resolve', return_value='sha256:test'), \
                patch.object(verify_ryuk.subprocess, 'run') as run:
            verify_ryuk.verify()
        commands = [call.args[0] for call in run.call_args_list]
        self.assertEqual(commands[0][:4], ['docker', 'pull', '--platform', 'linux/amd64'])
        self.assertIn('@sha256:', commands[0][4])
        self.assertEqual(commands[1][:3], ['docker', 'run', '--rm'])

    def test_failed_fixture_pull_cannot_run_or_skip_tests(self):
        with patch.object(verify_ryuk.Path, 'is_socket', return_value=True), \
                patch.object(verify_ryuk.security_images, 'resolve', return_value='sha256:test'), \
                patch.object(verify_ryuk.subprocess, 'run', side_effect=subprocess.CalledProcessError(1, 'pull')) as run:
            with self.assertRaises(subprocess.CalledProcessError):
                verify_ryuk.verify()
            self.assertEqual(run.call_count, 1)


if __name__ == '__main__':
    unittest.main()
