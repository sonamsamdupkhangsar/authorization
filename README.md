# OpenIssuer Authorization Server

OpenIssuer Authorization Server is the OAuth 2.1 and OpenID Connect provider for OpenIssuer. It is built on Spring Boot 4, Spring Security 7, and Spring Authorization Server, with host-based multi-tenancy for tenant issuers such as `platform.openissuer.com`, `free.openissuer.com`, `business1.openissuer.com`, and `business2.openissuer.com`.

## Key Features

- OAuth 2.1 and OpenID Connect login, authorization code, client credentials, token, JWK, and userinfo support.
- Spring Boot 4 and Spring Security 7, including Spring Security WebAuthn passkey support.
- Host-based multi-tenancy, where the request issuer host selects the tenant-specific authorization components.
- Tenant-specific clients, authorizations, consent records, JWKs, and passkey credentials.
- Signup policies per issuer host, including free/public signup and business-host signup.
- AuthzManager client administration integration.
- Optional passkey MFA enforcement after username/password login when a user has enrolled a passkey.
- Local HTTPS support for passkey/WebAuthn testing.

## Namespace And Tenant Mapping

Each authorization deployment represents one Kubernetes namespace and always has one
implicit **default tenant**. The default tenant is not listed under
`authorization-server.multitenancy.tenants`; it uses the deployment's normal datasource
(`POSTGRES_*`) and the hosts listed in `default-hosts`.

The `tenants` map contains only additional tenants that share that namespace's
application stack. Each additional tenant declares its owning `deployment-namespace`
and its own authorization database. Authorization loads an additional tenant only when
its namespace matches the running deployment namespace. Database registration and
`ClientSetup` use the same filtered tenant set.

For example, the current topology is conceptually:

```text
main
├── default tenant: platform       -> authorization-db
├── additional tenant: free        -> free-auth-db
├── additional tenant: demo        -> demo-auth-db
├── additional tenant: business1   -> business1-auth-db
└── additional tenant: business2   -> business2-auth-db

dedicated-tenant
└── default tenant: dedicated-tenant -> authorization-db in dedicated-tenant
```

The dedicated tenant does not appear in the additional `tenants` map because it is that
deployment's default tenant. A namespace may still host more tenants by assigning them
to the same namespace explicitly:

```yaml
authorization-server:
  multitenancy:
    deployment-namespace: dedicated-tenant
    default-hosts:
      - dedicated-tenant.openissuer.com
    tenants:
      customer-two:
        deployment-namespace: dedicated-tenant
        hosts:
          - customer-two.openissuer.com
        url: ${CUSTOMER_TWO_AUTH_DB_URL}
        username: ${CUSTOMER_TWO_AUTH_DB_USERNAME}
        password-secret-ref: ${CUSTOMER_TWO_AUTH_DB_PASSWORD_SECRET_REF}
        driver-class-name: org.postgresql.Driver
```

Persisted runtime registrations are stored in the deployment's default authorization
database, so they naturally belong to that namespace stack.

## Documentation

- [Overview](docs/01-overview.md)
- [Architecture](docs/02-architecture.md)
- [Host-Based Multi-Tenancy](docs/03-host-based-multitenancy.md)
- [Signup And Tenant Onboarding](docs/04-signup-and-tenant-onboarding.md)
- [OAuth Client Management](docs/05-oauth-client-management.md)
- [Passkeys And MFA](docs/06-passkeys-and-mfa.md)
- [Local Development](docs/07-local-development.md)
- [Kubernetes Deployment](docs/08-kubernetes-deployment.md)
- [Troubleshooting](docs/09-troubleshooting.md)

## Quick Start

Run locally with Eureka and local HTTPS. Use this for browser, tenant-host, and passkey/WebAuthn testing:

```bash
SPRING_PROFILES_ACTIVE=eureka,local-https ./gradlew bootRun
```

For local HTTP only:

```bash
SPRING_PROFILES_ACTIVE=eureka ./gradlew bootRun
```

See [Local Development](docs/07-local-development.md) for `/etc/hosts`, `mkcert`, and service startup details.
