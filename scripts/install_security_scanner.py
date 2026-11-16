"""Install the checksummed Linux/amd64 scanner into workspace .tools only."""
import hashlib
import json
from pathlib import Path
import platform
import shutil
import tarfile
import tempfile
import urllib.request

ROOT = Path(__file__).resolve().parent.parent


def sha(path):
    with path.open('rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()


def install():
    if platform.system() != 'Linux' or platform.machine() not in ('x86_64', 'amd64'):
        raise RuntimeError('Use a Linux/amd64 Docker host for this pinned scanner')
    spec = json.loads((ROOT / 'security/tools.json').read_text())['trivy']
    directory = ROOT / '.tools' / ('trivy-' + spec['version'] + '-linux')
    directory.mkdir(parents=True, exist_ok=True)
    target = directory / 'trivy'
    if target.exists():
        if sha(target) != spec['binarySha256']:
            raise RuntimeError('Existing scanner hash differs; refusing to overwrite it')
        print('Pinned scanner binary verified')
        return
    with tempfile.TemporaryDirectory(prefix='install-', dir=directory) as temporary:
        work = Path(temporary)
        for name, url, expected in (
                ('archive', spec['url'], spec['archiveSha256']),
                ('checksums', spec['checksumsUrl'], spec['checksumsSha256'])):
            with urllib.request.urlopen(url, timeout=60) as incoming, (work / name).open('xb') as out:
                shutil.copyfileobj(incoming, out)
            if sha(work / name) != expected:
                raise RuntimeError('Official release asset hash mismatch')
        filename = spec['url'].rsplit('/', 1)[1]
        expected_line = spec['archiveSha256'] + '  ' + filename
        if expected_line not in (work / 'checksums').read_text().splitlines():
            raise RuntimeError('Archive absent from the verified checksum list')
        with tarfile.open(work / 'archive', 'r:gz') as archive:
            member = archive.getmember('trivy')
            if not member.isfile():
                raise RuntimeError('Scanner archive member is not a regular file')
            with archive.extractfile(member) as incoming, (work / 'trivy').open('xb') as out:
                shutil.copyfileobj(incoming, out)
        if sha(work / 'trivy') != spec['binarySha256']:
            raise RuntimeError('Scanner binary hash mismatch')
        # Exclusive creation never replaces a previously installed binary.
        with (work / 'trivy').open('rb') as incoming, target.open('xb') as out:
            shutil.copyfileobj(incoming, out)
        target.chmod(0o755)
    print('Pinned scanner installed and verified')


if __name__ == '__main__':
    install()
