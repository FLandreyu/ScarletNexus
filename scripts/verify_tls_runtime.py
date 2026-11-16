"""Read-only assertions for the resource defaults in docker-compose.tls.yml."""
import argparse
import json
import backup_restore as runtime


def verify(project):
    for service, memory, cpus, pids in (
            ('db', 512 * 1024**2, 1_000_000_000, 128),
            ('backend', 1024 * 1024**2, 2_000_000_000, 256),
            ('web', 128 * 1024**2, 500_000_000, 64)):
        info = runtime.inspect(runtime.source(project, service))
        config = info['HostConfig']
        assert config['Memory'] == memory
        assert config['NanoCpus'] == cpus
        assert config['PidsLimit'] == pids
        assert 'no-new-privileges:true' in config['SecurityOpt']
        ports = info['NetworkSettings']['Ports']
        if service == 'backend':
            assert config['ReadonlyRootfs'] and config['CapDrop'] == ['ALL']
            assert 'noexec' in config['Tmpfs']['/tmp']
            env = dict(value.split('=', 1) for value in info['Config']['Env'])
            assert env['SESSION_COOKIE_SECURE'] == 'true'
            assert env['SERVER_FORWARD_HEADERS_STRATEGY'] == 'none'
            assert not any(ports.values())
        elif service == 'web':
            assert ports.get('80/tcp') is None
            assert set(key for key, value in ports.items() if value) == {'443/tcp'}
            assert len(ports['443/tcp']) == 1 and ports['443/tcp'][0]['HostIp'] == '127.0.0.1'
            runtime.docker('exec', info['Id'], 'nginx', '-t')
        else:
            assert not any(ports.values())
        print(json.dumps({'service': service, 'memory': memory, 'nanoCpus': cpus,
                          'pids': pids, 'verified': True}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--project', required=True)
    verify(parser.parse_args().project)
