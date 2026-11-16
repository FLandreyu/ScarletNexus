"""Check that every declared image consumer uses the reviewed immutable reference."""
import argparse
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parent.parent


def verify(remote=False):
    lock = json.loads((ROOT / 'security/images.lock.json').read_text(encoding='utf8'))
    if lock['schema'] != 1 or not lock['images']:
        raise ValueError('Unsupported or empty image lock')
    for key, entry in lock['images'].items():
        reference = entry['reference']
        if not re.fullmatch(r'[a-z0-9./_-]+:[a-zA-Z0-9._-]+@sha256:[0-9a-f]{64}', reference):
            raise ValueError('Image must have a tag and sha256 digest: ' + key)
        tag = reference.split('@')[0]
        for name in entry['consumers']:
            path = (ROOT / name).resolve()
            if not path.is_relative_to(ROOT):
                raise ValueError('Consumer outside the workspace')
            content = path.read_text(encoding='utf8')
            # The tag must never appear in a consumer without its exact digest.
            if not content.count(reference) or content.count(tag) != content.count(reference):
                raise ValueError('Missing or drifting image reference: ' + name)
        if remote:
            digest = subprocess.check_output(
                ['docker', 'buildx', 'imagetools', 'inspect', reference,
                 '--format', '{{.Manifest.Digest}}'], text=True, timeout=60).strip()
            if digest != reference.split('@')[1]:
                raise ValueError('Registry digest mismatch: ' + key)
    for pin in lock.get('runtimePackagePins', []):
        path = (ROOT / pin['consumer']).resolve()
        if not path.is_relative_to(ROOT) or (pin['package'] + '=' + pin['version']) not in path.read_text(encoding='utf8'):
            raise ValueError('Runtime package pin differs from its reviewed lock')
    tools = json.loads((ROOT / 'security/toolchain.lock.json').read_text(encoding='utf8'))
    recipe = (ROOT / tools['dockerfile']).read_text(encoding='utf8')
    for source in tools['sources'].values():
        if source['sha256'] not in recipe:
            raise ValueError('Tool asset checksum absent from its recipe')
    for role, consumers in tools['consumers'].items():
        for name in consumers:
            if tools['images'][role] not in (ROOT / name).read_text(encoding='utf8'):
                raise ValueError('Patched tool consumer differs from its lock: ' + name)
    return {'lockedImages': len(lock['images']), 'remoteVerified': remote,
            'verifiedRuntimePlatform': lock['verifiedPlatform']}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--remote', action='store_true')
    print(json.dumps(verify(parser.parse_args().remote)))
