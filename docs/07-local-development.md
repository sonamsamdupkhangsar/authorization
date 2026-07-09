# Local Development

## Local Hosts

Add local tenant hostnames to `/etc/hosts`:

```text
127.0.0.1 platform.openissuer.test
127.0.0.1 business1.openissuer.test business2.openissuer.test free.openissuer.test demo.openissuer.test
127.0.0.1 platform.admin.openissuer.test free.admin.openissuer.test business1.admin.openissuer.test business2.admin.openissuer.test demo.admin.openissuer.test
```

## Local HTTPS

Run authorization with Eureka and local HTTPS for browser, tenant-host, and passkey/WebAuthn testing:

```bash
SPRING_PROFILES_ACTIVE=eureka,local-https ./gradlew bootRun
```

Common issuer URLs:

- `https://platform.openissuer.test:9001`
- `https://free.openissuer.test:9001`
- `https://business1.openissuer.test:9001`
- `https://business2.openissuer.test:9001`
- `https://demo.openissuer.test:9001`

For local HTTP only:

```bash
SPRING_PROFILES_ACTIVE=eureka ./gradlew bootRun
```

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
  demo.openissuer.test \
  free.admin.openissuer.test \
  platform.admin.openissuer.test \
  business1.admin.openissuer.test \
  business2.admin.openissuer.test \
  demo.admin.openissuer.test \
  localhost \
  127.0.0.1
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

The Eureka profile includes organization seed data for business tenants. Seeding is delayed by `organization-seed.delay-seconds` so discovery and downstream services have time to become available.

The local demo tenant requires a `demoauth` database owned by the local `test` PostgreSQL role:

```bash
createdb -h localhost -p 5432 -U "$USER" -O test demoauth
```

Local seed password sources are intentionally different from Kubernetes:

| Seed users | Local password source |
| --- | --- |
| Free, Business1, and Business2 | Literal development-only values in `application-eureka.yaml` |
| Demo | `DEMO_USER_PASSWORD` environment variable populated from macOS Keychain |

Store the demo login password once from the infrastructure repository:

```bash
cd ../do-k8-terraform-1
scripts/store-demo-user-password-in-keychain.sh
```

Before starting authorization in a new terminal, load it and start the application from that same terminal:

```bash
export DEMO_USER_PASSWORD="$(
  security find-generic-password \
    -a "$USER" \
    -s "openissuer/demo/user_password" \
    -w
)"

cd ../authorization
SPRING_PROFILES_ACTIVE=eureka,local-https ./gradlew bootRun
```

The export is required only for the local demo seed. It is not needed for the existing local Free or Business seed users.

For Kubernetes, the demo password is read directly from Keychain by `scripts/apply-authorization-seed-from-zshrc.sh` and included in the sealed `authorization-seed` secret. Kubernetes does not use the local `DEMO_USER_PASSWORD` startup export.

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
