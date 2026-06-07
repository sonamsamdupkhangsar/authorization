# Handoff Note

## Current State

This repo was refactored from single-tenant / row-level tenant scoping to Spring Authorization Server-style issuer-based multitenancy.

The active model is:
- subdomain per issuer
- one datasource per issuer
- one JWK set per issuer datasource
- SAS components delegated by requested issuer / host

Examples:
- `https://business1.openissuer.com`
- `https://business2.openissuer.com`
- `https://free.openissuer.com`
- `https://platform.openissuer.com`

Local examples:
- `http://platform.openissuer.test:9001`
- `http://business1.openissuer.test:9001`
- `http://business2.openissuer.test:9001`
- `http://free.openissuer.test:9001`

## Main Architecture

Key classes:
- [PerIssuerAuthorizationServerComponentsConfig.java](/Users/sonamsamdupkhangsar/Documents/github/authorization/src/main/java/me/sonam/auth/config/PerIssuerAuthorizationServerComponentsConfig.java)
- [TenantPerHostComponentRegistry.java](/Users/sonamsamdupkhangsar/Documents/github/authorization/src/main/java/me/sonam/auth/multitenancy/TenantPerHostComponentRegistry.java)
- [IssuerContextExecutor.java](/Users/sonamsamdupkhangsar/Documents/github/authorization/src/main/java/me/sonam/auth/multitenancy/IssuerContextExecutor.java)
- [IssuerAwareAuthorizationServerOperations.java](/Users/sonamsamdupkhangsar/Documents/github/authorization/src/main/java/me/sonam/auth/multitenancy/IssuerAwareAuthorizationServerOperations.java)
- [IssuerComponentRegistrar.java](/Users/sonamsamdupkhangsar/Documents/github/authorization/src/main/java/me/sonam/auth/multitenancy/IssuerComponentRegistrar.java)
- [PersistentJwkSetStore.java](/Users/sonamsamdupkhangsar/Documents/github/authorization/src/main/java/me/sonam/auth/multitenancy/PersistentJwkSetStore.java)
- [TenantOnboardingService.java](/Users/sonamsamdupkhangsar/Documents/github/authorization/src/main/java/me/sonam/auth/multitenancy/TenantOnboardingService.java)
- [EnvironmentTenantSecretResolver.java](/Users/sonamsamdupkhangsar/Documents/github/authorization/src/main/java/me/sonam/auth/multitenancy/EnvironmentTenantSecretResolver.java)

Important behavior:
- `AuthorizationServerSettings` is configured with `multipleIssuersAllowed(true)`
- issuer is derived from the request host
- delegating SAS beans resolve the real component from the current issuer host
- runtime tenant onboarding persists tenant metadata and rebuilds on restart

## Secret Handling

Tenant datasource credentials no longer store raw DB passwords in tenant registration.

Current approach:
- store `passwordSecretRef`
- resolve through [EnvironmentTenantSecretResolver.java](/Users/sonamsamdupkhangsar/Documents/github/authorization/src/main/java/me/sonam/auth/multitenancy/EnvironmentTenantSecretResolver.java)

Resolution order:
- Spring property `tenant-secrets.<secretRef>`
- env-style key `TENANT_SECRET_<SECRET_REF>`

## Root Path Change

The old servlet context path `/issuer` was removed.

Current endpoint shape:
- `/oauth2/authorize`
- `/oauth2/token`
- `/oauth2/jwks`
- `/.well-known/openid-configuration`

Files changed for that move:
- [application.yaml](/Users/sonamsamdupkhangsar/Documents/github/authorization/src/main/resources/application.yaml)
- [application-local.yaml](/Users/sonamsamdupkhangsar/Documents/github/authorization/src/main/resources/application-local.yaml)
- [application.yaml](/Users/sonamsamdupkhangsar/Documents/github/authorization/src/test/resources/application.yaml)
- [TokenFilter.java](/Users/sonamsamdupkhangsar/Documents/github/authorization/src/main/java/me/sonam/auth/util/TokenFilter.java)
- several Thymeleaf templates under [templates](/Users/sonamsamdupkhangsar/Documents/github/authorization/src/main/resources/templates)

## Local Dev Setup

Local tenant hosts configured in [application-local.yaml](/Users/sonamsamdupkhangsar/Documents/github/authorization/src/main/resources/application-local.yaml):
- `platform.openissuer.test`
- `business1.openissuer.test`
- `business2.openissuer.test`
- `free.openissuer.test`

Local hostname model:
- transport for internal service-to-service token calls stays on Eureka service name `authorization-server`
- default/platform issuer for service-account tokens and JWKS validation is `http://platform.openissuer.test:9001`
- tenant issuers are host-bound:
  - `http://business1.openissuer.test:9001`
  - `http://business2.openissuer.test:9001`
  - `http://free.openissuer.test:9001`

This split is intentional:
- `TokenFilter` still posts to `http://authorization-server/oauth2/token`
- but forwarded host headers are derived from `ISSUER_ADDRESS`
- so service-account tokens are minted with issuer `http://platform.openissuer.test:9001`
- while tenant-facing browser and signup flows continue to use the actual tenant host

Recommended `/etc/hosts` entry:

```text
127.0.0.1 platform.openissuer.test
127.0.0.1 business1.openissuer.test business2.openissuer.test free.openissuer.test
```

Then run with:

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

Local issuer URLs:
- `http://platform.openissuer.test:9001`
- `http://business1.openissuer.test:9001`
- `http://business2.openissuer.test:9001`
- `http://free.openissuer.test:9001`

Sibling local services updated to validate against the platform issuer/JWKS host:
- `user-rest-service`
- `organization-rest-service`
- `authentication-rest-service`
- `role-rest-service`
- `email-rest-service`
- `account-rest-service`

Those services were updated to:
- stop using `/issuer`
- use `jwk-set-uri` instead of issuer discovery for local validation
- point local `ISSUER_ADDRESS` / `JWT_SET_URI` to `http://platform.openissuer.test:9001`

Authzmanager local admin hosts:
- `http://platform.admin.openissuer.test:9093`
- `http://business1.admin.openissuer.test:9093`
- `http://business2.admin.openissuer.test:9093`
- `http://free.admin.openissuer.test:9093`

Authzmanager host mapping rule:
- `platform.admin.openissuer.test` -> issuer `http://platform.openissuer.test:9001`
- `business1.admin.openissuer.test` -> issuer `http://business1.openissuer.test:9001`
- `business2.admin.openissuer.test` -> issuer `http://business2.openissuer.test:9001`
- `free.admin.openissuer.test` -> issuer `http://free.openissuer.test:9001`

Additional `/etc/hosts` entry for authzmanager local testing:

```text
127.0.0.1 platform.admin.openissuer.test
127.0.0.1 business1.admin.openissuer.test business2.admin.openissuer.test free.admin.openissuer.test
```

Authzmanager local test flow:
1. Verify issuer discovery endpoints:
   - `curl http://platform.openissuer.test:9001/.well-known/openid-configuration`
   - `curl http://business1.openissuer.test:9001/.well-known/openid-configuration`
   - `curl http://business2.openissuer.test:9001/.well-known/openid-configuration`
   - `curl http://free.openissuer.test:9001/.well-known/openid-configuration`
2. Open one of the admin hosts in the browser:
   - `http://platform.admin.openissuer.test:9093`
   - `http://business1.admin.openissuer.test:9093`
   - `http://business2.admin.openissuer.test:9093`
   - `http://free.admin.openissuer.test:9093`
3. Verify the authorization redirect host matches the tenant issuer host:
   - `business1.admin...` should redirect to `business1.openissuer.test:9001/oauth2/authorize`
   - `business2.admin...` should redirect to `business2.openissuer.test:9001/oauth2/authorize`
   - `free.admin...` should redirect to `free.openissuer.test:9001/oauth2/authorize`
   - `platform.admin...` should redirect to `platform.openissuer.test:9001/oauth2/authorize`
4. Verify the post-login callback returns to the matching admin host:
   - `http://<tenant>.admin.openissuer.test:9093/login/oauth2/code/...`

Authzmanager troubleshooting guide:
- wrong authorize host:
  - check authzmanager tenant host mapping
- redirect URI mismatch:
  - check authzmanager client seeding in authorization `ClientSetup`
- `invalid_client`:
  - authzmanager client not seeded for that issuer
- 401 after callback:
  - issuer/JWKS mismatch or downstream token validation problem

## Tests Added / Updated

Most important current multitenancy test:
- [PerIssuerAuthorizationServerComponentsIntegTest.java](/Users/sonamsamdupkhangsar/Documents/github/authorization/src/test/java/me/sonam/auth/PerIssuerAuthorizationServerComponentsIntegTest.java)

It now verifies:
- same `client_id` can exist across issuers
- bootstrap clients are seeded per issuer
- dynamic tenant onboarding works
- host-header end-to-end routing works:
  - discovery document per host
  - distinct JWKS `kid` per host
  - token endpoint success on the correct host
  - cross-tenant `invalid_client` rejection

Other tests updated after removing `/issuer`:
- [AuthorizationServerApplicationUserLoginTests.java](/Users/sonamsamdupkhangsar/Documents/github/authorization/src/test/java/me/sonam/auth/AuthorizationServerApplicationUserLoginTests.java)
- [ForgotUsernamePasswordIntegTest.java](/Users/sonamsamdupkhangsar/Documents/github/authorization/src/test/java/me/sonam/auth/ForgotUsernamePasswordIntegTest.java)
- [AccountLockControllerIntegTest.java](/Users/sonamsamdupkhangsar/Documents/github/authorization/src/test/java/me/sonam/auth/AccountLockControllerIntegTest.java)

## Verified Commands

These were run successfully in the recent session:

```bash
./gradlew compileJava testClasses
./gradlew test --tests me.sonam.auth.PerIssuerAuthorizationServerComponentsIntegTest
./gradlew test --tests me.sonam.auth.AccountLockControllerIntegTest --tests me.sonam.auth.AuthorizationServerApplicationUserLoginTests --tests me.sonam.auth.ForgotUsernamePasswordIntegTest
```

Note:
- some larger mixed test invocations were flaky due shared integration-test state
- the focused commands above are the reliable verification set used most recently

## Smoke Test Script

There is now a deploy-time smoke test script:
- [multitenant-smoke.sh](/Users/sonamsamdupkhangsar/Documents/github/authorization/scripts/multitenant-smoke.sh)

Runbook:
- [README-multitenant-smoke.md](/Users/sonamsamdupkhangsar/Documents/github/authorization/scripts/README-multitenant-smoke.md)

Purpose:
- verify discovery matches each issuer host
- verify token issuance succeeds for the correct tenant secret
- verify cross-tenant authentication fails with `invalid_client`

Local example:

```bash
BASE1=http://business1.openissuer.test:9001 \
BASE2=http://business2.openissuer.test:9001 \
CLIENT_ID=shared-client \
SECRET1=business1-secret \
SECRET2=business2-secret \
./scripts/multitenant-smoke.sh
```

## Known Caveats

- [MapBackedAuthorizationServerContext.java](/Users/sonamsamdupkhangsar/Documents/github/authorization/src/main/java/me/sonam/auth/config/MapBackedAuthorizationServerContext.java) returns `null` from `getAuthorizationServerSettings()`
- [TenantPerHostComponentRegistry.java](/Users/sonamsamdupkhangsar/Documents/github/authorization/src/main/java/me/sonam/auth/multitenancy/TenantPerHostComponentRegistry.java) still falls back to default components if issuer context is missing
- some legacy projection code still exists for compatibility and tests, especially [JpaRegisteredClientRepository.java](/Users/sonamsamdupkhangsar/Documents/github/authorization/src/main/java/me/sonam/auth/service/JpaRegisteredClientRepository.java)
- login tests still depend partly on legacy projection seeding, even though runtime SAS multitenancy is issuer-based

## Good Next Steps

Likely next improvements:
- add restart-persistence integration coverage for dynamically onboarded tenants
- harden `MapBackedAuthorizationServerContext` to provide real `AuthorizationServerSettings`
- reduce remaining dependence on legacy `JpaRegisteredClientRepository` in tests
- add explicit end-to-end authorize-flow host-header coverage if needed
