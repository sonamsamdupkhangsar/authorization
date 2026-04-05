# Multitenant Smoke Test

This script verifies issuer-based multitenancy against a running deployment.

Script:
- [multitenant-smoke.sh](/Users/sonamsamdupkhangsar/Documents/github/authorization/scripts/multitenant-smoke.sh)

What it checks:
- discovery document issuer matches `BASE1` and `BASE2`
- each tenant can issue a token for the same `CLIENT_ID` with its own secret
- cross-tenant authentication fails with `401` and `invalid_client`

Requirements:
- `curl`
- `jq`

## Local Example

```bash
BASE1=http://business1.openissuer.test:9001 \
BASE2=http://business2.openissuer.test:9001 \
CLIENT_ID=shared-client \
SECRET1=business1-secret \
SECRET2=business2-secret \
./scripts/multitenant-smoke.sh
```

Expected result:

```text
Checking discovery documents...
Checking tenant 1 token issuance...
Checking tenant 2 token issuance...
Checking cross-tenant rejection...
Smoke test passed.
```

## Staging Or Production Example

Use dedicated smoke-test clients, not application clients.

```bash
BASE1=https://business1.openissuer.com \
BASE2=https://business2.openissuer.com \
CLIENT_ID=shared-client \
SECRET1='tenant1-smoke-secret' \
SECRET2='tenant2-smoke-secret' \
./scripts/multitenant-smoke.sh
```

## Environment Variables

- `BASE1`: first issuer base URL
- `BASE2`: second issuer base URL
- `CLIENT_ID`: client id expected in both tenants
- `SECRET1`: secret for tenant 1
- `SECRET2`: secret for tenant 2

## Notes

- `CLIENT_ID` must exist in both tenant stores.
- `SECRET1` and `SECRET2` should be different if you want the cross-tenant rejection check to prove isolation.
- The script assumes the token endpoint is `/oauth2/token` and discovery is `/.well-known/openid-configuration`.
