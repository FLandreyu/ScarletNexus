"""Offline format/security checks. Real PostgreSQL restore is a separate Docker drill."""
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest
import backup_restore as backup


class BackupFormatTest(unittest.TestCase):
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
