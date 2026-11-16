"""Offline format/security checks. Real PostgreSQL restore is a separate Docker drill."""
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest
from unittest.mock import patch
from types import SimpleNamespace
import backup_restore as backup


class BackupFormatTest(unittest.TestCase):
    def test_v1_cannot_silently_downgrade_separated_roles(self):
        backup.validate_backup_format({'format': 'scarlet-nexus-full-backup/v1'})
        with self.assertRaisesRegex(ValueError, 'privilege downgrade'):
            backup.validate_backup_format({'format': 'scarlet-nexus-full-backup/v1', 'databaseRoles': 'separate-v1'})

    def test_v2_requires_readonly_identity_and_role_restoration(self):
        manifest = {'format': 'scarlet-nexus-full-backup/v2', 'databaseRoles': 'separate-v1',
                    'backupUser': 'scarlet_backup', 'backupAuthentication': 'tcp-password'}
        backup.validate_backup_format(manifest)
        for field in ['databaseRoles', 'backupUser', 'backupAuthentication']:
            changed = dict(manifest)
            changed.pop(field)
            with self.subTest(field=field), self.assertRaises(ValueError):
                backup.validate_backup_format(changed)

    def test_separated_backup_missing_database_password_never_stops_services(self):
        db = {'Config': {'Env': ['POSTGRES_USER=admin', 'POSTGRES_DB=test']}}
        app = {'Config': {'Env': ['DATABASE_USERNAME=scarlet_runtime']},
               'Mounts': [{'Name': 'files', 'Destination': '/var/lib/scarlet/attachments', 'Type': 'volume'}]}
        args = SimpleNamespace(project='isolated-test', database_password_file=None)
        with patch.object(backup, 'source', side_effect=lambda project, service: service), \
                patch.object(backup, 'inspect', side_effect=lambda cid: db if cid == 'db' else app), \
                patch.object(backup, 'docker') as docker:
            with self.assertRaisesRegex(ValueError, 'no administrator fallback'):
                backup.backup(args, Path('.'), Path('unused.key'), b'unused')
            docker.assert_not_called()

    def test_backup_connection_rejects_passwordless_trust_and_removes_client(self):
        info = {'Image': 'test-image', 'NetworkSettings': {'Networks': {'private': {'IPAddress': '172.20.0.2'}}}}
        with tempfile.TemporaryDirectory() as folder:
            work = Path(folder)
            secret = work / 'role.password'
            secret.write_text('test-only-database-password-' + 'x' * 32)
            with patch.object(backup, 'docker', side_effect=[b'owned-client', b'']) as docker, \
                    patch.object(backup.subprocess, 'run', return_value=SimpleNamespace(returncode=0)), \
                    patch.object(backup.os, 'getuid', return_value=0, create=True), \
                    patch.object(backup.os, 'getgid', return_value=0, create=True):
                with self.assertRaisesRegex(ValueError, 'trust authentication'):
                    backup.BackupConnection('db', info, 'test', secret, work)
                self.assertEqual(docker.call_args.args, ('rm', '-f', 'owned-client'))

    def test_pgpass_escapes_delimiters_and_backslashes(self):
        self.assertEqual(backup.pgpass_escape('a:b\\c'), 'a\\:b\\\\c')

    def test_upgrade_drill_rejects_mutable_image_before_creating_resources(self):
        args = SimpleNamespace(command='drill', input=Path('unused.snb'), backend_image='backend:latest')
        with patch.object(backup, 'unseal', return_value={'backendImage': 'sha256:' + 'a' * 64}), \
                patch.object(backup, 'docker') as docker:
            with self.assertRaisesRegex(ValueError, 'immutable'):
                backup.restore(args, Path('.'), Path('unused.key'), b'unused')
            docker.assert_not_called()

    def payload(self, work):
        (work / 'database.dump').write_bytes(b'format-test-only-not-a-postgres-dump')
        with tarfile.open(work / 'attachments.tar', 'w') as archive:
            member = tarfile.TarInfo('image.png')
            member.size = 4
            archive.addfile(member, io.BytesIO(b'data'))
        manifest = {'format':'scarlet-nexus-full-backup/v1',
                    'files':{name:backup.sha(work / name) for name in ('database.dump','attachments.tar')},
                    'attachments':backup.attachment_manifest(work / 'attachments.tar')}
        (work / 'manifest.json').write_text(json.dumps(manifest))
        with tarfile.open(work / 'payload.tar', 'w') as archive:
            for name in ('database.dump','attachments.tar','manifest.json'):
                archive.add(work / name, arcname=name)
        return manifest

    def test_authenticated_roundtrip_and_exclusive_output(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            secret = b'a-private-cryptographic-test-password-123456'
            key = root / 'key'
            key.write_bytes(secret)
            expected = self.payload(root)
            target = root / 'backup.snb'
            backup.seal(root / 'payload.tar', target, key, secret, root)
            with self.assertRaises(FileExistsError):
                backup.seal(root / 'payload.tar', target, key, secret, root)
            restore = root / 'restore'
            restore.mkdir()
            self.assertEqual(backup.unseal(target, key, secret, restore), expected)

    def test_wrong_password_and_tampering_fail_before_payload_extract(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            key = root / 'key'
            secret = b'a-private-cryptographic-test-password-123456'
            key.write_bytes(secret)
            self.payload(root)
            target = root / 'backup.snb'
            backup.seal(root / 'payload.tar', target, key, secret, root)
            for name, value in [('wrong', secret + b'x'), ('tampered', secret)]:
                if name == 'tampered':
                    data = bytearray(target.read_bytes())
                    data[-1] ^= 1
                    target.write_bytes(data)
                dest = root / name
                dest.mkdir()
                with self.assertRaisesRegex(ValueError, 'authentication failed'):
                    backup.unseal(target, key, value, dest)
                self.assertFalse((dest / 'database.dump').exists())

    def test_attachment_traversal_and_symlinks_are_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            for kind in ('traversal', 'link'):
                path = Path(folder) / kind
                with tarfile.open(path, 'w') as archive:
                    member = tarfile.TarInfo('../outside' if kind == 'traversal' else 'link')
                    if kind == 'link':
                        member.type = tarfile.SYMTYPE
                        member.linkname = '/outside'
                    archive.addfile(member)
                with self.assertRaises(ValueError):
                    backup.attachment_manifest(path)


if __name__ == '__main__':
    unittest.main()
