package me.sonam.auth.config;

import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import me.sonam.auth.jpa.entity.TenantRegistration;
import me.sonam.auth.jpa.repo.TenantRegistrationRepository;
import me.sonam.auth.multitenancy.AuthorizationServerMultitenancyProperties;
import me.sonam.auth.multitenancy.IssuerComponentRegistrar;
import me.sonam.auth.multitenancy.TenantPerHostComponentRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.util.Assert;
import org.springframework.web.filter.ForwardedHeaderFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        AuthorizationServerMultitenancyProperties.class,
        ClientLimitProperties.class,
        DemoCleanupProperties.class,
        OrganizationSeedProperties.class,
        SignupPolicyProperties.class
})
public class PerIssuerAuthorizationServerComponentsConfig {
    @Bean
    public TenantPerHostComponentRegistry tenantPerHostComponentRegistry(
            DataSource dataSource,
            AuthorizationServerMultitenancyProperties multitenancyProperties,
            IssuerComponentRegistrar issuerComponentRegistrar,
            TenantRegistrationRepository tenantRegistrationRepository) {
        TenantPerHostComponentRegistry componentRegistry = new TenantPerHostComponentRegistry();
        registerDefaultHosts(multitenancyProperties, componentRegistry);
        issuerComponentRegistrar.registerDefaultComponents(dataSource, componentRegistry);
        multitenancyProperties.getTenants().values().forEach(tenant -> {
            Assert.state(!tenant.getHosts().isEmpty(), "at least one host must be configured");
            issuerComponentRegistrar.registerTenantComponents(
                    issuerComponentRegistrar.createDataSource(tenant),
                    tenant.getHosts(),
                    componentRegistry
            );
        });
        tenantRegistrationRepository.findAll().forEach(registration -> registerPersistedTenant(
                registration, multitenancyProperties, issuerComponentRegistrar, componentRegistry));
        return componentRegistry;
    }

    @Bean
    public ForwardedHeaderFilter forwardedHeaderFilter() {
        return new ForwardedHeaderFilter();
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .multipleIssuersAllowed(true)
                .build();
    }

    @Bean
    @Primary
    public RegisteredClientRepository registeredClientRepository(
            TenantPerHostComponentRegistry componentRegistry) {
        return new DelegatingRegisteredClientRepository(componentRegistry);
    }

    @Bean
    @Primary
    public OAuth2AuthorizationService authorizationService(
            TenantPerHostComponentRegistry componentRegistry,
            RegisteredClientRepository registeredClientRepository) {
        return new DelegatingOAuth2AuthorizationService(componentRegistry);
    }

    @Bean
    @Primary
    public OAuth2AuthorizationConsentService authorizationConsentService(
            TenantPerHostComponentRegistry componentRegistry,
            RegisteredClientRepository registeredClientRepository) {
        return new DelegatingOAuth2AuthorizationConsentService(componentRegistry);
    }

    @Bean
    @Primary
    public JWKSource<SecurityContext> jwkSource(
            TenantPerHostComponentRegistry componentRegistry) {
        return new DelegatingJwkSource(componentRegistry);
    }

    private void registerDefaultHosts(AuthorizationServerMultitenancyProperties properties,
                                      TenantPerHostComponentRegistry componentRegistry) {
        if (!properties.getDefaultHosts().isEmpty()) {
            properties.getDefaultHosts().forEach(componentRegistry::registerDefaultHost);
            return;
        }
        componentRegistry.registerDefaultHost("localhost");
        componentRegistry.registerDefaultHost("127.0.0.1");
    }

    private void registerPersistedTenant(TenantRegistration registration,
                                         AuthorizationServerMultitenancyProperties multitenancyProperties,
                                         IssuerComponentRegistrar issuerComponentRegistrar,
                                         TenantPerHostComponentRegistry componentRegistry) {
        if (multitenancyProperties.getTenants().containsKey(registration.getTenantName())) {
            return;
        }
        AuthorizationServerMultitenancyProperties.Tenant tenant = new AuthorizationServerMultitenancyProperties.Tenant();
        tenant.setHosts(Arrays.stream(registration.getHosts().split(","))
                .map(String::trim)
                .filter(host -> !host.isEmpty())
                .collect(Collectors.toList()));
        tenant.setUrl(registration.getUrl());
        tenant.setUsername(registration.getUsername());
        tenant.setPasswordSecretRef(registration.getPasswordSecretRef());
        tenant.setDriverClassName(registration.getDriverClassName());
        issuerComponentRegistrar.registerTenantComponents(
                issuerComponentRegistrar.createDataSource(tenant),
                tenant.getHosts(),
                componentRegistry
        );
        multitenancyProperties.getTenants().put(registration.getTenantName(), tenant);
    }

    private static final class DelegatingRegisteredClientRepository implements RegisteredClientRepository {
        private static final Logger LOG = LoggerFactory.getLogger(DelegatingRegisteredClientRepository.class);
        private final TenantPerHostComponentRegistry componentRegistry;

        private DelegatingRegisteredClientRepository(TenantPerHostComponentRegistry componentRegistry) {
            this.componentRegistry = componentRegistry;
        }

        @Override
        public void save(org.springframework.security.oauth2.server.authorization.client.RegisteredClient registeredClient) {
            getRegisteredClientRepository().save(registeredClient);
        }

        @Override
        public org.springframework.security.oauth2.server.authorization.client.RegisteredClient findById(String id) {
            return getRegisteredClientRepository().findById(id);
        }

        @Override
        public org.springframework.security.oauth2.server.authorization.client.RegisteredClient findByClientId(String clientId) {
            return getRegisteredClientRepository().findByClientId(clientId);
        }

        private RegisteredClientRepository getRegisteredClientRepository() {
            RegisteredClientRepository registeredClientRepository = this.componentRegistry.get(RegisteredClientRepository.class);
            if (registeredClientRepository == null) {
                String issuer = this.componentRegistry.resolveCurrentIssuer();
                String host = this.componentRegistry.resolveCurrentHost();
                LOG.error("RegisteredClientRepository lookup failed for issuer '{}' host '{}' defaultHosts {}",
                        issuer, host, this.componentRegistry.getDefaultHosts());
                Assert.state(false, "RegisteredClientRepository not found for requested issuer. issuer="
                        + issuer + ", host=" + host + ", defaultHosts=" + this.componentRegistry.getDefaultHosts());
            }
            return registeredClientRepository;
        }
    }

    private static final class DelegatingOAuth2AuthorizationService implements OAuth2AuthorizationService {
        private final TenantPerHostComponentRegistry componentRegistry;

        private DelegatingOAuth2AuthorizationService(TenantPerHostComponentRegistry componentRegistry) {
            this.componentRegistry = componentRegistry;
        }

        @Override
        public void save(org.springframework.security.oauth2.server.authorization.OAuth2Authorization authorization) {
            getAuthorizationService().save(authorization);
        }

        @Override
        public void remove(org.springframework.security.oauth2.server.authorization.OAuth2Authorization authorization) {
            getAuthorizationService().remove(authorization);
        }

        @Override
        public org.springframework.security.oauth2.server.authorization.OAuth2Authorization findById(String id) {
            return getAuthorizationService().findById(id);
        }

        @Override
        public org.springframework.security.oauth2.server.authorization.OAuth2Authorization findByToken(String token, org.springframework.security.oauth2.server.authorization.OAuth2TokenType tokenType) {
            return getAuthorizationService().findByToken(token, tokenType);
        }

        private OAuth2AuthorizationService getAuthorizationService() {
            OAuth2AuthorizationService authorizationService = this.componentRegistry.get(OAuth2AuthorizationService.class);
            Assert.state(authorizationService != null, "OAuth2AuthorizationService not found for requested issuer.");
            return authorizationService;
        }
    }

    private static final class DelegatingOAuth2AuthorizationConsentService implements OAuth2AuthorizationConsentService {
        private final TenantPerHostComponentRegistry componentRegistry;

        private DelegatingOAuth2AuthorizationConsentService(TenantPerHostComponentRegistry componentRegistry) {
            this.componentRegistry = componentRegistry;
        }

        @Override
        public void save(org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent authorizationConsent) {
            getAuthorizationConsentService().save(authorizationConsent);
        }

        @Override
        public void remove(org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent authorizationConsent) {
            getAuthorizationConsentService().remove(authorizationConsent);
        }

        @Override
        public org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent findById(String registeredClientId, String principalName) {
            return getAuthorizationConsentService().findById(registeredClientId, principalName);
        }

        private OAuth2AuthorizationConsentService getAuthorizationConsentService() {
            OAuth2AuthorizationConsentService authorizationConsentService = this.componentRegistry.get(OAuth2AuthorizationConsentService.class);
            Assert.state(authorizationConsentService != null, "OAuth2AuthorizationConsentService not found for requested issuer.");
            return authorizationConsentService;
        }
    }

    private static final class DelegatingJwkSource implements JWKSource<SecurityContext> {
        private static final Logger LOG = LoggerFactory.getLogger(DelegatingJwkSource.class);
        private final TenantPerHostComponentRegistry componentRegistry;

        private DelegatingJwkSource(TenantPerHostComponentRegistry componentRegistry) {
            this.componentRegistry = componentRegistry;
        }

        @Override
        public List<JWK> get(JWKSelector jwkSelector, SecurityContext context) throws KeySourceException {
            JWKSet jwkSet = this.componentRegistry.get(JWKSet.class);
            if (jwkSet == null) {
                String issuer = this.componentRegistry.resolveCurrentIssuer();
                String host = this.componentRegistry.resolveCurrentHost();
                LOG.error("JWKSet lookup failed for issuer '{}' host '{}' defaultHosts {}",
                        issuer, host, this.componentRegistry.getDefaultHosts());
                Assert.state(false, "JWKSet not found for requested issuer. issuer="
                        + issuer + ", host=" + host + ", defaultHosts=" + this.componentRegistry.getDefaultHosts());
            }
            return jwkSelector.select(jwkSet);
        }
    }
}
