# Kubernetes Deployment

The Kubernetes deployment uses the shared `sonam-helm-chart` chart and service-specific `values-backend.yaml` files.

## Runtime Profile

Kubernetes runs with:

```yaml
SPRING_PROFILES_ACTIVE: kubernetes,non-eureka
```

In this profile:

- Kubernetes discovery is enabled.
- Eureka is disabled.
- Services resolve each other through Kubernetes service names.
- Gateway and HTTPRoute expose tenant hosts.

## Important Environment Values

Authorization production values include:

```yaml
ISSUER_URI: https://platform.openissuer.com
ISSUER_ADDRESS: https://platform.openissuer.com
AUTHORIZATION_SERVER_MULTITENANCY_DEFAULT_HOSTS_3: platform.openissuer.com
```

Tenant database settings include:

```yaml
BUSINESS1_AUTH_DB_URL: jdbc:postgresql://business1-auth-db-rw/business1auth
BUSINESS1_AUTH_DB_USERNAME: business1auth
BUSINESS1_AUTH_DB_PASSWORD_SECRET_REF: business1-db-password
```

Tenant DB passwords are not literal environment variables. The application resolves them through tenant secret references.

## Required Kubernetes Objects

The authorization service expects:

- Namespace, usually `main`.
- Image pull secret, usually `github-regcred`.
- Authorization database and app secret.
- Tenant authorization databases and app secrets.
- `service-service-client-credential-flow-secret`.
- Optional `authorization-seed` sealed secret for seed JSON.
- Gateway/HTTPRoute configuration for issuer hosts.

## Deploy

Example:

```bash
export KUBECONFIG=/path/to/kubeconfig.yaml

helm upgrade --install --timeout 10m --wait \
  authorization-server \
  /path/to/sonam-helm-chart \
  -f values-backend.yaml \
  --namespace main
```

If the image tag is mutable, such as `multitenancy`, restart the deployment after upgrade to force a fresh pull:

```bash
kubectl rollout restart deployment/authorization-server -n main
kubectl rollout status deployment/authorization-server -n main --timeout=10m
```

## Health Probes

Authorization uses startup, liveness, and readiness probes:

- Startup: `/api/health/liveness`
- Liveness: `/api/health/liveness`
- Readiness: `/api/health/readiness`

Startup probes are important because Spring Boot 4 services can take longer to start under constrained cluster resources. Without a startup probe, Kubernetes may kill the container before it finishes booting.

## Resource Pressure

Authorization requests more memory than smaller services because it loads Spring Authorization Server, tenant datasources, JWKs, WebAuthn support, templates, and service clients. If rollouts stall with Pending pods, check:

```bash
kubectl describe pod -n main <pod>
kubectl get nodes
```

Common scheduling messages:

```text
Insufficient cpu
Insufficient memory
```

Cluster autoscaler may need time to add capacity.
