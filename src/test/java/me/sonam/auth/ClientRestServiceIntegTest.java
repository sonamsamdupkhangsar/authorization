package me.sonam.auth;

import me.sonam.auth.jpa.entity.ClientOrganization;
import me.sonam.auth.jpa.repo.ClientOrganizationRepository;
import me.sonam.auth.jpa.repo.ClientRepository;
import me.sonam.auth.jpa.repo.HClientUserRepository;
import me.sonam.auth.multitenancy.IssuerAwareAuthorizationServerOperations;
import me.sonam.auth.mocks.TestSecurityConfig;
import me.sonam.auth.mocks.WithMockCustomUser;
import me.sonam.auth.service.JpaRegisteredClientRepository;
import me.sonam.auth.util.CustomPair;
import me.sonam.auth.util.CustomRestPage;
import me.sonam.auth.util.JwtUtil;
import me.sonam.auth.util.TokenRequestFilter;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.context.WebApplicationContext;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/**
 * this is for testing 'clients' endpoint
 */
@Import({TestSecurityConfig.class, ClientInit.class})
@EnableAutoConfiguration
@ExtendWith(SpringExtension.class)
@SpringBootTest( webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = {DefaultAuthorizationServerApplication.class})
@AutoConfigureMockMvc
@AutoConfigureWebTestClient
public class ClientRestServiceIntegTest {
    private static final Logger LOG = LoggerFactory.getLogger(ClientRestServiceIntegTest.class);
    @Value("classpath:client-credential-access-token.json")
    private Resource refreshTokenResource;
    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JpaRegisteredClientRepository jpaRegisteredClientRepository;

    @Autowired
    private IssuerAwareAuthorizationServerOperations issuerAwareAuthorizationServerOperations;

    @Autowired
    private HClientUserRepository clientUserRepository;
    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private ClientOrganizationRepository clientOrganizationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final String tokenValue ="my-dummy-token";
    @MockitoBean
    ReactiveJwtDecoder jwtDecoder;

    @Autowired
    private TokenRequestFilter tokenRequestFilter;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    WebApplicationContext context;

    @LocalServerPort
    private int port;

    UUID clientId = UUID.randomUUID();
    //UUID messageClient = UUID.randomUUID();
    //String clientId = "test-private-client";  //this is created in the test
    private String messageClient = "messaging-client";
    private String clientSecret = "secret";
    private String base64ClientSecret = Base64.getEncoder().encodeToString(new StringBuilder(messageClient.toString())
            .append(":").append(clientSecret).toString().getBytes());

    private static MockWebServer mockWebServer;

    @org.junit.jupiter.api.BeforeEach
    public void setup() {
        this.webTestClient = MockMvcWebTestClient
                .bindToApplicationContext(this.context)
                // add Spring Security test Support
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .configureClient()
                .build();
    }

    @BeforeAll
    static void setupMockWebServer() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        LOG.info("host: {}, port: {}", mockWebServer.getHostName(), mockWebServer.getPort());
    }

    @AfterAll
    public static void shutdownMockWebServer() throws IOException {
        LOG.info("shutdown and close mockWebServer");
        mockWebServer.shutdown();
        mockWebServer.close();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) throws IOException {
        r.add("auth-server.root", () -> "http://localhost:"+mockWebServer.getPort());
        r.add("oauth2-token-mediator.root", () -> "http://localhost:"+mockWebServer.getPort());
        r.add("setting-rest-service.root", () -> "http://localhost:"+mockWebServer.getPort());
        r.add("role-rest-service.root", () -> "http://localhost:"+mockWebServer.getPort());
        r.add("organization-rest-service.root", () -> "http://localhost:"+mockWebServer.getPort());
    }


    @AfterEach
    public void cleanoutTokenFilter() {
        LOG.info("clear accesstoken in requestfilter");
        tokenRequestFilter.getRequestFilters().forEach(requestFilter ->
            requestFilter.getAccessToken().setAccessToken(null));

        clientOrganizationRepository.deleteAll();
        clientUserRepository.deleteAll();
    }

    @Test
    public void checkJwtDecoder() {
        LOG.info("assert jwtDecoder is not null");
        assertThat(jwtDecoder).isNotNull();
    }

    @WithMockCustomUser( userId = "5d8de63a-0b45-4c33-b9eb-d7fb8d662107", username = "user@sonam.cloud", password = "password", role= "USER")
    @Test
    public void getClientCountForLoggedInUser() throws Exception {
        UUID defaultOrgId = UUID.randomUUID();

        enqueueDefaultOrganizationResponse(defaultOrgId);

        LOG.info("call getClientCount without any clients for a org");

        Mono<Long> longMono = webTestClient
                .get().uri("/clients/count/users")
                .exchange().expectStatus().isOk().returnResult(Long.class).getResponseBody().single();

        StepVerifier.create(longMono).assertNext(aLong -> assertThat(aLong).isEqualTo(0)).verifyComplete();


        RecordedRequest recordedRequest = takeDefaultOrganizationRequest();

        LOG.info("create 3 client organizations");
        clientOrganizationRepository.save(new ClientOrganization(UUID.randomUUID(), defaultOrgId));
        clientOrganizationRepository.save(new ClientOrganization(UUID.randomUUID(), defaultOrgId));
        clientOrganizationRepository.save(new ClientOrganization(UUID.randomUUID(), defaultOrgId));

        enqueueDefaultOrganizationResponse(defaultOrgId);

        LOG.info("call getClientCount without any clients for a org");

        longMono = webTestClient.get().uri("/clients/count/users")
                .exchange().expectStatus().isOk().returnResult(Long.class).getResponseBody().single();

        StepVerifier.create(longMono).assertNext(aLong -> assertThat(aLong).isEqualTo(3)).verifyComplete();


        recordedRequest = takeDefaultOrganizationRequest();


    }

        @WithMockCustomUser( userId = "5d8de63a-0b45-4c33-b9eb-d7fb8d662107", username = "user@sonam.cloud", password = "password", role = "ROLE_USER")
    @Test
    public void create() throws Exception {
        LOG.info("create registration client");

        UUID userId = UUID.fromString("5d8de63a-0b45-4c33-b9eb-d7fb8d662107");
        UUID defaultOrgId = UUID.randomUUID();
        saveClient(clientId.toString(), clientSecret, userId, defaultOrgId, true);

        RegisteredClient registeredClient = issuerAwareAuthorizationServerOperations.findByClientId(currentIssuer(), clientId.toString());
        assertThat(registeredClient).isNotNull();

        assertThat(passwordEncoder.matches("secret", registeredClient.getClientSecret())).isTrue();
        LOG.info("clientAuthMethods: {}", registeredClient.getClientAuthenticationMethods());
        assertThat(registeredClient.getClientAuthenticationMethods().size()).isEqualTo(2);

        Set<ClientAuthenticationMethod> authMethods = Set.of(ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
                ClientAuthenticationMethod.CLIENT_SECRET_JWT);

        assertThat(registeredClient.getClientAuthenticationMethods()).contains(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        assertThat(registeredClient.getClientAuthenticationMethods()).contains(ClientAuthenticationMethod.CLIENT_SECRET_JWT);
        for (AuthorizationGrantType au : registeredClient.getAuthorizationGrantTypes()) {
            LOG.info("au: {}", au.getValue());
        }

        assertThat(registeredClient.getAuthorizationGrantTypes()).contains(AuthorizationGrantType.AUTHORIZATION_CODE);
        assertThat(registeredClient.getAuthorizationGrantTypes()).contains(AuthorizationGrantType.REFRESH_TOKEN);
        assertThat(registeredClient.getAuthorizationGrantTypes()).contains(AuthorizationGrantType.CLIENT_CREDENTIALS);
        registeredClient.getScopes().forEach(s -> LOG.info("scopes: {}", s));

        assertThat(registeredClient.getScopes()).contains("openid");
        assertThat(registeredClient.getScopes()).contains("profile");
        assertThat(registeredClient.getScopes()).contains("message.read");
        assertThat(registeredClient.getScopes()).contains("message.write");
        assertThat(registeredClient.getClientSettings().getSetting("settings.client.require-proof-key").toString()).isEqualTo("false");
        assertThat(registeredClient.getClientSettings().getSetting("settings.client.require-authorization-consent").toString()).isEqualTo("true");

        LOG.info("delete clientId");
        webTestClient.delete().uri("/clients/"+clientId)
                .exchange().expectStatus().isNoContent();

        assertThat(clientUserRepository.existsByClientId(UUID.fromString(registeredClient.getId()))).isFalse();
    }


    @WithMockCustomUser( userId = "5d8de63a-0b45-4c33-b9eb-d7fb8d662107", username = "user@sonam.cloud", password = "password", role = "ROLE_USER")
    @Test
    public void createFailWhenNotSuperAdmin() throws Exception {
        LOG.info("create registration client");

        UUID userId = UUID.fromString("5d8de63a-0b45-4c33-b9eb-d7fb8d662107");
        UUID defaultOrgId = UUID.randomUUID();

        UUID clientId = UUID.randomUUID();
        Map<String, Object> regClientMap = getRegClientMap(clientId.toString(), "{noop}"+clientSecret, userId);
        String authenticationId = "dave";
        Jwt jwt = JwtUtil.jwt(authenticationId);

        //this is needed to let the service endpoint be called and still use the userId from the @WithMockCustomUser( userId = "5d8de63a-0b45-4c33-b9eb-d7fb8d662107"
        when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));

        enqueueDefaultOrganizationResponse(defaultOrgId);

        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setResponseCode(200).setBody(getJson(Map.of("message", false))));

        try {
            Mono<String> stringMono = webTestClient.post().uri("/clients").bodyValue(regClientMap)
                    .exchange().expectStatus().is4xxClientError().returnResult(String.class).getResponseBody().single();
            StepVerifier.create(stringMono).assertNext(s -> {
                LOG.info("got response: {}",s );
            }).verifyComplete();
        }
        catch (Exception e) {
            LOG.info("This exception is thrown because the user is not a superadmin for orgId, error: {}", e.getMessage());
        }

        // take request for mocked response of access token
        RecordedRequest recordedRequest = takeDefaultOrganizationRequest();

        recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("GET");
        assertThat(recordedRequest.getPath()).startsWith("/roles/authzmanagerroles/users/"+userId+"/organizations/"+defaultOrgId);


        RegisteredClient registeredClient = jpaRegisteredClientRepository.findByClientId(clientId.toString());
        assertThat(registeredClient).isNull();
    }

    @WithMockCustomUser( userId = "5d8de63a-0b45-4c33-b9eb-d7fb8d662107", username = "user@sonam.cloud", password = "password", role = "ROLE_USER")
    @Test
    public void createFailMaxCountReached() throws Exception {
        LOG.info("create registration client");

        UUID userId = UUID.fromString("5d8de63a-0b45-4c33-b9eb-d7fb8d662107");
        UUID defaultOrgId = UUID.randomUUID();

        UUID clientId1 = UUID.randomUUID();
        UUID clientId2 = UUID.randomUUID();
        UUID clientId3 = UUID.randomUUID();
        UUID clientId4 = UUID.randomUUID();
        UUID clientId5 = UUID.randomUUID();
        UUID clientId6 = UUID.randomUUID();

        saveClient(clientId1.toString(),"{noop}"+clientSecret, userId, defaultOrgId, true);
        saveClient(clientId2.toString(),"{noop}"+clientSecret, userId, defaultOrgId, true);
        saveClient(clientId3.toString(),"{noop}"+clientSecret, userId, defaultOrgId, true);
        saveClient(clientId4.toString(),"{noop}"+clientSecret, userId, defaultOrgId, true);
        saveClient(clientId5.toString(),"{noop}"+clientSecret, userId, defaultOrgId, true);

        LOG.info("this should fail after creating max clients");
        saveClientNotSuccess(clientId6.toString(), "{noop}" + clientSecret, userId, defaultOrgId, true);

        RegisteredClient registeredClient = jpaRegisteredClientRepository.findByClientId(clientId6.toString());
        assertThat(registeredClient).isNull();
        LOG.info("client: {}", registeredClient);
    }


        @WithMockCustomUser( userId = "5d8de63a-0b45-4c33-b9eb-d7fb8d662107", username = "user@sonam.cloud", password = "password", role = "ROLE_USER")
    @Test
    public void update() throws Exception {
        LOG.info("update registration client by using access_token from this client itself for client credential flow");
        final String messageClientAccessToken = getOauth2Token(messageClient, clientSecret); //get token using messageClient first

        UUID userId = UUID.fromString("5d8de63a-0b45-4c33-b9eb-d7fb8d662107");
        UUID defaultOrgId = UUID.randomUUID();
        saveClient(clientId.toString(), "{noop}"+clientSecret, userId, defaultOrgId, true);

        RegisteredClient registeredClient = getRegisteredClientFromRestService(clientId.toString(), defaultOrgId);
        Map<String, String> rcMap = jpaRegisteredClientRepository.getMap(registeredClient);
        rcMap.put("redirectUris", "http://www.sonam.cloud");
        rcMap.put("userId", userId.toString());
        rcMap.put("mediateToken", "false");


        Map<String, Object> registeredClientMap = updateClient(rcMap);

        RegisteredClient registeredClient1 = jpaRegisteredClientRepository.build(registeredClientMap);

        assertThat(registeredClient1.getRedirectUris()).contains("http://www.sonam.cloud");


        Map<String, Object> map2 = getClientById(registeredClient1.getId(), defaultOrgId);
        RegisteredClient registeredClient2 = jpaRegisteredClientRepository.build(map2);

        assertThat(registeredClient2.getClientName()).isEqualTo(registeredClient1.getClientName());
        assertThat(registeredClient2.getId()).isEqualTo(registeredClient1.getClientId());

        map2 = getClientByClientId(registeredClient1.getClientId(), defaultOrgId);
        registeredClient2 = jpaRegisteredClientRepository.build(map2);

        assertThat(registeredClient2.getClientName()).isEqualTo(registeredClient1.getClientName());
        assertThat(registeredClient2.getId()).isEqualTo(registeredClient1.getClientId());

    }

    /**
     * this will test the "get all client association with user".
     * It will first get clients by organization, and verifies there is none on first attempt
     * Then it will create a client, verify in get client by org that client id is retrieved and so on
     * @throws Exception
     */
    @WithMockCustomUser( userId = "5d8de63a-0b45-4c33-b9eb-d7fb8d662107", username = "user@sonam.cloud", password = "password", role = "ROLE_USER")
    @Test
    public void getAllClientIdAssociatedWithUser() throws Exception {
        LOG.info("get all client ids by user-id");
        UUID userId = UUID.fromString("5d8de63a-0b45-4c33-b9eb-d7fb8d662107");

        UUID defaultOrgId = UUID.randomUUID();

        LOG.info("get clients assoicated to userId");
        CustomRestPage<CustomPair<String, String>> page = getClientIdsAssociatedWithUser(userId, defaultOrgId, true);
        assertThat(page).isNotNull();
        assertThat(page.content()).isEmpty();

        UUID testClient1 = UUID.randomUUID();
        LOG.info("save a client");
        saveClient(testClient1.toString(), "{noop}"+clientSecret, userId, defaultOrgId, true);
        LOG.info("get associated clients to another user");
        page = getClientIdsAssociatedWithUser(userId, defaultOrgId,true);
        assertThat(page.totalElements()).isEqualTo(1);

        assertThat(page.content().getFirst().getFirst()).isNotNull();
        assertThat(page.content().getFirst().getFirst()).isEqualTo(testClient1.toString());
        assertThat(page.content().getFirst().getSecond()).isEqualTo(testClient1.toString());
        assertThat(page.content()).contains(CustomPair.of(testClient1.toString(), testClient1.toString()));

        UUID testClient2= UUID.randomUUID();
        saveClient(testClient2.toString(), "{noop}"+clientSecret, userId, defaultOrgId, true);
        page = getClientIdsAssociatedWithUser(userId, defaultOrgId,true);
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.content()).contains(CustomPair.of(testClient1.toString(), testClient1.toString()));
        assertThat(page.content()).contains(CustomPair.of(testClient2.toString(), testClient2.toString()));

        UUID testClient3 = UUID.randomUUID();
        saveClient(testClient3.toString(), "{noop}"+clientSecret, userId, defaultOrgId, true);
        page = getClientIdsAssociatedWithUser(userId, defaultOrgId,true);
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.content().get(2).getFirst()).isNotNull();
        assertThat(page.content()).contains(CustomPair.of(testClient1.toString(), testClient1.toString()));
        assertThat(page.content()).contains(CustomPair.of(testClient2.toString(), testClient2.toString()));
        assertThat(page.content()).contains(CustomPair.of(testClient3.toString(), testClient3.toString()));

        UUID testClient4 = UUID.randomUUID();
        saveClient(testClient4.toString(), "{noop}"+clientSecret, userId,  defaultOrgId, true);
        page = getClientIdsAssociatedWithUser(userId, defaultOrgId,true);
        assertThat(page.totalElements()).isEqualTo(4);
        assertThat(page.content().get(3).getFirst()).isNotNull();
        assertThat(page.content()).contains(CustomPair.of(testClient1.toString(), testClient1.toString()));
        assertThat(page.content()).contains(CustomPair.of(testClient2.toString(), testClient2.toString()));
        assertThat(page.content()).contains(CustomPair.of(testClient3.toString(), testClient3.toString()));
        assertThat(page.content()).contains(CustomPair.of(testClient4.toString(), testClient4.toString()));

        UUID testClient5 = UUID.randomUUID();
        saveClient(testClient5.toString(), "{noop}"+clientSecret, userId, defaultOrgId, true);
        page = getClientIdsAssociatedWithUser(userId, defaultOrgId,true);
        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.content().get(4).getFirst()).isNotNull();
        assertThat(page.content()).contains(CustomPair.of(testClient1.toString(), testClient1.toString()));
        assertThat(page.content()).contains(CustomPair.of(testClient2.toString(), testClient2.toString()));
        assertThat(page.content()).contains(CustomPair.of(testClient3.toString(), testClient3.toString()));
        assertThat(page.content()).contains(CustomPair.of(testClient4.toString(), testClient4.toString()));
        assertThat(page.content()).contains(CustomPair.of(testClient5.toString(), testClient5.toString()));
    }

    private String getOauth2Token(String clientId, String secret) {
        LOG.info("clientId: {}, secret: {}", clientId, secret);
        final String encodedSecret  = Base64.getEncoder().encodeToString((clientId +":"+secret).getBytes());

        MultiValueMap<String, Object> mvm = new LinkedMultiValueMap<>();
        mvm.add("grant_type", "client_credentials");
        mvm.add("scopes", List.of("message.read", "message.write"));

        EntityExchangeResult<Map<String, String>> entityExchangeResult = webTestClient.post().uri("/oauth2/token")
                .headers(httpHeaders -> httpHeaders.setBasicAuth(encodedSecret))
                .bodyValue(mvm)
                .exchange().expectStatus().isOk().expectBody(new ParameterizedTypeReference<Map<String, String>>() {
                })
                .returnResult();
        Map<String, String> tokenMap = entityExchangeResult.getResponseBody();
        assertThat(tokenMap).isNotNull();
        assertThat(tokenMap.get("access_token")).isNotNull();
        return tokenMap.get("access_token");
    }

    private void saveClient(String clientId, String clientSecret, UUID userId, UUID defaultOrgId, boolean isSuperAdmin) throws  Exception {
        LOG.info("now make a request to create a client");
        Map<String, Object> regClientMap = getRegClientMap(clientId, clientSecret, userId);

        LOG.info("requestBody: {}", regClientMap);
        String authenticationId = "dave";
        Jwt jwt = JwtUtil.jwt(authenticationId);

        assertThat(jwtDecoder).isNotNull();

        //this is needed to let the service endpoint be called and still use the userId from the @WithMockCustomUser( userId = "5d8de63a-0b45-4c33-b9eb-d7fb8d662107"
        when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));

        enqueueDefaultOrganizationResponse(defaultOrgId);

        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setResponseCode(200).setBody(getJson(Map.of("message", isSuperAdmin))));


        Mono<Map> mapMono = webTestClient.post().uri("/clients").bodyValue(regClientMap)
                .exchange().expectStatus().isCreated().returnResult(Map.class).getResponseBody().single();


        LOG.info("saved client");
        StepVerifier.create(mapMono).assertNext(map1 -> {
            LOG.info("map: {}", map1);
            assertThat(map1.get("id")).isNotNull();
            LOG.info("map1.id: {}", map1.get("id"));
        }).verifyComplete();

        LOG.info("test verified complete");


        // take request for mocked response of access token
        RecordedRequest recordedRequest = takeDefaultOrganizationRequest();

        recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("GET");
        assertThat(recordedRequest.getPath()).startsWith("/roles/authzmanagerroles/users/"+userId+"/organizations/"+defaultOrgId);
    }

    private void saveClientNotSuccess(String clientId, String clientSecret, UUID userId, UUID defaultOrgId, boolean isSuperAdmin) throws  Exception {

        try {
            LOG.info("now make a request to create a client");
            Map<String, Object> regClientMap = getRegClientMap(clientId, clientSecret, userId);
            LOG.info("requestBody: {}", regClientMap);
            String authenticationId = "dave";
            Jwt jwt = JwtUtil.jwt(authenticationId);

            //this is needed to let the service endpoint be called and still use the userId from the @WithMockCustomUser( userId = "5d8de63a-0b45-4c33-b9eb-d7fb8d662107"
            when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));

            enqueueDefaultOrganizationResponse(defaultOrgId);

            mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                    .setResponseCode(200).setBody(getJson(Map.of("message", isSuperAdmin))));

            Mono<String> stringMono = webTestClient.post().uri("/clients").bodyValue(regClientMap)
                    .exchange().expectStatus().is4xxClientError().returnResult(String.class).getResponseBody().single();


            StepVerifier.create(stringMono).assertNext(s -> {
                LOG.info("got response: {}", s);
            }).verifyComplete();

        }
        catch (Exception e) {
            LOG.error("Exception occured: {}", e.getMessage());
        }
            // take request for mocked response of access token
            RecordedRequest recordedRequest = takeDefaultOrganizationRequest();

            recordedRequest = mockWebServer.takeRequest();
            assertThat(recordedRequest.getMethod()).isEqualTo("GET");
            assertThat(recordedRequest.getPath()).startsWith("/roles/authzmanagerroles/users/" + userId + "/organizations/" + defaultOrgId);

    }

    /**
     * this test is for testing client delete, part of delete my info
     * @throws Exception
     */
    //WithMockCustomUser annotation will pass a token to the security.  No need to add to the http header as bearer authorization
    @WithMockCustomUser( userId = "5d8de63a-0b45-4c33-b9eb-d7fb8d662107", username = "user@sonam.cloud", password = "password", role = "ROLE_USER")
    @Test
    public void deleteClient() throws Exception {
        LOG.info("create registration client");
        //final String accessToken  = getOauth2Token(messageClient, "secret");
        final String accessToken = "eyJraWQiOiJiYThjMDY1Mi1mNDY1LTRjMjgtYTBhNC00ZjkwZjZiMDgwYWUiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJzb25hbSIsImF1ZCI6ImI0ZGZlM2ZiLTE2OTItNDRiOC05MmFiLTM2NmNjYzg0YjUzOS1hdXRoem1hbmFnZXIiLCJuYmYiOjE3MTg4MjMyOTgsInNjb3BlIjpbIm9wZW5pZCIsInByb2ZpbGUiXSwiaXNzIjoiaHR0cDovL2FwaS1nYXRld2F5OjkwMDEvaXNzdWVyIiwiZXhwIjoxNzE4ODIzNTk4LCJ1c2VyUm9sZSI6WyJVU0VSX1JPTEUiXSwiaWF0IjoxNzE4ODIzMjk4LCJ1c2VySWQiOiIxZjQ0MmRhYi05NmEzLTQ1OWUtODYwNS03ZjVjZDVmODJlMjUiLCJqdGkiOiJkOTA2MmE2MC01ODQ2LTRiM2YtYmYwOC03ZGZmNDY5ZmIxOTMifQ.fwKo-SWQnFRpyAVuLxTjjkAqMqNMXBy7NNr-SIbuaXYzOrpzdhY0PFKrG2sRbbvSWxoIIjPFaVeFskh-I_sON8uvTw3MPld5W3gf7RcT_ZG49UlGt4E1R_BzhxiYpkm2QCZqZl1CtgQ_lqgN0roTWuXGMCPFuwATIyIhfkAHnyvWBcUlGRavDfGEBx61MEWJZ3ZnK0Mr08_LH4dXqms2QoDEIQzDbpNLUFCpV99mTEKyOMfKh5wrSgex7fdwdDcdhq1wx98nlbrk9gmLRMruYaPx8Vun0xFjudZzIDwvqA9iQRPjQmJdO-8V9xFY5mvS04zrRbRCIDR_g09hwkkRTw";

        LOG.info("oauth2Token: {}", accessToken);

        UUID userId = UUID.fromString("5d8de63a-0b45-4c33-b9eb-d7fb8d662107"); //this should match the value from WithMockCustomerUser.userId value
        UUID defaultOrgId = UUID.randomUUID();
        saveClient(clientId.toString(),"{noop}"+clientSecret, userId, defaultOrgId, true);

        assertThat(issuerAwareAuthorizationServerOperations.findByClientId(currentIssuer(), clientId.toString())).isNotNull();
        assertThat(clientOrganizationRepository.findByClientId(clientId)).isPresent();

        enqueueDefaultOrganizationResponse(defaultOrgId);

        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setResponseCode(200).setBody(getJson(Map.of("message", true))));


        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setResponseCode(200).setBody(getJson(Map.of("message", 0L))));

        LOG.info("call client delete");
        EntityExchangeResult<Map<String, Object>> entityExchangeResult = webTestClient.delete()
                .uri("/clients")
                .accept(MediaType.APPLICATION_JSON).exchange().expectBody(new ParameterizedTypeReference<Map<String, Object>>() {}).returnResult();

        LOG.info("response: {}", entityExchangeResult.getResponseBody());
        RecordedRequest recordedRequest = takeDefaultOrganizationRequest();

        recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("GET");
        assertThat(recordedRequest.getPath()).startsWith("/roles/authzmanagerroles/users/" + userId + "/organizations/" + defaultOrgId);

        recordedRequest = mockWebServer.takeRequest();

        assertThat(recordedRequest.getMethod()).isEqualTo("GET");
        assertThat(recordedRequest.getPath()).startsWith("/roles/organizations/"+defaultOrgId+"/count");

        assertThat(issuerAwareAuthorizationServerOperations.findByClientId(currentIssuer(), clientId.toString())).isNull();
        assertThat(clientOrganizationRepository.findByClientId(clientId)).isEmpty();

        LOG.info("done test");
    }

    /**
     * Delete my info from clients when there are clients with organization-user-roles for any client
     * @throws Exception
     */
    //WithMockCustomUser annotation will pass a token to the security.  No need to add to the http header as bearer authorization
    @WithMockCustomUser( userId = "5d8de63a-0b45-4c33-b9eb-d7fb8d662107", username = "user@sonam.cloud", password = "password", role = "ROLE_USER")
    @Test
    public void deleteClientWithClientOrgRoles() throws Exception {
        LOG.info("create registration client");
        final String accessToken = "eyJraWQiOiJiYThjMDY1Mi1mNDY1LTRjMjgtYTBhNC00ZjkwZjZiMDgwYWUiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJzb25hbSIsImF1ZCI6ImI0ZGZlM2ZiLTE2OTItNDRiOC05MmFiLTM2NmNjYzg0YjUzOS1hdXRoem1hbmFnZXIiLCJuYmYiOjE3MTg4MjMyOTgsInNjb3BlIjpbIm9wZW5pZCIsInByb2ZpbGUiXSwiaXNzIjoiaHR0cDovL2FwaS1nYXRld2F5OjkwMDEvaXNzdWVyIiwiZXhwIjoxNzE4ODIzNTk4LCJ1c2VyUm9sZSI6WyJVU0VSX1JPTEUiXSwiaWF0IjoxNzE4ODIzMjk4LCJ1c2VySWQiOiIxZjQ0MmRhYi05NmEzLTQ1OWUtODYwNS03ZjVjZDVmODJlMjUiLCJqdGkiOiJkOTA2MmE2MC01ODQ2LTRiM2YtYmYwOC03ZGZmNDY5ZmIxOTMifQ.fwKo-SWQnFRpyAVuLxTjjkAqMqNMXBy7NNr-SIbuaXYzOrpzdhY0PFKrG2sRbbvSWxoIIjPFaVeFskh-I_sON8uvTw3MPld5W3gf7RcT_ZG49UlGt4E1R_BzhxiYpkm2QCZqZl1CtgQ_lqgN0roTWuXGMCPFuwATIyIhfkAHnyvWBcUlGRavDfGEBx61MEWJZ3ZnK0Mr08_LH4dXqms2QoDEIQzDbpNLUFCpV99mTEKyOMfKh5wrSgex7fdwdDcdhq1wx98nlbrk9gmLRMruYaPx8Vun0xFjudZzIDwvqA9iQRPjQmJdO-8V9xFY5mvS04zrRbRCIDR_g09hwkkRTw";

        UUID userId = UUID.fromString("5d8de63a-0b45-4c33-b9eb-d7fb8d662107"); //this should match the value from WithMockCustomerUser.userId value
        UUID defaultOrgId = UUID.randomUUID();
        saveClient(clientId.toString(),"{noop}"+clientSecret, userId, defaultOrgId, true);

        assertThat(issuerAwareAuthorizationServerOperations.findByClientId(currentIssuer(), clientId.toString())).isNotNull();
        assertThat(clientOrganizationRepository.findByClientId(clientId)).isPresent();

        LOG.info("call client delete");
        enqueueDefaultOrganizationResponse(defaultOrgId);

        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setResponseCode(200).setBody(getJson(Map.of("message", true))));

        // return 1 (row count) when getting count of client-organization-user roles by orgId
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setResponseCode(200).setBody(getJson(Map.of("message", 1L))));

        EntityExchangeResult<Map<String, Object>> entityExchangeResult = webTestClient.delete()
                .uri("/clients")
                .accept(MediaType.APPLICATION_JSON).exchange().expectBody(new ParameterizedTypeReference<Map<String, Object>>() {}).returnResult();

        LOG.info("response: {}", entityExchangeResult.getResponseBody());
        RecordedRequest recordedRequest = takeDefaultOrganizationRequest();

        recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("GET");
        assertThat(recordedRequest.getPath()).startsWith("/roles/authzmanagerroles/users/" + userId + "/organizations/" + defaultOrgId);

        recordedRequest = mockWebServer.takeRequest();

        assertThat(recordedRequest.getMethod()).isEqualTo("GET");
        assertThat(recordedRequest.getPath()).startsWith("/roles/organizations/"+defaultOrgId+"/count");

        //the client(s) should not be deleted if we get a count of 1 for roles in client-org-user-roles

        assertThat(issuerAwareAuthorizationServerOperations.findByClientId(currentIssuer(), clientId.toString())).isNotNull();
        assertThat(clientOrganizationRepository.findByClientId(clientId)).isNotEmpty();

        assertThat(clientOrganizationRepository.findByClientId(clientId)).isNotNull();
        assertThat(clientOrganizationRepository.findByClientId(clientId).get().getOrganizationId()).isEqualTo(defaultOrgId);
        assertThat(clientOrganizationRepository.findByClientId(clientId).get().getClientId()).isEqualTo(clientId);

    }

    private RegisteredClient getRegisteredClientFromRestService(String clientId, UUID defaultOrgId) throws Exception {

        enqueueDefaultOrganizationResponse(defaultOrgId);

        EntityExchangeResult<Map<String, Object>> entityExchangeResult = webTestClient.get()
                .uri("/clients/client-id/"+clientId)
                .accept(MediaType.APPLICATION_JSON).exchange().expectBody(new ParameterizedTypeReference<Map<String, Object>>() {}).returnResult();

        assertThat(entityExchangeResult).isNotNull();
        assertThat(entityExchangeResult.getResponseBody()).isNotNull();

        RecordedRequest recordedRequest = takeDefaultOrganizationRequest();

        Map<String, Object> map = entityExchangeResult.getResponseBody();
        RegisteredClient registeredClient = jpaRegisteredClientRepository.build(map);
        assertThat(registeredClient).isNotNull();
        return registeredClient;
    }

    private Map<String, Object> updateClient(Map<String, String> map) {

        EntityExchangeResult<Map<String, Object>> entityExchangeResult = webTestClient.put().uri("/clients")
                .bodyValue(map)
                .exchange().expectStatus().isOk().expectBody(new ParameterizedTypeReference<Map<String, Object>>(){}).returnResult();

        assertThat(entityExchangeResult).isNotNull();
        assertThat(entityExchangeResult.getResponseBody()).isNotNull();

        return entityExchangeResult.getResponseBody();
    }

    private Map<String, Object> getClientById(String id, UUID defaultOrgId) throws Exception {
        enqueueDefaultOrganizationResponse(defaultOrgId);

        EntityExchangeResult<Map<String, Object>> entityExchangeResult = webTestClient.get().uri("/clients/"+id)
                .exchange().expectStatus().isOk().expectBody(new ParameterizedTypeReference<Map<String, Object>>(){}).returnResult();

        assertThat(entityExchangeResult).isNotNull();
        assertThat(entityExchangeResult.getResponseBody()).isNotNull();

        RecordedRequest recordedRequest = takeDefaultOrganizationRequest();

        return entityExchangeResult.getResponseBody();
    }

    private Map<String, Object> getClientByClientId(String clientId, UUID defaultOrgId) throws Exception {
        enqueueDefaultOrganizationResponse(defaultOrgId);
        EntityExchangeResult<Map<String, Object>> entityExchangeResult = webTestClient.get().uri("/clients/client-id/"+clientId)
                .exchange().expectStatus().isOk().expectBody(new ParameterizedTypeReference<Map<String, Object>>(){}).returnResult();

        assertThat(entityExchangeResult).isNotNull();
        assertThat(entityExchangeResult.getResponseBody()).isNotNull();
        RecordedRequest recordedRequest = takeDefaultOrganizationRequest();

        return entityExchangeResult.getResponseBody();
    }

    private CustomRestPage<CustomPair<String, String>> getClientIdsAssociatedWithUser(UUID userId, UUID defaultOrgId, boolean accessTokenPassed) throws Exception {
        //get defaultOrganization response
        enqueueDefaultOrganizationResponse(defaultOrgId);

        //isSuperAdminInOrgId response
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setResponseCode(200).setBody(getJson(Map.of("message", true))));

        EntityExchangeResult<CustomRestPage<CustomPair<String, String>>> entityExchangeResult = webTestClient.get()
                .uri("/clients/organizations")
                .exchange().expectStatus().isOk().expectBody(new ParameterizedTypeReference<
                        CustomRestPage<CustomPair<String, String>>>(){}).returnResult();

        LOG.info("entityExchange result: {}", entityExchangeResult.getResponseBody());

        RecordedRequest recordedRequest = takeDefaultOrganizationRequest();

        recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("GET");
        assertThat(recordedRequest.getPath()).startsWith("/roles/authzmanagerroles/users/"+userId+"/organizations/"+defaultOrgId);

        assertThat(entityExchangeResult).isNotNull();
        assertThat(entityExchangeResult.getResponseBody()).isNotNull();

        return entityExchangeResult.getResponseBody();
    }

    private void enqueueDefaultOrganizationResponse(UUID defaultOrgId) throws IOException {
        if (tokenRequestFilter.getRequestFilters().stream()
                .anyMatch(requestFilter -> requestFilter.getAccessToken() != null
                        && requestFilter.getAccessToken().getAccessToken() == null)) {
            mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                    .setResponseCode(200).setBody(refreshTokenResource.getContentAsString(StandardCharsets.UTF_8)));
        }
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setResponseCode(200).setBody(getJson(Map.of("message", defaultOrgId))));
    }

    private RecordedRequest takeDefaultOrganizationRequest() throws InterruptedException {
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        if ("POST".equals(recordedRequest.getMethod()) && recordedRequest.getPath().startsWith("/oauth2/token")) {
            recordedRequest = mockWebServer.takeRequest();
        }
        assertThat(recordedRequest.getMethod()).isEqualTo("GET");
        assertThat(recordedRequest.getPath()).startsWith("/organizations/users");
        return recordedRequest;
    }


    private static String getJson(Object object) {
        ObjectMapper objectMapper = JsonMapper.builder().build();
        String json = objectMapper.writeValueAsString(object);
        LOG.info("json for object: {}", json);
        return json;
    }

    private Map<String, Object> getRegClientMap(String clientId, String clientSecret, UUID userId) {
        RegisteredClient registeredClient = RegisteredClient.withId(clientId)
                .clientId(clientId)
                .clientSecret(clientSecret)
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

        Map<String, Object> regClientMap = jpaRegisteredClientRepository.getMapObject(registeredClient);
        regClientMap.put("userId", userId);

        return regClientMap;
    }

    private String currentIssuer() {
        return "http://localhost:" + port;
    }

}
