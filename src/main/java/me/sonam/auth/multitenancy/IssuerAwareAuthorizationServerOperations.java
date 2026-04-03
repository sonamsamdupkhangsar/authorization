package me.sonam.auth.multitenancy;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
public class IssuerAwareAuthorizationServerOperations {
    private final RegisteredClientRepository registeredClientRepository;
    private final TenantPerHostComponentRegistry componentRegistry;
    private final IssuerContextExecutor issuerContextExecutor;

    public IssuerAwareAuthorizationServerOperations(RegisteredClientRepository registeredClientRepository,
                                                    TenantPerHostComponentRegistry componentRegistry,
                                                    IssuerContextExecutor issuerContextExecutor) {
        this.registeredClientRepository = registeredClientRepository;
        this.componentRegistry = componentRegistry;
        this.issuerContextExecutor = issuerContextExecutor;
    }

    public RegisteredClient findById(String id) {
        return issuerContextExecutor.withCurrentRequestIssuer(() -> registeredClientRepository.findById(id));
    }

    public RegisteredClient findById(String issuer, String id) {
        return issuerContextExecutor.withIssuer(issuer, () -> registeredClientRepository.findById(id));
    }

    public RegisteredClient findByClientId(String clientId) {
        return issuerContextExecutor.withCurrentRequestIssuer(() -> registeredClientRepository.findByClientId(clientId));
    }

    public RegisteredClient findByClientId(String issuer, String clientId) {
        return issuerContextExecutor.withIssuer(issuer, () -> registeredClientRepository.findByClientId(clientId));
    }

    public void save(RegisteredClient registeredClient) {
        issuerContextExecutor.withCurrentRequestIssuer(() -> registeredClientRepository.save(registeredClient));
    }

    public void save(String issuer, RegisteredClient registeredClient) {
        issuerContextExecutor.withIssuer(issuer, () -> {
            registeredClientRepository.save(registeredClient);
            return null;
        });
    }

    public void deleteById(String id) {
        issuerContextExecutor.withCurrentRequestIssuer(() -> {
            JdbcTemplate jdbcTemplate = componentRegistry.get(JdbcTemplate.class);
            Assert.state(jdbcTemplate != null, "JdbcTemplate not found for requested issuer.");
            deleteClientData(jdbcTemplate, id);
        });
    }

    public void deleteById(String issuer, String id) {
        issuerContextExecutor.withIssuer(issuer, () -> {
            JdbcTemplate jdbcTemplate = componentRegistry.get(JdbcTemplate.class);
            Assert.state(jdbcTemplate != null, "JdbcTemplate not found for requested issuer.");
            deleteClientData(jdbcTemplate, id);
            return null;
        });
    }

    private void deleteClientData(JdbcTemplate jdbcTemplate, String id) {
        jdbcTemplate.update("DELETE FROM oauth2_authorization WHERE registered_client_id = ?", id);
        jdbcTemplate.update("DELETE FROM oauth2_authorization_consent WHERE registered_client_id = ?", id);
        jdbcTemplate.update("DELETE FROM oauth2_registered_client WHERE id = ?", id);
    }

    public String currentHost() {
        return issuerContextExecutor.currentHost();
    }

    public String currentIssuer() {
        return issuerContextExecutor.currentIssuer();
    }
}
