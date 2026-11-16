"""Linux Docker-only gateway stream check; disposable fixtures, no published ports."""
import argparse
import json
from pathlib import Path
import socket
import ssl
import time
import uuid
import backup_restore as docker_tools


def verify(ca_file, key_file, web_image):
    root = Path(__file__).resolve().parent.parent
    name = 'scarlet-tls-test-' + uuid.uuid4().hex[:12]
    network = None
    containers = []
    try:
        network = docker_tools.docker('network', 'create', '--internal', name).decode().strip()
        fixture = docker_tools.docker(
            'run', '-d', '--network', network, '--network-alias', 'backend',
            '--name', name + '-fixture', '--memory', '256m', '--cpus', '1', '--pids-limit', '128',
            '--mount', f'type=bind,src={root / "backend/src/test/java/dev/scarletnexus/TlsStreamFixture.java"},dst=/fixture/dev/scarletnexus/TlsStreamFixture.java,readonly',
            '--entrypoint', 'java', 'maven:3.9.16-eclipse-temurin-25@sha256:93b8a14ea2f412782e4e842651273b4d903e35cc496284f178fbbe2d67d00976',
            '/fixture/dev/scarletnexus/TlsStreamFixture.java').decode().strip()
        containers.append(fixture)
        gateway = docker_tools.docker(
            'run', '-d', '--network', network, '--name', name + '-gateway',
            '--memory', '128m', '--cpus', '0.5', '--pids-limit', '64',
            '-e', 'NGINX_ENTRYPOINT_WORKER_PROCESSES_AUTOTUNE=1',
            '--mount', f'type=bind,src={root / "frontend/nginx.tls.conf"},dst=/etc/nginx/conf.d/default.conf,readonly',
            '--mount', f'type=bind,src={ca_file.resolve()},dst=/etc/nginx/tls/fullchain.pem,readonly',
            '--mount', f'type=bind,src={key_file.resolve()},dst=/etc/nginx/tls/privkey.pem,readonly',
            web_image).decode().strip()
        containers.append(gateway)
        info = docker_tools.inspect(gateway)
        address = next(iter(info['NetworkSettings']['Networks'].values()))['IPAddress']
        context = ssl.create_default_context(cafile=str(ca_file))
        for attempt in range(40):
            if docker_tools.inspect(fixture)['State']['Status'] == 'exited':
                raise RuntimeError('Disposable Java stream fixture exited before serving requests')
            try:
                # SNI and hostname verification stay localhost while the test
                # connects only through the Linux host's private Docker bridge.
                with socket.create_connection((address, 443), timeout=3) as raw:
                    with context.wrap_socket(raw, server_hostname='localhost') as tls:
                        start = time.monotonic()
                        tls.sendall(b'GET /api/test-stream HTTP/1.0\r\nHost: localhost\r\n'
                                    b'Forwarded: for=198.51.100.9\r\n'
                                    b'X-Forwarded-For: 198.51.100.9\r\n'
                                    b'X-Real-IP: 198.51.100.9\r\n'
                                    b'X-Forwarded-Proto: http\r\n'
                                    b'X-Forwarded-Port: 80\r\n'
                                    b'X-Forwarded-Host: attacker.invalid\r\n\r\n')
                        stream = tls.makefile('rb')
                        status = stream.readline()
                        if b'200' not in status:
                            time.sleep(1)
                            continue
                        while stream.readline() not in (b'\r\n', b'\n', b''):
                            pass
                        first = stream.readline()
                        sanitized = stream.readline()
                        elapsed_first = time.monotonic() - start
                        rest = stream.read()
                        elapsed_total = time.monotonic() - start
                        assert first.strip() == b'event: first'
                        assert sanitized.strip() == b'data: true'
                        assert b'event: done' in rest
                        assert elapsed_first < 1.5 and elapsed_total - elapsed_first >= 1.5
                        print(json.dumps({'delayedSseGateway': True, 'forwardedHeadersSanitized': True,
                                          'certificateVerified': True, 'publishedPorts': 0,
                                          'firstSeconds': round(elapsed_first, 3),
                                          'totalSeconds': round(elapsed_total, 3)}))
                        return
            except (OSError, ssl.SSLError):
                time.sleep(1)
        raise RuntimeError('Disposable stream fixture did not become ready')
    finally:
        for container in reversed(containers):
            docker_tools.docker('rm', '-f', container)
        if network:
            docker_tools.docker('network', 'rm', network)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ca-file', type=Path, required=True)
    parser.add_argument('--key-file', type=Path, required=True)
    parser.add_argument('--web-image', required=True)
    options = parser.parse_args()
    verify(options.ca_file, options.key_file, options.web_image)
