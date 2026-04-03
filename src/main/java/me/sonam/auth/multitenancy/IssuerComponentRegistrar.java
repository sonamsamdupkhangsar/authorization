package me.sonam.auth.multitenancy;

import com.nimbusds.jose.jwk.JWKSet;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.sql.DataSource;
import java.util.List;

@Component
public class IssuerComponentRegistrar {
    private final PersistentJwkSetStore persistentJwkSetStore;
    private final TenantSecretResolver tenantSecretResolver;

    public IssuerComponentRegistrar(PersistentJwkSetStore persistentJwkSetStore,
                                    TenantSecretResolver tenantSecretResolver) {
        this.persistentJwkSetStore = persistentJwkSetStore;
        this.tenantSecretResolver = tenantSecretResolver;
    }

    public void registerDefaultComponents(DataSource dataSource, TenantPerHostComponentRegistry componentRegistry) {
        initializeAuthorizationServerSchema(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        initializeJwkSchema(jdbcTemplate);
        JdbcRegisteredClientRepository registeredClientRepository = new JdbcRegisteredClientRepository(jdbcTemplate);
        JdbcOAuth2AuthorizationService authorizationService =
                new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
        JdbcOAuth2AuthorizationConsentService authorizationConsentService =
                new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository);

        componentRegistry.registerDefault(DataSource.class, dataSource);
        componentRegistry.registerDefault(JdbcTemplate.class, jdbcTemplate);
        componentRegistry.registerDefault(RegisteredClientRepository.class, registeredClientRepository);
        componentRegistry.registerDefault(OAuth2AuthorizationService.class, authorizationService);
        componentRegistry.registerDefault(OAuth2AuthorizationConsentService.class, authorizationConsentService);
        componentRegistry.registerDefault(JWKSet.class, persistentJwkSetStore.loadOrCreate(jdbcTemplate));
    }

    public void registerTenantComponents(DataSource dataSource, List<String> hosts, TenantPerHostComponentRegistry componentRegistry) {
        Assert.state(!hosts.isEmpty(), "at least one host must be configured");
        initializeAuthorizationServerSchema(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        initializeJwkSchema(jdbcTemplate);
        JdbcRegisteredClientRepository registeredClientRepository = new JdbcRegisteredClientRepository(jdbcTemplate);
        JdbcOAuth2AuthorizationService authorizationService =
                new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
        JdbcOAuth2AuthorizationConsentService authorizationConsentService =
                new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository);
        JWKSet jwkSet = persistentJwkSetStore.loadOrCreate(jdbcTemplate);

        hosts.forEach(host -> {
            componentRegistry.register(host, DataSource.class, dataSource);
            componentRegistry.register(host, JdbcTemplate.class, jdbcTemplate);
            componentRegistry.register(host, RegisteredClientRepository.class, registeredClientRepository);
            componentRegistry.register(host, OAuth2AuthorizationService.class, authorizationService);
            componentRegistry.register(host, OAuth2AuthorizationConsentService.class, authorizationConsentService);
            componentRegistry.register(host, JWKSet.class, jwkSet);
        });
    }

    public DataSource createDataSource(AuthorizationServerMultitenancyProperties.Tenant tenant) {
        return DataSourceBuilder.create()
                .driverClassName(tenant.getDriverClassName())
                .url(tenant.getUrl())
                .username(tenant.getUsername())
                .password(resolvePassword(tenant))
                .build();
    }

    private String resolvePassword(AuthorizationServerMultitenancyProperties.Tenant tenant) {
        if (tenant.getPasswordSecretRef() == null || tenant.getPasswordSecretRef().isBlank()) {
            return "";
        }
        return tenantSecretResolver.resolve(tenant.getPasswordSecretRef());
    }

    private void initializeAuthorizationServerSchema(DataSource dataSource) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setContinueOnError(true);
        populator.addScript(new ClassPathResource("org/springframework/security/oauth2/server/authorization/client/oauth2-registered-client-schema.sql"));
        populator.addScript(new ClassPathResource("org/springframework/security/oauth2/server/authorization/oauth2-authorization-schema.sql"));
        populator.addScript(new ClassPathResource("org/springframework/security/oauth2/server/authorization/oauth2-authorization-consent-schema.sql"));
        populator.execute(dataSource);
    }

    private void initializeJwkSchema(JdbcTemplate jdbcTemplate) {
        try {
            persistentJwkSetStore.initializeSchema(jdbcTemplate);
        } catch (Exception ignored) {
            // table already exists
        }
    }
}
