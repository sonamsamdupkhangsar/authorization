# Overview

OpenIssuer Authorization Server is the central identity and OAuth service for OpenIssuer. It authenticates users, issues OAuth/OIDC tokens, publishes tenant-specific JWKs, manages registered OAuth clients, and coordinates signup, account activation, organization assignment, and passkey MFA.

The service is a customization of Spring Authorization Server. It keeps the standards-based OAuth/OIDC behavior from Spring Security while adding OpenIssuer-specific multi-tenancy and business onboarding behavior.

## What It Does

- Serves OAuth/OIDC endpoints such as `/oauth2/authorize`, `/oauth2/token`, `/oauth2/jwks`, `/.well-known/openid-configuration`, and `/userinfo`.
- Authenticates usernames and passwords through `authentication-rest-service`.
- Looks up users through `user-rest-service`.
- Checks organization membership through `organization-rest-service`.
- Checks organization-scoped AuthzManager authorization through `role-rest-service`.
- Calls `account-rest-service` for activation, reset, lock, unlock, and email flows.
- Calls `attempt-rest-service` to track login attempts.
- Stores OAuth clients, authorizations, authorization consent, JWKs, tenant registration records, and passkey credentials.

## Recent Platform Updates

The current implementation includes several major platform changes:

- Spring Boot upgraded to `4.0.0`.
- Spring Security upgraded to the Spring Security 7 line.
- Spring Authorization Server dependency is `org.springframework.security:spring-security-oauth2-authorization-server:7.0.0`.
- WebAuthn/passkey support is provided through `org.springframework.security:spring-security-webauthn`.
- Host-based multi-tenancy allows one authorization server deployment to serve multiple issuer hosts.
- AuthzManager administration uses scoped `OrgAdmin` and `SubdomainAdmin` role assignments.
- Passkey MFA is enforced after password login for users who have enrolled passkeys.
- Local HTTPS profile supports passkey development with trusted `mkcert` certificates.

## Main Runtime Hosts

Production issuer hosts:

- `https://platform.openissuer.com`
- `https://free.openissuer.com`
- `https://business1.openissuer.com`
- `https://business2.openissuer.com`

Production admin hosts:

- `https://platform.admin.openissuer.com`
- `https://free.admin.openissuer.com`
- `https://business1.admin.openissuer.com`
- `https://business2.admin.openissuer.com`

Local equivalents use `.test`, for example:

- `https://free.openissuer.test:9001`
- `https://free.admin.openissuer.test:9093`

## Important Packages

- `me.sonam.auth.config`: Spring Security, authorization server, WebClient, and multi-issuer configuration.
- `me.sonam.auth.multitenancy`: tenant component registration and issuer-host mapping.
- `me.sonam.auth.mfa.passkey`: passkey registration, MFA challenge, and tenant-aware WebAuthn repositories.
- `me.sonam.auth.rest`: login, signup, client management, password/account flows, and health endpoints.
- `me.sonam.auth.webclient`: calls to user, account, role, organization, authentication, and attempt services.
