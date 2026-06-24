# Troubleshooting

## `RegisteredClientRepository not found for requested issuer`

Example:

```text
RegisteredClientRepository not found for requested issuer.
issuer=http://10.0.0.244:9001
host=10.0.0.244
```

Likely cause:

- The issuer host does not match a default host or configured tenant host.
- A proxy/gateway did not preserve forwarded host headers.
- The client used an IP address instead of the issuer host.

Fix:

- Use tenant hosts such as `free.openissuer.com`, not pod IPs.
- Ensure gateway forwards host/proto headers.
- Add the correct host to `authorization-server.multitenancy.default-hosts` or tenant config when appropriate.

## Token Issuer Mismatch

Symptom:

```text
401 Unauthorized from role-rest-service or attempt-rest-service
```

Likely cause:

- Token `iss` claim does not match the issuer/JWK configuration used by the resource service.

Fix:

- Decode the JWT and check `iss`.
- Resource services should use the same issuer/JWK pattern:

```yaml
ISSUER_URI: https://platform.openissuer.com/issuer
JWT_SET_URI: https://platform.openissuer.com/issuer/oauth2/jwks
```

For tenant user tokens, resource services must accept trusted OpenIssuer tenant hosts where configured.

## `LoadBalancer does not contain an instance`

Example:

```text
LoadBalancer does not contain an instance for the service authorization-server
```

Likely cause:

- Service discovery has not registered the service yet.
- Kubernetes service name is wrong.
- Local seeding started before downstream services were available.

Fix:

- Verify the service exists and has endpoints.
- Increase seeding delay if it happens during startup.
- In local HTTPS, make sure token requests are not incorrectly routed through HTTP service discovery.

## `This combination of host and port requires TLS`

Likely cause:

- A client called `http://...:9001` while authorization is running with TLS on port `9001`.

Fix:

- Start services with consistent `local-https` issuer values.
- Use `https://platform.openissuer.test:9001` for local token calls.

## Passkey Does Nothing Or Browser Rejects It

Likely cause:

- Page is not a secure context.
- Local certificate is not trusted.
- Browser profile/device passkey state does not contain the credential.

Fix:

- Use HTTPS.
- Use `mkcert -install`.
- Restart browser after trusting the CA.
- Try Chrome with Touch ID or another platform authenticator.

## Passkey Verification Redirects To `/error`

Likely cause:

- Saved OAuth request was captured as `/error?...response_type=code...`.

The passkey MFA service normalizes this by rewriting a saved `/error` OAuth query back to `/oauth2/authorize` when it detects a valid OAuth authorization request.

## Signup Email Domain Rejected

Example:

```text
email domain is not allowed for this subdomain
```

Likely cause:

- The current host has `allowed-email-domains` configured and the submitted email does not match.

Fix:

- Use an email domain allowed by that tenant host.
- Adjust `authorization-server.signup-policy.hosts.<host>.allowed-email-domains`.

## Activation Link Uses Wrong Host

Likely cause:

- `activationHost` was not sent or downstream services used a generic fallback host.

Fix:

- Signup flows should set `activationHost` from the current tenant host.
- Downstream account/email services should build activation/reset/unlock links from that tenant host.

