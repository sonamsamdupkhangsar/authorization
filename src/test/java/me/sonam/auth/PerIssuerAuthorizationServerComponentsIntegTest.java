package me.sonam.auth;

import me.sonam.auth.multitenancy.IssuerAwareAuthorizationServerOperations;
import me.sonam.auth.multitenancy.TenantOnboardingService;
import me.sonam.auth.multitenancy.TenantRegistrationRequest;
import me.sonam.auth.jpa.repo.TenantRegistrationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureWebTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class PerIssuerAuthorizationServerComponentsIntegTest {
    private static final String BUSINESS1_ISSUER = "https://business1.openissuer.test";
    private static final String BUSINESS2_ISSUER = "https://business2.openissuer.test";
    private static final String BUSINESS1_HOST = "business1.openissuer.test";
    private static final String BUSINESS2_HOST = "business2.openissuer.test";
    private static final String SHARED_CLIENT_ID = "shared-client";

    @Autowired
    private IssuerAwareAuthorizationServerOperations issuerAwareAuthorizationServerOperations;

    @Autowired
    private TenantOnboardingService tenantOnboardingService;

    @Autowired
    private TenantRegistrationRepository tenantRegistrationRepository;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @LocalServerPort
    private int port;

    @Value("${authzmanager-client}")
    private String authzmanagerClientId;

    @Value("${BASE64_CLIENT_ID_SECRET}")
    private String base64ClientIdSecret;

    @BeforeEach
    void cleanSharedClients() {
        deleteIfPresent(BUSINESS1_ISSUER, SHARED_CLIENT_ID);
        deleteIfPresent(BUSINESS2_ISSUER, SHARED_CLIENT_ID);
    }

    @AfterEach
    void cleanDiscoveryTestClients() {
        deleteIfPresent(BUSINESS1_ISSUER, SHARED_CLIENT_ID);
        deleteIfPresent(BUSINESS2_ISSUER, SHARED_CLIENT_ID);
    }

    @Test
    void sameClientIdCanExistAcrossDifferentIssuers() {
        RegisteredClient business1Client = registeredClient("business1-secret");
        RegisteredClient business2Client = registeredClient("business2-secret");

        issuerAwareAuthorizationServerOperations.save(BUSINESS1_ISSUER, business1Client);
        issuerAwareAuthorizationServerOperations.save(BUSINESS2_ISSUER, business2Client);

        RegisteredClient foundInBusiness1 =
                issuerAwareAuthorizationServerOperations.findByClientId(BUSINESS1_ISSUER, SHARED_CLIENT_ID);
        RegisteredClient foundInBusiness2 =
                issuerAwareAuthorizationServerOperations.findByClientId(BUSINESS2_ISSUER, SHARED_CLIENT_ID);

        assertThat(foundInBusiness1).isNotNull();
        assertThat(foundInBusiness2).isNotNull();
        assertThat(foundInBusiness1.getId()).isEqualTo(business1Client.getId());
        assertThat(foundInBusiness2.getId()).isEqualTo(business2Client.getId());
        assertThat(foundInBusiness1.getClientSecret()).isEqualTo("{noop}business1-secret");
        assertThat(foundInBusiness2.getClientSecret()).isEqualTo("{noop}business2-secret");
    }

    @Test
    void bootstrapClientsAreSeededPerIssuer() {
        String serviceAccountClientId = Base64.getDecoder().decode(base64ClientIdSecret).length > 0
                ? new String(Base64.getDecoder().decode(base64ClientIdSecret)).split(":")[0]
                : "";

        assertThat(issuerAwareAuthorizationServerOperations.findByClientId(BUSINESS1_ISSUER, authzmanagerClientId)).isNotNull();
        assertThat(issuerAwareAuthorizationServerOperations.findByClientId(BUSINESS2_ISSUER, authzmanagerClientId)).isNotNull();
        assertThat(issuerAwareAuthorizationServerOperations.findByClientId(BUSINESS1_ISSUER, serviceAccountClientId)).isNotNull();
        assertThat(issuerAwareAuthorizationServerOperations.findByClientId(BUSINESS2_ISSUER, serviceAccountClientId)).isNotNull();
    }

    @Test
    void canDynamicallyRegisterNewTenantIssuer() {
        String tenantName = "dynamic-" + UUID.randomUUID();
        String host = tenantName + ".openissuer.test";
        String issuer = "https://" + host;

        TenantRegistrationRequest request = new TenantRegistrationRequest();
        request.setTenantName(tenantName);
        request.setHosts(java.util.List.of(host));
        request.setUrl("jdbc:hsqldb:mem:" + tenantName);
        request.setUsername("sa");
        request.setPasswordSecretRef("dynamic-tenant-db-password");
        request.setDriverClassName("org.hsqldb.jdbc.JDBCDriver");

        tenantOnboardingService.registerTenant(request);

        RegisteredClient dynamicClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("dynamic-client")
                .clientSecret("{noop}dynamic-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("message.read")
                .build();

        issuerAwareAuthorizationServerOperations.save(issuer, dynamicClient);

        assertThat(issuerAwareAuthorizationServerOperations.findByClientId(issuer, "dynamic-client")).isNotNull();
        assertThat(issuerAwareAuthorizationServerOperations.findByClientId(issuer, authzmanagerClientId)).isNotNull();
        assertThat(tenantRegistrationRepository.findById(tenantName)).isPresent();
    }

    @Test
    void discoveryJwksAndTokenEndpointsAreIsolatedPerHost() {
        issuerAwareAuthorizationServerOperations.save(BUSINESS1_ISSUER, tokenEndpointRegisteredClient("business1-secret"));
        issuerAwareAuthorizationServerOperations.save(BUSINESS2_ISSUER, tokenEndpointRegisteredClient("business2-secret"));

        String business1Issuer = issuerFor(BUSINESS1_HOST);
        String business2Issuer = issuerFor(BUSINESS2_HOST);

        webTestClient.get()
                .uri("/.well-known/openid-configuration")
                .header(HttpHeaders.HOST, BUSINESS1_HOST + ":" + port)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.issuer").isEqualTo(business1Issuer)
                .jsonPath("$.token_endpoint").isEqualTo(business1Issuer + "/oauth2/token")
                .jsonPath("$.jwks_uri").isEqualTo(business1Issuer + "/oauth2/jwks");

        webTestClient.get()
                .uri("/.well-known/openid-configuration")
                .header(HttpHeaders.HOST, BUSINESS2_HOST + ":" + port)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.issuer").isEqualTo(business2Issuer)
                .jsonPath("$.token_endpoint").isEqualTo(business2Issuer + "/oauth2/token")
                .jsonPath("$.jwks_uri").isEqualTo(business2Issuer + "/oauth2/jwks");

        String business1Kid = getJwkKid(BUSINESS1_HOST);
        String business2Kid = getJwkKid(BUSINESS2_HOST);
        assertThat(business1Kid).isNotBlank();
        assertThat(business2Kid).isNotBlank();
        assertThat(business1Kid).isNotEqualTo(business2Kid);

        requestClientCredentialsToken(BUSINESS1_HOST, SHARED_CLIENT_ID, "business1-secret")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.access_token").exists()
                .jsonPath("$.token_type").isEqualTo("Bearer");

        requestClientCredentialsToken(BUSINESS2_HOST, SHARED_CLIENT_ID, "business2-secret")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.access_token").exists()
                .jsonPath("$.token_type").isEqualTo("Bearer");

        requestClientCredentialsToken(BUSINESS1_HOST, SHARED_CLIENT_ID, "business2-secret")
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo("invalid_client");

        requestClientCredentialsToken(BUSINESS2_HOST, SHARED_CLIENT_ID, "business1-secret")
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo("invalid_client");
    }

    private RegisteredClient registeredClient(String secret) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(SHARED_CLIENT_ID)
                .clientSecret("{noop}" + secret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("message.read")
                .build();
    }

    private RegisteredClient tokenEndpointRegisteredClient(String secret) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(SHARED_CLIENT_ID)
                .clientSecret(passwordEncoder.encode(secret))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("message.read")
                .build();
    }

    private void deleteIfPresent(String issuer, String clientId) {
        RegisteredClient registeredClient = issuerAwareAuthorizationServerOperations.findByClientId(issuer, clientId);
        if (registeredClient != null) {
            issuerAwareAuthorizationServerOperations.deleteById(issuer, registeredClient.getId());
        }
    }

    private String issuerFor(String host) {
        return "http://" + host + ":" + port;
    }

    private String getJwkKid(String host) {
        String body = webTestClient.get()
                .uri("/oauth2/jwks")
                .header(HttpHeaders.HOST, host + ":" + port)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        assertThat(body).isNotBlank();
        Map<String, Object> map = JsonParserFactory.getJsonParser().parseMap(body);
        List<Map<String, Object>> keys = (List<Map<String, Object>>) map.get("keys");
        assertThat(keys).isNotEmpty();
        return keys.get(0).get("kid").toString();
    }

    private WebTestClient.ResponseSpec requestClientCredentialsToken(String host, String clientId, String secret) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "client_credentials");
        formData.add("scope", "message.read");

        return webTestClient.post()
                .uri("/oauth2/token")
                .header(HttpHeaders.HOST, host + ":" + port)
                .headers(headers -> headers.setBasicAuth(clientId, secret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(formData)
                .exchange();
    }
}
