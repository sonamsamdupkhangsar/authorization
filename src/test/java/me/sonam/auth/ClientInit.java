package me.sonam.auth;

import jakarta.annotation.PostConstruct;
import me.sonam.auth.jpa.repo.ClientRepository;
import me.sonam.auth.service.JpaRegisteredClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

import java.util.UUID;
// initilization class for setting up a RegisteredClient used in test cases

@Configuration
public class ClientInit {
    private static final Logger LOG = LoggerFactory.getLogger(ClientInit.class);

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    @Autowired
    private JpaRegisteredClientRepository jpaRegisteredClientRepository;

    @Autowired
    private ClientRepository clientRepository;
    private String clientId = "messaging-client";

    private String clientSecret = "secret";

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostConstruct
    public void saveInitialClient() {
        LOG.info("save messaging client for testing");
        clientRepository.deleteAll();
        if (registeredClientRepository.findByClientId(clientId) == null) {

            RegisteredClient registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId(clientId)
                    .clientSecret(passwordEncoder.encode(clientSecret))
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
            registeredClientRepository.save(registeredClient);
            jpaRegisteredClientRepository.save(registeredClient, "localhost");

            LOG.info("saved initial client");
        }
    }

}
