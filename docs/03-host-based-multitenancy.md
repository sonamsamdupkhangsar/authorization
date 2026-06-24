# Host-Based Multi-Tenancy

OpenIssuer uses the request issuer host as the tenant selector. The same authorization server deployment can serve different issuers, but each issuer can have its own authorization database, clients, authorization records, consent records, JWKs, and passkey credentials.

## Host To Issuer

Examples:

| Host | Issuer |
| --- | --- |
| `platform.openissuer.com` | `https://platform.openissuer.com` |
| `free.openissuer.com` | `https://free.openissuer.com` |
| `business1.openissuer.com` | `https://business1.openissuer.com` |
| `business2.openissuer.com` | `https://business2.openissuer.com` |

In local HTTPS:

| Host | Issuer |
| --- | --- |
| `free.openissuer.test` | `https://free.openissuer.test:9001` |
| `business1.openissuer.test` | `https://business1.openissuer.test:9001` |

## Admin Host Mapping

AuthzManager runs on admin hosts:

| Admin Host | Issuer/Organization Host |
| --- | --- |
| `free.admin.openissuer.com` | `free.openissuer.com` |
| `business1.admin.openissuer.com` | `business1.openissuer.com` |
| `business2.admin.openissuer.com` | `business2.openissuer.com` |

`HostOrganizationResolver` removes the `.admin.` segment so admin flows can still operate on the correct tenant organization host.

## How Components Are Selected

`PerIssuerAuthorizationServerComponentsConfig` enables multiple issuers:

```java
AuthorizationServerSettings.builder()
    .multipleIssuersAllowed(true)
    .build();
```

`TenantPerHostComponentRegistry` resolves the current host from `AuthorizationServerContextHolder`. Delegating beans then pick the correct tenant component:

- `DelegatingRegisteredClientRepository`
- `DelegatingOAuth2AuthorizationService`
- `DelegatingOAuth2AuthorizationConsentService`
- `DelegatingJwkSource`

If the current host is a default host, the default/platform components are used. If the host is a tenant host, tenant-specific components are used.

## Configured Tenants

Static tenants are configured under:

```yaml
authorization-server:
  multitenancy:
    default-hosts:
      - localhost
      - 127.0.0.1
      - authorization-server
    tenants:
      free:
        hosts:
          - free.openissuer.com
        url: ${FREE_AUTH_DB_URL}
        username: ${FREE_AUTH_DB_USERNAME}
        password-secret-ref: ${FREE_AUTH_DB_PASSWORD_SECRET_REF}
```

Persisted tenants are stored in `TenantRegistration` rows and loaded on startup.

## Runtime Tenant Registration

`TenantOnboardingRestService` exposes:

- `GET /admin/tenants`
- `POST /admin/tenants`

`TenantOnboardingService` validates host availability, creates a tenant datasource, registers authorization server components, persists the tenant registration, and seeds issuer clients for each host.

Tenant registration requires infrastructure to already exist:

- DNS/host route for the issuer.
- Tenant authorization database.
- Database user and password secret reference.
- Gateway/ingress route.

## Forwarded Headers

In Kubernetes, browser traffic often reaches the service through a gateway. Forwarded headers must preserve the original public host. The authorization server uses a `ForwardedHeaderFilter`, and service-to-service token calls set stable forwarded host/proto/port headers when needed.

If forwarded host information is wrong, the server may resolve the wrong issuer, create tokens with the wrong `iss`, or fail with a missing `RegisteredClientRepository`.

