"""Reconstruct and test the exact locked Ryuk patch against the local Linux Docker daemon."""
import os
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
    subprocess.run(['docker', 'run', '--rm', '--network', 'bridge',
                    '--mount', 'type=bind,src=' + endpoint[7:] + ',dst=/var/run/docker.sock',
                    '-e', 'DOCKER_HOST=unix:///var/run/docker.sock', image], check=True)


if __name__ == '__main__':
    verify()
