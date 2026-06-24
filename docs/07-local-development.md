# Local Development

## Local Hosts

Add local tenant hostnames to `/etc/hosts`:

```text
127.0.0.1 platform.openissuer.test
127.0.0.1 business1.openissuer.test business2.openissuer.test free.openissuer.test
127.0.0.1 platform.admin.openissuer.test free.admin.openissuer.test business1.admin.openissuer.test business2.admin.openissuer.test
```

## Local HTTP

Run authorization with the local profile:

```bash
./gradlew bootRun --args="--spring.profiles.active=local"
```

Common issuer URLs:

- `http://platform.openissuer.test:9001`
- `http://free.openissuer.test:9001`
- `http://business1.openissuer.test:9001`
- `http://business2.openissuer.test:9001`

## Local HTTPS For Passkeys

Passkeys require a trusted secure browser context. Install and trust the local CA once:

```bash
brew install mkcert nss
mkcert -install
```

Create the certificate used by `application-local-https.yaml`:

```bash
mkdir -p ~/openissuer-local-certs
mkcert \
  -cert-file ~/openissuer-local-certs/openissuer.test.pem \
  -key-file ~/openissuer-local-certs/openissuer.test-key.pem \
  free.openissuer.test \
  platform.openissuer.test \
  business1.openissuer.test \
  business2.openissuer.test \
  free.admin.openissuer.test \
  platform.admin.openissuer.test \
  business1.admin.openissuer.test \
  business2.admin.openissuer.test \
  localhost \
  127.0.0.1
```

Run authorization with HTTPS:

```bash
SPRING_PROFILES_ACTIVE=local,local-https ./gradlew bootRun
```

Use:

```text
https://free.openissuer.test:9001
```

## Local HTTPS And Service Calls

The `local-https` profile points token requests to HTTPS:

```yaml
ISSUER_ADDRESS: https://platform.openissuer.test:${AUTH_SERVER_PORT}
auth-server:
  root: https://platform.openissuer.test:${AUTH_SERVER_PORT}
```

Other local services that request tokens from authorization also need compatible local HTTPS issuer settings. Otherwise a downstream service may call an HTTP endpoint on a TLS-only port and fail with:

```text
This combination of host and port requires TLS.
```

## Local Seed Data

The local profile includes organization seed data for business tenants. Seeding is delayed by `organization-seed.delay-seconds` so discovery and downstream services have time to become available.

If seeding fails with service discovery errors, verify:

- Discovery service is running.
- Downstream services are registered.
- Authorization was started after dependencies or has enough seed delay.
- Local HTTPS profile token endpoints are configured consistently.

## Browser Notes

If Chrome shows a certificate warning:

1. Run `mkcert -install`.
2. Fully quit Chrome.
3. Reopen Chrome from a clean session.

Do not test passkeys on local HTTP tenant hosts. Use HTTPS.

