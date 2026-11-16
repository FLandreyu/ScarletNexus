"""Reconstruct and test the exact locked Ryuk patch against the local Linux Docker daemon."""
import os
import json
from pathlib import Path
import subprocess
import security_images


def verify():
    security_images.verify_patch()
    endpoint = os.environ.get('DOCKER_HOST', 'unix:///var/run/docker.sock')
    if not endpoint.startswith('unix://') or not Path(endpoint[7:]).is_socket():
        raise ValueError('Ryuk tests require a local Linux Unix-socket daemon')
    try:
        image = security_images.resolve('ryuk-test')
    except ValueError:
        security_images.build_target('ryuk-test', security_images.specification()['images']['ryuk-test'])
        image = security_images.resolve('ryuk-test')
    # BuildKit's parent cache is not necessarily loaded into the Engine store.
    # The first resource-isolation test creates a container before upstream's
    # pull-on-missing helper runs, so prepare its immutable fixture explicitly.
    fixture = json.loads((security_images.ROOT / 'security/images.lock.json').read_text(encoding='utf8'))['images']['go']['reference']
    subprocess.run(['docker', 'pull', '--platform', 'linux/amd64', fixture], check=True)
    subprocess.run(['docker', 'run', '--rm', '--network', 'bridge',
                    '--mount', 'type=bind,src=' + endpoint[7:] + ',dst=/var/run/docker.sock',
                    '-e', 'DOCKER_HOST=unix:///var/run/docker.sock', image], check=True)


if __name__ == '__main__':
    verify()
