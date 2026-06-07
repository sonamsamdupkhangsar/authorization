package me.sonam.auth.multitenancy;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
public class EnvironmentTenantSecretResolver implements TenantSecretResolver {
    private final Environment environment;

    public EnvironmentTenantSecretResolver(Environment environment) {
        this.environment = environment;
    }

    @Override
    public String resolve(String secretRef) {
        Assert.hasText(secretRef, "secretRef is required");
        String propertyKey = "tenant-secrets." + secretRef;
        String resolved = environment.getProperty(propertyKey);
        if (resolved != null) {
            return resolved;
        }
        String envKey = "TENANT_SECRET_" + secretRef
                .replace('-', '_')
                .replace('.', '_')
                .toUpperCase();
        resolved = environment.getProperty(envKey);
        Assert.state(resolved != null, "No tenant secret configured for reference: " + secretRef);
        return resolved;
    }
}
