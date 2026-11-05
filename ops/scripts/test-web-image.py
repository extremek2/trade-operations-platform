#!/usr/bin/env python3
"""Smoke-test the built web image using disposable Docker resources and a local certificate."""
import json
import os
from pathlib import Path
import ssl
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request

image = sys.argv[1] if len(sys.argv) == 2 else 'trade-ops-web:stage6-test'
name = f'trade-ops-web-smoke-{os.getpid()}'
created = []

def run(*args):
    return subprocess.run(args, check=True, capture_output=True, text=True).stdout.strip()

with tempfile.TemporaryDirectory(prefix='trade-ops-web-smoke-') as temporary:
    directory = Path(temporary)
    cert, key = directory / 'cert.pem', directory / 'key.pem'
    run('openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '1', '-subj', '/CN=localhost',
        '-addext', 'subjectAltName=DNS:localhost,IP:127.0.0.1', '-keyout', str(key), '-out', str(cert))
    try:
        run('docker', 'network', 'create', name)
        backend = name + '-api'
        run('docker', 'run', '-d', '--name', backend, '--network', name, '--network-alias', 'api-server', 'node:20-alpine',
            'node', '-e', 'require("http").createServer((q,s)=>{s.setHeader("Content-Type","application/json");s.end(JSON.stringify({path:q.url,forwarded:q.headers["x-forwarded-for"],proto:q.headers["x-forwarded-proto"]}));}).listen(8080,"0.0.0.0")')
        created.append(backend)
        web = name + '-web'
        run('docker', 'run', '-d', '--name', web, '--network', name, '-p', '127.0.0.1::443',
            '-v', f'{cert}:/run/tls/fullchain.pem:ro', '-v', f'{key}:/run/tls/privkey.pem:ro', image)
        created.append(web)
        port = run('docker', 'port', web, '443/tcp').split(':')[-1]
        origin = f'https://localhost:{port}'
        context = ssl.create_default_context(cafile=str(cert))
        def get(path, headers=None):
            return urllib.request.urlopen(urllib.request.Request(origin + path, headers=headers or {}), context=context, timeout=5)
        for attempt in range(30):
            try:
                with get('/') as response:
                    html = response.read()
                break
            except (urllib.error.URLError, TimeoutError):
                if attempt == 29:
                    raise
                time.sleep(0.2)
        for path in ['/verify-email', '/external-access', '/external-case', '/system-admin/applications', '/organization/members']:
            with get(path) as response:
                assert response.read() == html, path
                assert response.headers['Cache-Control'] == 'no-store', path
                assert response.headers['Referrer-Policy'] == 'no-referrer', path
        with get('/api/v1/proxy-fixture', {'X-Forwarded-For': '198.51.100.99', 'X-Forwarded-Proto': 'http'}) as response:
            result = json.load(response)
            assert result['path'] == '/api/v1/proxy-fixture'
            assert result['proto'] == 'https'
            assert result['forwarded'] != '198.51.100.99'
            assert response.headers['Cache-Control'] == 'no-store'
        for path in ['/actuator/health', '/static/missing.js']:
            try:
                get(path)
                raise AssertionError(path + ' should return 404')
            except urllib.error.HTTPError as error:
                assert error.code == 404
        print('PASS: HTTPS, SPA routes, proxy header replacement, no-store and blocked health endpoint')
    finally:
        for container in reversed(created):
            subprocess.run(['docker', 'rm', '-f', container], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        subprocess.run(['docker', 'network', 'rm', name], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
