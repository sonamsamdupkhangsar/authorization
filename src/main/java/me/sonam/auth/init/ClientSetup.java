package me.sonam.auth.init;

import jakarta.annotation.PostConstruct;
import me.sonam.auth.multitenancy.AuthorizationServerMultitenancyProperties;
import me.sonam.auth.multitenancy.IssuerAwareAuthorizationServerOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;
import java.net.URI;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Configuration
public class ClientSetup {
    private static final Logger LOG = LoggerFactory.getLogger(ClientSetup.class);

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    @Autowired
    private IssuerAwareAuthorizationServerOperations issuerAwareAuthorizationServerOperations;

    @Autowired
    private AuthorizationServerMultitenancyProperties multitenancyProperties;

    @Value("${BASE64_CLIENT_ID_SECRET}")
    private String base64ClientIdSecret;

    @Value("${authzmanager-id}")
    private String authzManagerId;

    @Value("${authzmanager-client}")
    private String authzManagerClient;

    @Value("${AUTHZMANAGER_INITIAL_SECRET}")
    private String authzManagerInitialSecret;  //this secret can be changed by user in the authzmanager

    @Value("${authzmanager}")
    private String authzManagerUri;

    @Value("${authzmanager-admin-label:admin}")
    private String authzManagerHostLabel;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostConstruct
    public void seedConfiguredClients() {
        LOG.info("create authzManager and service account clients if they are not created for each issuer");
        String[] serviceAccountCredentials = serviceAccountCredentials();
        String clientId = serviceAccountCredentials[0];
        String secret = serviceAccountCredentials[1];

        seedDefaultIssuerAuthzManagerClient();
        LOG.info("create service account for clientId {}", clientId);
        seedDefaultIssuerServiceAccount(clientId, secret);
        configuredIssuers().forEach(issuer -> seedIssuerClients(issuer, clientId, secret));
    }

    private void seedDefaultIssuerAuthzManagerClient() {
        String expectedAuthzManagerUri = authzManagerUri;
        RegisteredClient registeredClient = registeredClientRepository.findByClientId(authzManagerClient);
        if (registeredClient != null && hasAuthzManagerRedirectUri(registeredClient, expectedAuthzManagerUri)) {
            LOG.info("authzmanager exists in default issuer store");
            return;
        }
        if (registeredClient != null) {
            LOG.info("updating authzmanager redirect uri in default issuer store to {}", expectedAuthzManagerUri);
        }
        registeredClientRepository.save(buildAuthzManagerClient(registeredClient, expectedAuthzManagerUri));
        LOG.info("saved authzmanager client in default issuer store");
    }

    private void seedAuthzManagerClient(String issuer) {
        String expectedAuthzManagerUri = authzManagerUriForIssuer(issuer);
        RegisteredClient registeredClient = issuerAwareAuthorizationServerOperations.findByClientId(issuer, authzManagerClient);
        if (registeredClient != null && hasAuthzManagerRedirectUri(registeredClient, expectedAuthzManagerUri)) {
            LOG.info("authzmanager exists for issuer {}", issuer);
            return;
        }
        if (registeredClient != null) {
            LOG.info("updating authzmanager redirect uri for issuer {} to {}", issuer, expectedAuthzManagerUri);
        }
        issuerAwareAuthorizationServerOperations.save(issuer, buildAuthzManagerClient(registeredClient, expectedAuthzManagerUri));
        LOG.info("saved authzmanager client for issuer {}", issuer);
    }

    private boolean hasAuthzManagerRedirectUri(RegisteredClient registeredClient, String authzManagerBaseUri) {
        return registeredClient.getRedirectUris().contains(authzManagerRedirectUri(authzManagerBaseUri));
    }

    private String authzManagerRedirectUri(String authzManagerBaseUri) {
        return authzManagerBaseUri + "/login/oauth2/code/" + authzManagerClient;
    }

    private RegisteredClient buildAuthzManagerClient(RegisteredClient existingClient, String authzManagerBaseUri) {
        String id = existingClient == null ? authzManagerId : existingClient.getId();
        String secret = existingClient == null ? passwordEncoder.encode(authzManagerInitialSecret) : existingClient.getClientSecret();
        return buildAuthzManagerClient(id, secret, authzManagerBaseUri);
    }

    private RegisteredClient buildAuthzManagerClient(String authzManagerBaseUri) {
        return buildAuthzManagerClient(authzManagerId, passwordEncoder.encode(authzManagerInitialSecret), authzManagerBaseUri);
    }

    private RegisteredClient buildAuthzManagerClient(String id, String clientSecret, String authzManagerBaseUri) {
        TokenSettings tokenSettings = TokenSettings.builder().accessTokenTimeToLive(Duration.ofSeconds(1200)).build();
        return RegisteredClient.withId(id)
                .clientId(authzManagerClient)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .redirectUri(authzManagerRedirectUri(authzManagerBaseUri))
                .tokenSettings(tokenSettings)
                .build();
    }

    private String authzManagerUriForIssuer(String issuer) {
        URI seedBaseUri = URI.create(authzManagerUri);
        URI issuerUri = URI.create(issuer);
        String adminHost = toAdminHost(issuerUri.getHost(), seedBaseUri.getHost());
        int port = seedBaseUri.getPort();
        boolean defaultPort = port < 0
                || ("http".equalsIgnoreCase(seedBaseUri.getScheme()) && port == 80)
                || ("https".equalsIgnoreCase(seedBaseUri.getScheme()) && port == 443);
        String portSegment = defaultPort ? "" : ":" + port;
        String path = seedBaseUri.getPath() == null ? "" : seedBaseUri.getPath();

        return seedBaseUri.getScheme() + "://" + adminHost + portSegment + path;
    }

    private String toAdminHost(String issuerHost, String fallbackHost) {
        if (issuerHost == null || issuerHost.isBlank()) {
            return fallbackHost;
        }
        String adminSegment = "." + authzManagerHostLabel + ".";
        if (issuerHost.contains(adminSegment)) {
            return issuerHost;
        }
        int firstDot = issuerHost.indexOf('.');
        if (firstDot < 0) {
            return fallbackHost;
        }
        return issuerHost.substring(0, firstDot) + adminSegment + issuerHost.substring(firstDot + 1);
    }

    private void seedDefaultIssuerServiceAccount(String clientId, String secret) {
        RegisteredClient registeredClient = registeredClientRepository.findByClientId(clientId);
        if (registeredClient != null) {
            LOG.info("keeping existing service-account client in default issuer store: {}", registeredClient.getClientId());
            return;
        }
        registeredClientRepository.save(buildServiceAccount(clientId, secret));
        LOG.info("saved service-account client in default issuer store");
    }

    private void seedServiceAccount(String issuer, String clientId, String secret) {
        RegisteredClient registeredClient = issuerAwareAuthorizationServerOperations.findByClientId(issuer, clientId);
        if (registeredClient != null) {
            LOG.info("keeping existing service-account client for issuer {}: {}", issuer, registeredClient.getClientId());
            return;
        }
        issuerAwareAuthorizationServerOperations.save(issuer, buildServiceAccount(clientId, secret));
        LOG.info("saved service-account client for issuer {}", issuer);
    }

    private RegisteredClient buildServiceAccount(String clientId, String secret) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientSecret(passwordEncoder.encode(secret))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .scope("message.read")
                .scope("message.write")
                .build();
    }

    private Set<String> configuredIssuers() {
        Set<String> issuers = new LinkedHashSet<>();
        if (!multitenancyProperties.isAdditionalTenantsEnabled()) {
            return issuers;
        }
        multitenancyProperties.getTenants().values().stream()
                .filter(multitenancyProperties::shouldLoadTenant)
                .forEach(tenant ->
                        tenant.getHosts().forEach(host -> issuers.add(toIssuer(host))));
        return issuers;
    }

    private String toIssuer(String host) {
        if (host.equals("localhost") || host.equals("127.0.0.1")) {
            return "http://" + host;
        }
        return "https://" + host;
    }

    public void seedIssuerClients(String issuer) {
        String[] serviceAccountCredentials = serviceAccountCredentials();
        seedIssuerClients(issuer, serviceAccountCredentials[0], serviceAccountCredentials[1]);
    }

    private void seedIssuerClients(String issuer, String clientId, String secret) {
        seedAuthzManagerClient(issuer);
        seedServiceAccount(issuer, clientId, secret);
    }

    private String[] serviceAccountCredentials() {
        String decodedString = new String(Base64.getDecoder().decode(base64ClientIdSecret));
        return decodedString.split(":", 2);
    }
}
