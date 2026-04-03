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

Local examples:
- `http://business1.openissuer.test:9001`
- `http://business2.openissuer.test:9001`

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
- `business1.openissuer.test`
- `business2.openissuer.test`

Recommended `/etc/hosts` entry:

```text
127.0.0.1 business1.openissuer.test business2.openissuer.test
```

Then run with:

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

Local issuer URLs:
- `http://business1.openissuer.test:9001`
- `http://business2.openissuer.test:9001`

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

