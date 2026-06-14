package me.sonam.auth.mfa.passkey;

import me.sonam.auth.multitenancy.TenantPerHostComponentRegistry;
import me.sonam.auth.service.HostOrganizationResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CurrentTenantJdbcTemplateResolver {
    private final HostOrganizationResolver hostOrganizationResolver;
    private final TenantPerHostComponentRegistry componentRegistry;

    public CurrentTenantJdbcTemplateResolver(HostOrganizationResolver hostOrganizationResolver,
                                             TenantPerHostComponentRegistry componentRegistry) {
        this.hostOrganizationResolver = hostOrganizationResolver;
        this.componentRegistry = componentRegistry;
    }

    public JdbcTemplate resolve() {
        JdbcTemplate jdbcTemplate = hostOrganizationResolver.currentHost()
                .map(host -> componentRegistry.get(host, JdbcTemplate.class))
                .orElse(null);
        return jdbcTemplate != null ? jdbcTemplate : componentRegistry.get(JdbcTemplate.class);
    }
}
