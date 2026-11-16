import copy
import tempfile
import shutil
from pathlib import Path
import unittest
from unittest.mock import patch
import security_images


class SecurityImagesTest(unittest.TestCase):
    def test_patch_tampering_is_rejected_and_changes_the_image_identity(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            shutil.copytree(security_images.ROOT / 'security', root / 'security')
            shutil.copytree(security_images.ROOT / 'docker', root / 'docker')
            shutil.copyfile(security_images.ROOT / '.dockerignore', root / '.dockerignore')
            with patch.object(security_images, 'ROOT', root):
                security_images.verify_patch()
                before = security_images.recipe_hash()
                source = root / 'security/ryuk/sdk.patch'
                source.write_bytes(source.read_bytes() + b'\n')
                self.assertNotEqual(before, security_images.recipe_hash())
                with self.assertRaisesRegex(ValueError, 'checksum'):
                    security_images.verify_patch()

    def test_stale_wrong_role_and_wrong_platform_images_are_rejected(self):
        reviewed = {'Id': 'sha256:reviewed', 'Os': 'linux', 'Architecture': 'amd64',
                    'Config': {'Labels': {'io.scarletnexus.recipe.sha256': 'recipe',
                                          'io.scarletnexus.tool': 'postgres'}}}
        with patch.object(security_images, 'recipe_hash', return_value='recipe'):
            with patch.object(security_images, 'inspect', return_value=reviewed):
                self.assertEqual(security_images.resolve('postgres'), 'sha256:reviewed')
            for field, value in [('recipe.sha256', 'stale'), ('tool', 'maven')]:
                changed = copy.deepcopy(reviewed)
                changed['Config']['Labels']['io.scarletnexus.' + field] = value
                with self.subTest(field=field), patch.object(security_images, 'inspect', return_value=changed):
                    with self.assertRaises(ValueError):
                        security_images.resolve('postgres')
            for field, value in [('Os', 'windows'), ('Architecture', 'arm64'), ('Config', {})]:
                changed = copy.deepcopy(reviewed)
                changed[field] = value
                with self.subTest(field=field), patch.object(security_images, 'inspect', return_value=changed):
                    with self.assertRaises(ValueError):
                        security_images.resolve('postgres')

    def test_missing_reviewed_image_does_not_fall_back(self):
        with patch.object(security_images, 'inspect', side_effect=ValueError('missing')) as inspection:
            with self.assertRaisesRegex(ValueError, 'missing'):
                security_images.resolve('maven')
            inspection.assert_called_once_with(security_images.specification()['images']['maven'])


if __name__ == '__main__':
    unittest.main()
