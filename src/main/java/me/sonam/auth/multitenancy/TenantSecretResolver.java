package me.sonam.auth.multitenancy;

public interface TenantSecretResolver {
    String resolve(String secretRef);
}
