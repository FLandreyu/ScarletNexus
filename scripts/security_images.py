"""Build and verify local patched tools; never fall back to upstream floating tags."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parent.parent
LOCK = ROOT / 'security/toolchain.lock.json'


def specification():
    return json.loads(LOCK.read_text(encoding='utf8'))


def verify_patch():
    manifest = json.loads((ROOT / 'security/ryuk/lock.json').read_text(encoding='utf8'))
    if manifest['schema'] != 1 or manifest['upstreamVersion'] != specification()['sources']['ryuk']['version']:
        raise ValueError('Unsupported Ryuk patch lock')
    expected = {'security/ryuk/' + name for name in ('sdk.patch', 'go.mod', 'go.sum', 'LICENSE', 'patched.sha256')}
    if set(manifest['files']) != expected:
        raise ValueError('Incomplete Ryuk patch inventory')
    for name, checksum in manifest['files'].items():
        if hashlib.sha256((ROOT / name).read_bytes()).hexdigest() != checksum:
            raise ValueError('Ryuk patch asset checksum differs: ' + name)
    checks = ''.join(checksum + '  ' + name + '\n' for name, checksum in sorted(manifest['patchedFiles'].items()))
    if (ROOT / 'security/ryuk/patched.sha256').read_text(encoding='utf8') != checks:
        raise ValueError('Patched Ryuk source checksums differ')
    return manifest


def recipe_hash():
    spec = specification()
    digest = hashlib.sha256()
    patch_lock = 'security/ryuk/lock.json'
    patch = json.loads((ROOT / patch_lock).read_text(encoding='utf8'))
    for name in ('security/toolchain.lock.json', 'security/images.lock.json', spec['dockerfile'], '.dockerignore',
                 patch_lock, *sorted(patch['files'])):
        digest.update(name.encode() + b'\0' + (ROOT / name).read_bytes() + b'\0')
    return digest.hexdigest()


def inspect(reference):
    result = subprocess.run(['docker', 'image', 'inspect', reference], stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, check=False)
    if result.returncode:
        raise ValueError('Build the reviewed tool images first: ' + reference)
    return json.loads(result.stdout)[0]


def resolve(role):
    spec = specification()
    reference = spec['images'][role]
    info = inspect(reference)
    labels = info['Config'].get('Labels') or {}
    if (info['Os'] != 'linux' or info['Architecture'] != 'amd64'
            or labels.get('io.scarletnexus.recipe.sha256') != recipe_hash()
            or labels.get('io.scarletnexus.tool') != role):
        raise ValueError('Tool image source identity differs from the reviewed recipe: ' + role)
    return info['Id']


def build_target(role, reference):
    spec = specification()
    network = os.environ.get('SCARLET_BUILD_NETWORK', 'default')
    if network not in ('default', 'host'):
        raise ValueError('Unsupported explicit build network mode')
    network_args = [] if network == 'default' else ['--network', 'host', '--build-arg', 'HTTP_PROXY',
                                                   '--build-arg', 'HTTPS_PROXY', '--build-arg', 'NO_PROXY']
    subprocess.run(['docker', 'build', '--platform', spec['verifiedPlatform'],
                    '--file', str(ROOT / spec['dockerfile']), '--target', role,
                    '--build-arg', 'RECIPE_SHA=' + recipe_hash(), '--tag', reference, *network_args, str(ROOT)],
                   cwd=ROOT, check=True)


def build():
    verify_patch()
    spec = specification()
    for role, reference in spec['images'].items():
        try:
            resolve(role)
        except ValueError:
            build_target(role, reference)
        print(json.dumps({'role': role, 'image': resolve(role)}), flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build', action='store_true')
    parser.add_argument('--role', choices=['maven', 'node', 'postgres', 'ryuk', 'ryuk-test'])
    args = parser.parse_args()
    if args.build:
        build()
    elif args.role:
        print(resolve(args.role))
    else:
        print(json.dumps({role: resolve(role) for role in specification()['images']}))
