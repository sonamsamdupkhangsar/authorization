package me.sonam.auth.init;

import jakarta.annotation.PostConstruct;
import me.sonam.auth.config.AuthorizationServerConfig;
import me.sonam.auth.jpa.entity.Client;
import me.sonam.auth.jpa.repo.ClientRepository;
import me.sonam.auth.service.JpaRegisteredClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

import java.util.Optional;
import java.util.UUID;

@Configuration
public class ClientSetup {
    private static final Logger LOG = LoggerFactory.getLogger(AuthorizationServerConfig.class);

    @Autowired
    private JpaRegisteredClientRepository jpaRegisteredClientRepository;

    @Autowired
    private ClientRepository clientRepository;

    @PostConstruct
    public void registeredClientRepository() {
       Optional<Client> optionalClient  = clientRepository.findByClientId("messaging-client");
        if (optionalClient.isPresent()) {
            LOG.info("delete client: {}", optionalClient.get());
            clientRepository.delete(optionalClient.get());
        }
        if (clientRepository.findByClientId("messaging-client") != null) {
            LOG.info("message-client already exists");
        }
        else {
            RegisteredClient confidentialClient = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId("messaging-client")
                    .clientSecret("{noop}secret")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .redirectUri("http://127.0.0.1:8080/login/oauth2/code/messaging-client-oidc")
                    .redirectUri("http://127.0.0.1:8080/authorized")
                    .scope(OidcScopes.OPENID)
                    .scope(OidcScopes.PROFILE)
                    .scope("message.read")
                    .scope("message.write")
                    .clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).requireProofKey(true).build())
                    .build();
            jpaRegisteredClientRepository.save(confidentialClient);
        }
    }

    //  @PostConstruct
    private void savePublicRegisteredClient() {
        final String clientId = "public-client";
        Optional<Client> cLientOptional = clientRepository.findByClientId(clientId);
        cLientOptional.ifPresent(client -> clientRepository.delete(client));

        RegisteredClient registeredClient = jpaRegisteredClientRepository.findByClientId(clientId);
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
            jpaRegisteredClientRepository.save(registeredClient);

            LOG.info("saved registeredClient");
        }
    }

    // @PostConstruct
    private void savePrivateRegisteredClient() {
        final String clientId = "private-client";
        // Optional<Client> cLientOptional = clientRepository.findByClientId(clientId);
        // cLientOptional.ifPresent(client -> clientRepository.delete(client));

        RegisteredClient registeredClient = jpaRegisteredClientRepository.findByClientId(clientId);
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
                    .redirectUri("http://127.0.0.1:8080/login/oauth2/code/pkce")
                    .redirectUri("http://127.0.0.1:8080/authorized")
                    //.postLogoutRedirectUri("http://127.0.0.1:8080/logged-out")
                    .scope(OidcScopes.OPENID)
                    .scope(OidcScopes.PROFILE)
                    .scope(OidcScopes.EMAIL)
                    .scope("message.read")
                    .scope("message.write")
                    .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false)
                            .requireProofKey(true).build())
                    .build();
            jpaRegisteredClientRepository.save(registeredClient);

            LOG.info("saved registeredClient");
        }
    }
    //@PostConstruct
    private void saveClientCredential() {
        final String clientId = "oauth-client";
        clientRepository.deleteAll();

        RegisteredClient registeredClient = jpaRegisteredClientRepository.findByClientId(clientId);
        if (registeredClient != null) {
            LOG.info("registered client exists");
        }
        else {
            registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId(clientId)
                    .clientSecret("{noop}oauth-secret")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .scope(OidcScopes.OPENID)
                    .scope(OidcScopes.PROFILE)
                    .scope(OidcScopes.EMAIL)
                    .scope("message.read")
                    .scope("message.write")
                    .build();
            jpaRegisteredClientRepository.save(registeredClient);

            LOG.info("save a client-credential");
        }
    }


}