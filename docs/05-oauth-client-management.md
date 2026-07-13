# OAuth Client Management

OAuth clients are managed through authorization server REST endpoints and the AuthzManager UI. Clients are tenant-aware: the issuer in the caller's JWT is used to select the correct registered client repository.

## Where Clients Are Stored

Spring Authorization Server registered clients are stored in the issuer-specific authorization database.

Associations between clients and organizations are stored through `ClientOrganization` rows in the platform authorization database. This lets the admin UI list clients by the logged-in user's organization while still storing each registered client in the issuer-specific Spring Authorization Server tables.

## Main Client Endpoints

`ClientRestService` is mapped under:

```text
/clients
```

Important behavior:

- Create/update clients only for authorized organization admins.
- Resolve the issuer from the access token.
- Use `IssuerAwareAuthorizationServerOperations` to read/write the registered client in the correct issuer repository.
- Enforce the configured client limit for the user's current organization.
- Associate created clients to the user's default organization.

## Creating A Client In AuthzManager

Typical flow:

1. Admin signs in at an admin host, for example `https://free.admin.openissuer.com`.
2. Admin opens the client page.
3. AuthzManager calls authorization server client APIs with the user's access token.
4. Authorization server reads the JWT issuer, for example `https://free.openissuer.com`.
5. Authorization server saves the registered client in the `free` issuer repository.
6. Authorization server associates the client ID with the admin's default organization.

Before creating a new client, AuthzManager asks the authorization server for the current default organization's client count. If the limit is already reached, AuthzManager shows the limit message before submitting the create request. The authorization server enforces the same limit for direct API calls.

## Client Limits

Client limits are configured by issuer host:

```yaml
client-limits:
  default-max-clients: ${MAX_CLIENTS:5}
  hosts:
    free.openissuer.com: ${FREE_MAX_CLIENTS:2}
    demo.openissuer.com: ${DEMO_MAX_CLIENTS:2}
```

Local `eureka` profile values use the matching `.test` hosts:

```yaml
client-limits:
  hosts:
    free.openissuer.test: ${FREE_MAX_CLIENTS:2}
    demo.openissuer.test: ${DEMO_MAX_CLIENTS:2}
```

The current public policy is:

| Issuer | Max clients per organization |
| --- | ---: |
| Default/unlisted hosts | 5 |
| `free.openissuer.com` / `free.openissuer.test` | 2 |
| `demo.openissuer.com` / `demo.openissuer.test` | 2 |

## Client Fields

Common client fields:

- `clientId`
- `clientSecret`
- `clientName`
- `clientAuthenticationMethods`
- `authorizationGrantTypes`
- `redirectUris`
- `postLogoutRedirectUris`
- `scopes`
- `clientSettings`
- `tokenSettings`

Client secrets are encoded before storage. Existing client secret handling in the admin UI should preserve the stored secret unless the admin intentionally enters a new secret.

## Example Authorization URL

```text
https://free.openissuer.com/oauth2/authorize
  ?response_type=code
  &client_id={client-id}
  &scope=openid%20profile
  &redirect_uri={registered-redirect-uri}
  &state={state}
  &nonce={nonce}
```

The host matters. A client registered under `free.openissuer.com` must use the `free.openissuer.com` issuer.

## Example Token Request

```bash
curl -X POST https://free.openissuer.com/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -u "{client-id}:{client-secret}" \
  -d "grant_type=authorization_code" \
  -d "code={authorization-code}" \
  -d "redirect_uri={registered-redirect-uri}"
```

The response token `iss` claim should match the issuer host used for the flow.

## Common Client Issues

| Symptom | Likely Cause |
| --- | --- |
| `RegisteredClientRepository not found for requested issuer` | Request host/issuer does not map to a default or tenant host. |
| Login starts but token exchange fails | Client was registered under a different issuer host. |
| Admin can list orgs but not clients | Client API call may not be using tenant forwarded headers or correct issuer. |
| Redirect URI error | Redirect URI submitted by client does not exactly match registered URI. |
