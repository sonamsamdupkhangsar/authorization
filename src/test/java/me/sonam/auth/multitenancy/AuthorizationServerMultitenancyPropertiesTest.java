package me.sonam.auth.multitenancy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationServerMultitenancyPropertiesTest {

    @Test
    void loadsMainTenantInMainNamespaceByDefault() {
        AuthorizationServerMultitenancyProperties properties =
                new AuthorizationServerMultitenancyProperties();
        AuthorizationServerMultitenancyProperties.Tenant tenant =
                new AuthorizationServerMultitenancyProperties.Tenant();

        assertThat(properties.shouldLoadTenant(tenant)).isTrue();
    }

    @Test
    void skipsMainTenantInDedicatedNamespace() {
        AuthorizationServerMultitenancyProperties properties =
                new AuthorizationServerMultitenancyProperties();
        properties.setDeploymentNamespace("dedicated-tenant");
        AuthorizationServerMultitenancyProperties.Tenant tenant =
                new AuthorizationServerMultitenancyProperties.Tenant();

        assertThat(properties.shouldLoadTenant(tenant)).isFalse();
    }

    @Test
    void loadsTenantOwnedByDedicatedNamespace() {
        AuthorizationServerMultitenancyProperties properties =
                new AuthorizationServerMultitenancyProperties();
        properties.setDeploymentNamespace("dedicated-tenant");
        AuthorizationServerMultitenancyProperties.Tenant tenant =
                new AuthorizationServerMultitenancyProperties.Tenant();
        tenant.setDeploymentNamespace("dedicated-tenant");

        assertThat(properties.shouldLoadTenant(tenant)).isTrue();
    }

    @Test
    void legacyDisableFlagStillOverridesNamespaceSelection() {
        AuthorizationServerMultitenancyProperties properties =
                new AuthorizationServerMultitenancyProperties();
        properties.setAdditionalTenantsEnabled(false);
        AuthorizationServerMultitenancyProperties.Tenant tenant =
                new AuthorizationServerMultitenancyProperties.Tenant();

        assertThat(properties.shouldLoadTenant(tenant)).isFalse();
    }
}
