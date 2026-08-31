package me.sonam.auth;

import me.sonam.auth.init.ClientSetup;
import me.sonam.auth.multitenancy.AuthorizationServerMultitenancyProperties;
import me.sonam.auth.multitenancy.IssuerAwareAuthorizationServerOperations;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientSetupTest {
    private static final String AUTHZ_MANAGER_CLIENT_ID = "authzmanager-client";
    private static final String SERVICE_ACCOUNT_CLIENT_ID = "service-account";
    private static final String FREE_ISSUER = "https://free.openissuer.com";

    @Test
    void seedConfiguredClientsChecksEachAdditionalIssuerClientOnce() {
        RegisteredClientRepository registeredClientRepository = mock(RegisteredClientRepository.class);
        IssuerAwareAuthorizationServerOperations issuerOperations =
                mock(IssuerAwareAuthorizationServerOperations.class);

        when(registeredClientRepository.findByClientId(AUTHZ_MANAGER_CLIENT_ID))
                .thenReturn(authzManagerClient("https://platform.admin.openissuer.com"));
        when(registeredClientRepository.findByClientId(SERVICE_ACCOUNT_CLIENT_ID))
                .thenReturn(serviceAccountClient());
        when(issuerOperations.findByClientId(FREE_ISSUER, AUTHZ_MANAGER_CLIENT_ID))
                .thenReturn(authzManagerClient("https://free.admin.openissuer.com"));
        when(issuerOperations.findByClientId(FREE_ISSUER, SERVICE_ACCOUNT_CLIENT_ID))
                .thenReturn(serviceAccountClient());

        ClientSetup clientSetup = new ClientSetup();
        ReflectionTestUtils.setField(clientSetup, "registeredClientRepository", registeredClientRepository);
        ReflectionTestUtils.setField(clientSetup, "issuerAwareAuthorizationServerOperations", issuerOperations);
        ReflectionTestUtils.setField(clientSetup, "multitenancyProperties", multitenancyProperties());
        ReflectionTestUtils.setField(clientSetup, "passwordEncoder", mock(PasswordEncoder.class));
        ReflectionTestUtils.setField(clientSetup, "base64ClientIdSecret", encodedServiceAccountCredentials());
        ReflectionTestUtils.setField(clientSetup, "authzManagerId", "authzmanager-id");
        ReflectionTestUtils.setField(clientSetup, "authzManagerClient", AUTHZ_MANAGER_CLIENT_ID);
        ReflectionTestUtils.setField(clientSetup, "authzManagerInitialSecret", "initial-secret");
        ReflectionTestUtils.setField(clientSetup, "authzManagerUri", "https://platform.admin.openissuer.com");
        ReflectionTestUtils.setField(clientSetup, "authzManagerHostLabel", "admin");

        clientSetup.seedConfiguredClients();

        verify(issuerOperations).findByClientId(FREE_ISSUER, AUTHZ_MANAGER_CLIENT_ID);
        verify(issuerOperations).findByClientId(FREE_ISSUER, SERVICE_ACCOUNT_CLIENT_ID);
    }

    private AuthorizationServerMultitenancyProperties multitenancyProperties() {
        AuthorizationServerMultitenancyProperties properties = new AuthorizationServerMultitenancyProperties();
        AuthorizationServerMultitenancyProperties.Tenant tenant =
                new AuthorizationServerMultitenancyProperties.Tenant();
        tenant.setDeploymentNamespace("main");
        tenant.setHosts(List.of("free.openissuer.com"));
        properties.getTenants().put("free", tenant);
        return properties;
    }

    private RegisteredClient authzManagerClient(String baseUri) {
        return RegisteredClient.withId("authzmanager-id")
                .clientId(AUTHZ_MANAGER_CLIENT_ID)
                .clientSecret("{noop}secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(baseUri + "/login/oauth2/code/" + AUTHZ_MANAGER_CLIENT_ID)
                .build();
    }

    private RegisteredClient serviceAccountClient() {
        return RegisteredClient.withId("service-account-id")
                .clientId(SERVICE_ACCOUNT_CLIENT_ID)
                .clientSecret("{noop}secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .build();
    }

    private String encodedServiceAccountCredentials() {
        return Base64.getEncoder().encodeToString(
                (SERVICE_ACCOUNT_CLIENT_ID + ":service-secret").getBytes(StandardCharsets.UTF_8));
    }
}
