"""Check an isolated loopback TLS gateway with certificate verification enabled."""
import argparse
import http.client
import http.cookiejar
import json
from pathlib import Path
import socket
import ssl
import urllib.error
import urllib.parse
import urllib.request


def endpoint(value):
    url = urllib.parse.urlsplit(value)
    if (url.scheme != 'https' or url.hostname not in ('localhost', '127.0.0.1', '::1')
            or url.username or url.password or url.path not in ('', '/') or url.query or url.fragment):
        raise ValueError('Use an isolated loopback HTTPS origin')
    return url


def verify(base_url, ca_file, email, password_file):
    url = endpoint(base_url)
    origin = base_url.rstrip('/')
    context = ssl.create_default_context(cafile=str(ca_file))
    jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(
        urllib.request.ProxyHandler({}), urllib.request.HTTPSHandler(context=context),
        urllib.request.HTTPCookieProcessor(jar))

    def request(path, data=None, headers=None):
        payload = None if data is None else json.dumps(data).encode()
        req = urllib.request.Request(origin + path, data=payload,
                                     headers={'Content-Type': 'application/json', **(headers or {})})
        try:
            response = opener.open(req, timeout=15)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            return response.status, response.headers, response.read()

    for version in (ssl.TLSVersion.TLSv1_2, ssl.TLSVersion.TLSv1_3):
        handshake = ssl.create_default_context(cafile=str(ca_file))
        handshake.minimum_version = handshake.maximum_version = version
        connection = http.client.HTTPSConnection(url.hostname, url.port or 443, context=handshake, timeout=10)
        try:
            connection.connect()
            assert connection.sock.version() == version.name.replace('_', '.')
        finally:
            connection.close()

    # The local test CA must not have been installed into the global trust store.
    untrusted = http.client.HTTPSConnection(url.hostname, url.port or 443,
                                          context=ssl.create_default_context(), timeout=10)
    try:
        untrusted.connect()
    except ssl.SSLCertVerificationError:
        pass
    else:
        raise AssertionError('Disposable certificate unexpectedly globally trusted')
    finally:
        untrusted.close()

    try:
        with socket.create_connection((url.hostname, url.port or 443), timeout=10) as raw:
            with context.wrap_socket(raw, server_hostname='unmatched.invalid'):
                pass
    except ssl.SSLCertVerificationError:
        pass
    else:
        raise AssertionError('Wrong certificate hostname accepted')

    plaintext = http.client.HTTPConnection(url.hostname, url.port or 443, timeout=10)
    try:
        plaintext.request('GET', '/api/v1/system/status')
        assert plaintext.getresponse().status == 400
    finally:
        plaintext.close()

    status, headers, _ = request('/')
    assert status == 200
    assert headers['Strict-Transport-Security'] == 'max-age=86400'
    assert "frame-ancestors 'none'" in headers['Content-Security-Policy']
    assert headers['X-Content-Type-Options'] == 'nosniff'
    assert request('/actuator/health')[0] == 404
    assert request('/api/v1/me')[0] == 401
    status, headers, body = request('/api/v1/auth/csrf')
    assert status == 200
    csrf = json.loads(body)
    cookies = headers.get_all('Set-Cookie') or []
    assert any('SCARLET_SESSION=' in cookie and '; Secure' in cookie
               and '; HttpOnly' in cookie and 'SameSite=Lax' in cookie for cookie in cookies)
    password = password_file.read_text(encoding='utf8').strip()
    assert request('/api/v1/auth/login', {'email': email, 'password': password})[0] == 403
    assert request('/api/v1/auth/login', {'email': email, 'password': password},
                   {csrf['headerName']: csrf['token']})[0] == 200
    assert request('/api/v1/me')[0] == 200
    assert request('/api/v1/auth/logout', {})[0] == 403
    csrf = json.loads(request('/api/v1/auth/csrf')[2])
    assert request('/api/v1/auth/logout', {}, {csrf['headerName']: csrf['token']})[0] == 204
    assert request('/api/v1/me')[0] == 401
    # Invalid CSRF never reaches the application's login bucket. A 429 here is
    # the actual gateway bucket, even while every forged address is different.
    statuses = [request('/api/v1/auth/login', {},
                        {'X-Forwarded-For': f'198.51.100.{i}',
                         'X-Real-IP': f'198.51.100.{i}',
                         'Forwarded': f'for=198.51.100.{i};proto=http'})[0]
                for i in range(16)]
    assert 429 in statuses and set(statuses) <= {403, 429}
    print(json.dumps({'tls12': True, 'tls13': True, 'certificateVerified': True,
                      'untrustedCertificateRejected': True, 'secureCookie': True,
                      'wrongHostnameRejected': True, 'plaintextRejected': True,
                      'loginCsrfLogout': True, 'gatewaySpoofedHeaderRateLimit': True}))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', required=True)
    parser.add_argument('--ca-file', type=Path, required=True)
    parser.add_argument('--email', required=True)
    parser.add_argument('--password-file', type=Path, required=True)
    args = parser.parse_args()
    verify(args.base_url, args.ca_file, args.email, args.password_file)


if __name__ == '__main__':
    main()
