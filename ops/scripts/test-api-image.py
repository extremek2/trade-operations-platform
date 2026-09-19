#!/usr/bin/env python3
"""Exercise production guards, migrate-only and startup against a disposable database."""
import base64
import os
import secrets
import subprocess
import sys
import time

image = sys.argv[1] if len(sys.argv) == 2 else 'trade-ops-api:stage6-test'
name = f'trade-ops-api-smoke-{os.getpid()}'
created = []

def run(*args, check=True):
    result = subprocess.run(args, capture_output=True, text=True)
    if check and result.returncode:
        raise RuntimeError('Test command failed: ' + args[0])
    return result

# No network and no database: development secrets must fail before connection attempts.
invalid = run('docker', 'run', '--rm', '--network', 'none', '-e', 'SPRING_PROFILES_ACTIVE=production', image, check=False)
assert invalid.returncode != 0
assert 'JWT_SECRET must be a generated production secret' in invalid.stdout + invalid.stderr
assert 'HikariPool' not in invalid.stdout + invalid.stderr

try:
    run('docker', 'network', 'create', name)
    database = name + '-db'
    password = secrets.token_urlsafe(32)
    runtime_password = secrets.token_urlsafe(32)
    run('docker', 'run', '-d', '--name', database, '--network', name, '--network-alias', 'postgres',
        '--tmpfs', '/var/lib/postgresql/data', '-e', 'POSTGRES_DB=fixture', '-e', 'POSTGRES_USER=fixture',
        '-e', 'POSTGRES_PASSWORD=' + password, '-e', 'APP_DB_USER=runtime', '-e', 'APP_DB_PASSWORD=' + runtime_password, 'postgres:15-alpine')
    created.append(database)
    for attempt in range(90):
        ready = run('docker', 'exec', database, 'pg_isready', '-h', '127.0.0.1', '-U', 'fixture', '-d', 'fixture', check=False)
        if ready.returncode == 0:
            break
        if attempt == 89:
            raise RuntimeError('Fixture database did not become ready')
        time.sleep(0.2)
    environment = {
        'SPRING_PROFILES_ACTIVE': 'production',
        'SPRING_DATASOURCE_URL': 'jdbc:postgresql://postgres:5432/fixture',
        'SPRING_DATASOURCE_USERNAME': 'fixture', 'SPRING_DATASOURCE_PASSWORD': password,
        'JWT_SECRET': secrets.token_urlsafe(48),
        'MAIL_OUTBOX_KEY': base64.b64encode(secrets.token_bytes(32)).decode(),
        'APP_ALLOWED_ORIGINS': 'https://trade.example.com',
        'MAIL_VERIFICATION_URL': 'https://trade.example.com/verify-email',
        'MAIL_CASE_ACCESS_URL': 'https://trade.example.com/external-access',
        'MAIL_DISPATCH_ENABLED': 'false',
    }
    env_args = [argument for key, value in environment.items() for argument in ('-e', key + '=' + value)]
    migration = run('docker', 'run', '--rm', '--network', name, *env_args, image, '--migrate-only')
    assert 'Tomcat started' not in migration.stdout
    versions = run('docker', 'exec', database, 'psql', '-U', 'fixture', '-d', 'fixture', '-Atc',
                   'SELECT count(*) FROM flyway_schema_history WHERE success').stdout.strip()
    assert versions == '2'
    run('docker', 'cp', 'ops/sql/grant-runtime-role.sql', database + ':/tmp/grant-runtime-role.sql')
    run('docker', 'exec', database, 'psql', '-U', 'fixture', '-d', 'fixture', '-f', '/tmp/grant-runtime-role.sql')
    environment['SPRING_DATASOURCE_USERNAME'] = 'runtime'
    environment['SPRING_DATASOURCE_PASSWORD'] = runtime_password
    env_args = [argument for key, value in environment.items() for argument in ('-e', key + '=' + value)]
    api = name + '-api'
    run('docker', 'run', '-d', '--name', api, '--network', name, *env_args, image)
    created.append(api)
    for attempt in range(100):
        ready = run('docker', 'exec', api, 'curl', '--fail', '--silent', 'http://localhost:8080/actuator/health/readiness', check=False)
        if ready.returncode == 0:
            assert 'UP' in ready.stdout
            break
        if attempt == 99:
            raise RuntimeError('Production API did not become ready')
        time.sleep(0.3)
    cors = run('docker', 'exec', api, 'curl', '--silent', '-i', '-X', 'OPTIONS',
               '-H', 'Origin: https://trade.example.com', '-H', 'Access-Control-Request-Method: POST',
               'http://localhost:8080/api/v1/auth/login').stdout.lower()
    assert 'access-control-allow-origin: https://trade.example.com' in cors
    cookie = run('docker', 'exec', api, 'curl', '--silent', '-i', '-X', 'POST',
                 'http://localhost:8080/api/v1/auth/logout').stdout.lower()
    assert all(value in cookie for value in ('secure', 'httponly', 'samesite=strict', 'path=/api/v1/auth'))
    print('PASS: production fail-fast, non-web migration V1-V10, restricted DB role readiness, HTTPS CORS and secure cookie')
finally:
    for container in reversed(created):
        subprocess.run(['docker', 'rm', '-fv', container], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    subprocess.run(['docker', 'network', 'rm', name], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
