package me.sonam.auth.init;

import jakarta.annotation.PostConstruct;
import me.sonam.auth.jpa.entity.Client;
import me.sonam.auth.jpa.repo.ClientRepository;
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
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Optional;
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

    @Autowired
    private ClientRepository clientRepository;

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


    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostConstruct
    public void createAuthzManagerClient() {
        LOG.info("create authzManager client if it is not created for each issuer");
        seedDefaultIssuerAuthzManagerClient();
        configuredIssuers().forEach(this::seedIssuerClients);
    }

    @PostConstruct
    public void createServiceAccount() {
        String decodedString = new String(Base64.getDecoder().decode(base64ClientIdSecret));
        String[] clientIdSecretArray = decodedString.split(":");
        final String clientId = clientIdSecretArray[0];
        final String secret = clientIdSecretArray[1];

        LOG.info("clientId: {}, secret: {}", clientId, secret);

        seedDefaultIssuerServiceAccount(clientId, secret);
        configuredIssuers().forEach(this::seedIssuerClients);
/*
        final String nextJsClientId = "nextjs-client";

        registeredClient = registeredClientRepository.findByClientId(nextJsClientId);

        if (registeredClient != null) {
            LOG.info("found registered client");
        }
        else {
            ClientSettings clientSettings = ClientSettings.builder()
                    .requireAuthorizationConsent(true)
                    .requireProofKey(false)
                    .build();

            registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientSettings(clientSettings)
                    .clientId(nextJsClientId)
                    .clientSecret("{noop}nextjs-secret")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .redirectUri("http://localhost:3001/api/auth/callback/myauth")
                    .scope(OidcScopes.OPENID)
                    .scope(OidcScopes.PROFILE)
                    .scope(OidcScopes.EMAIL)
                    .scope("message.read")
                    .scope("message.write")
                    .build();
            registeredClientRepository.save(registeredClient);

            LOG.info("save a client-credential");
        }*/
    }

    //test cases depend on this
    //@PostConstruct
    public void saveClient() {
        RegisteredClient registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("messaging-client")
                .clientSecret("{noop}secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_JWT)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .redirectUri("http://127.0.0.1:8080/login/oauth2/code/messaging-client-oidc")
                .redirectUri("http://127.0.0.1:8080/authorized")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope("message.read")
                .scope("message.write")
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).requireProofKey(false).build())
                .build();

        // Save registered client in db as if in-memory
        //JdbcRegisteredClientRepository registeredClientRepository = new JdbcRegisteredClientRepository(jdbcTemplate);
        registeredClientRepository.save(registeredClient);

        //	return registeredClientRepository;
    }

    //@PostConstruct
    public void saveAnotherClient() {
        LOG.info("save myclient");
        final String myclient = "myclient";
        if (registeredClientRepository.findByClientId(myclient) == null) {
            RegisteredClient registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId(myclient)
                    .clientSecret("{noop}secret")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_JWT)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .redirectUri("http://127.0.0.1:8080/login/oauth2/code/myclient-oidc")
                    .redirectUri("http://127.0.0.1:8080/authorized")
                    .scope(OidcScopes.OPENID)
                    .scope(OidcScopes.PROFILE)
                    .scope("message.read")
                    .scope("message.write")
                    .clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).requireProofKey(false).build())
                    .build();

            // Save registered client in db as if in-memory
            //JdbcRegisteredClientRepository registeredClientRepository = new JdbcRegisteredClientRepository(jdbcTemplate);
            registeredClientRepository.save(registeredClient);
        }
        //	return registeredClientRepository;
    }

    //  @PostConstruct
    private void savePublicRegisteredClient() {
        final String clientId = "public-client";
        Optional<Client> cLientOptional = clientRepository.findByClientId(clientId);
        cLientOptional.ifPresent(client -> clientRepository.delete(client));

        RegisteredClient registeredClient = registeredClientRepository.findByClientId(clientId);
        if (registeredClient != null) {
            LOG.info("registered public client exists");
        }
        else {
            registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId(clientId)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    //.redirectUri("http://localhost:8080")
                    // .redirectUri("http://127.0.0.1:8080/login/oauth2/code/pkce")
                    .redirectUri("http://127.0.0.1:8080")
                    .scope(OidcScopes.OPENID)
                    .scope(OidcScopes.PROFILE)
                    .scope("message.read")
                    .scope("message.write")
                    .clientSettings(ClientSettings.builder().requireAuthorizationConsent(true)
                            .requireProofKey(true).build())
                    .build();
            registeredClientRepository.save(registeredClient);

            LOG.info("saved registeredClient");
        }
    }

    // @PostConstruct
  /*  private void savePrivateRegisteredClient() {
        final String clientId = "private-client";

        RegisteredClient registeredClient = registeredClientRepository.findByClientId(clientId);
        if (registeredClient != null) {
            LOG.info("registered private client exists");
        }
        else {
            registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId(clientId)
                    .clientSecret("{noop}secret")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri(privateClientUrl+"/login/oauth2/code/pkce") //+"http://127.0.0.1:8080/login/oauth2/code/pkce")
                    .redirectUri(privateClientUrl+"/authorized") //http://127.0.0.1:8080/authorized")
                    .postLogoutRedirectUri(privateClientUrl+"/logged-out") //http://127.0.0.1:8080/logged-out")
                    .scope(OidcScopes.OPENID)
                    .scope(OidcScopes.PROFILE)
                    .scope(OidcScopes.EMAIL)
                    .scope("message.read")
                    .scope("message.write")
                    .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false)
                            .requireProofKey(true).build())
                    .build();
            registeredClientRepository.save(registeredClient);

            LOG.info("saved registeredClient");
        }
    }
*/

    //@PostConstruct
    private void saveArticlesClient() {
        final String clientId = "articles-client";

        //clientRepository.deleteAll();

        if (registeredClientRepository.findByClientId(clientId) != null) {
            LOG.info("registered articles client exists");
        }
        else {
            ClientSettings clientSettings = ClientSettings.builder()
                    .requireAuthorizationConsent(true)
                    .requireProofKey(false)
                    .build();

            RegisteredClient registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientSettings(clientSettings)
                    .clientId(clientId)
                    .clientSecret("{noop}secret")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .redirectUri("http://127.0.0.1:8080/login/oauth2/code/articles-client-oidc")
                    .scope(OidcScopes.OPENID)
                    .scope("articles.read")
                    .scope("articles.write")
                    .build();

            registeredClientRepository.save(registeredClient);
            LOG.info("saved arcticles client oidc");
        }
    }

    //@PostConstruct
    public void deleteAll() {
        clientRepository.deleteAll();
    }

   //@PostConstruct


    private void seedDefaultIssuerAuthzManagerClient() {
        RegisteredClient registeredClient = registeredClientRepository.findByClientId(authzManagerClient);
        if (registeredClient != null) {
            LOG.info("authzmanager exists in default issuer store");
            return;
        }
        registeredClientRepository.save(buildAuthzManagerClient());
        LOG.info("saved authzmanager client in default issuer store");
    }

    private void seedAuthzManagerClient(String issuer) {
        RegisteredClient registeredClient = issuerAwareAuthorizationServerOperations.findByClientId(issuer, authzManagerClient);
        if (registeredClient != null) {
            LOG.info("authzmanager exists for issuer {}", issuer);
            return;
        }
        issuerAwareAuthorizationServerOperations.save(issuer, buildAuthzManagerClient());
        LOG.info("saved authzmanager client for issuer {}", issuer);
    }

    private RegisteredClient buildAuthzManagerClient() {
        TokenSettings tokenSettings = TokenSettings.builder().accessTokenTimeToLive(Duration.ofSeconds(1200)).build();
        return RegisteredClient.withId(authzManagerId)
                .clientId(authzManagerClient)
                .clientSecret(passwordEncoder.encode(authzManagerInitialSecret))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .redirectUri(authzManagerUri + "/login/oauth2/code/" + authzManagerClient)
                .tokenSettings(tokenSettings)
                .build();
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
        multitenancyProperties.getTenants().values().forEach(tenant ->
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
        seedAuthzManagerClient(issuer);
        String decodedString = new String(Base64.getDecoder().decode(base64ClientIdSecret));
        String[] clientIdSecretArray = decodedString.split(":");
        seedServiceAccount(issuer, clientIdSecretArray[0], clientIdSecretArray[1]);
    }
}
