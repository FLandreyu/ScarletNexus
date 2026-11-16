"""Record binary symbol and daemon-version evidence without suppressing CVE reports."""
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile
import security_images


def verify():
    version = subprocess.check_output(['docker', 'version', '--format', '{{.Server.Version}}'], text=True).strip()
    match = re.fullmatch(r'(\d+)\.(\d+)\.(\d+)', version)
    if not match or tuple(map(int, match.groups())) < (29, 5, 1):
        raise ValueError('Daemon advisory assessment requires a verified stable Docker Engine >=29.5.1')
    image = security_images.resolve('ryuk')
    spec = security_images.specification()
    roots = security_images.ROOT
    go_image = json.loads((roots / 'security/images.lock.json').read_text(encoding='utf8'))['images']['go']['reference']
    container = subprocess.check_output(['docker', 'create', '--network', 'none', image], text=True).strip()
    try:
        with tempfile.TemporaryDirectory(prefix='ryuk-proof-', dir=roots / '.runtime') as directory:
            binary = Path(directory) / 'ryuk'
            subprocess.run(['docker', 'cp', container + ':/bin/ryuk', str(binary)], check=True, stdout=subprocess.DEVNULL)
            symbols = subprocess.check_output(['docker', 'run', '--rm', '--network', 'none',
                                              '--mount', f'type=bind,src={binary},dst=/proof/ryuk,readonly',
                                              go_image, 'go', 'tool', 'nm', '/proof/ryuk'], text=True)
            if 'github.com/docker/docker/client.' not in symbols:
                raise ValueError('Client symbols missing; cannot establish binary coverage')
            if re.search(r'github\.com/(?:docker/docker|moby/moby(?:/v2)?)/(?:daemon|builder|libcontainerd)[/.]', symbols):
                raise ValueError('Daemon implementation symbols present; reassess advisories')
            return {'image': image, 'recipeSha256': security_images.recipe_hash(), 'dockerEngine': version,
                    'binarySha256': hashlib.sha256(binary.read_bytes()).hexdigest(),
                    'sourceCommit': spec['sources']['ryuk']['commit'], 'clientSymbolsPresent': True,
                    'daemonImplementationSymbolsPresent': False,
                    'advisories': ['CVE-2026-41567', 'CVE-2026-42306'],
                    'changesScannerGate': False}
    finally:
        subprocess.run(['docker', 'rm', container], check=True, stdout=subprocess.DEVNULL)


if __name__ == '__main__':
    print(json.dumps(verify()))
