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

Run locally with the Eureka profile:

```bash
./gradlew bootRun --args="--spring.profiles.active=eureka"
```

For passkey testing, use local HTTPS:

```bash
SPRING_PROFILES_ACTIVE=eureka,local-https ./gradlew bootRun
```

See [Local Development](docs/07-local-development.md) for `/etc/hosts`, `mkcert`, and service startup details.
