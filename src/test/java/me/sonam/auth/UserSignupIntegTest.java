package me.sonam.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.sonam.auth.config.SignupPolicyProperties;
import me.sonam.auth.mocks.WithMockCustomUser;
import me.sonam.auth.rest.signup.Organization;
import me.sonam.auth.rest.signup.User;
import me.sonam.auth.rest.signup.UserSignup;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import me.sonam.auth.util.TokenRequestFilter;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Only test the user signup form.  The Admin signup is from the AuthzManager so leave it in authzmanager project.
 */
@AutoConfigureMockMvc
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {DefaultAuthorizationServerApplication.class})
@AutoConfigureWebTestClient
public class UserSignupIntegTest {
    private static final Logger LOG = LoggerFactory.getLogger(UserSignupIntegTest.class);

    private String userId = UUID.randomUUID().toString();

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokenRequestFilter tokenRequestFilter;
    @Autowired
    private SignupPolicyProperties signupPolicyProperties;
    private static MockWebServer mockWebServer;

    private RegisteredClientUtil registeredClientUtil = new RegisteredClientUtil();
    private final String token = "{\"access_token\": \"eyJraWQiOiJlOGQ3MjIzMC1iMDgwLTRhZjEtODFkOC0zMzE3NmNhMTM5ODIiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiI3NzI1ZjZmZC1kMzk2LTQwYWYtOTg4Ni1jYTg4YzZlOGZjZDgiLCJhdWQiOiI3NzI1ZjZmZC1kMzk2LTQwYWYtOTg4Ni1jYTg4YzZlOGZjZDgiLCJuYmYiOjE3MTQ3NTY2ODIsImlzcyI6Imh0dHA6Ly9teS1zZXJ2ZXI6OTAwMSIsImV4cCI6MTcxNDc1Njk4MiwiaWF0IjoxNzE0NzU2NjgyLCJqdGkiOiI0NDBlZDY0My00MzdkLTRjOTMtYTZkMi1jNzYxNjFlNDRlZjUifQ.fjqgoczZbbmcnvYpVN4yakpbplp7EkDyxslvar5nXBFa6mgIFcZa29fwIKfcie3oUMQ8MDWxayak5PZ_QIuHwTvKSWHs0WL91ljf-GT1sPi1b4gDKf0rJOwi0ClcoTCRIx9-WGR6t2BBR1Rk6RGF2MW7xKw8M-RMac2A2mPEPJqoh4Pky1KgxhZpEXixegpAdQIvBgc0KBZeQme-ZzTYugB8EPUmGpMlfd-zX_vcR1ijxi8e-LRRJMqmGkc9GXfrH7MOKNQ_nu6pc6Gish2v_iuUEcpPHXrfqzGb9IHCLvfuLSaTDcYKYjQaEUAp-1uDW8-5posjiUV2eBiU48ajYg\", \"token_type\":\"Bearer\", \"expires_in\":\"299\"}";

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
        r.add("auth-server.root", () -> "http://localhost:" + mockWebServer.getPort());
        r.add("oauth2-token-mediator.root", () -> "http://localhost:" + mockWebServer.getPort());
        r.add("organization-rest-service.root", () -> "http://localhost:" + mockWebServer.getPort());
        r.add("role-rest-service.root", () -> "http://localhost:" + mockWebServer.getPort());
        r.add("user-rest-service.root", () -> "http://localhost:" + mockWebServer.getPort());
        r.add("setting-rest-service.root", () -> "http://localhost:" + mockWebServer.getPort());
    }

    @BeforeEach
    void clearAccessTokens() {
        for (TokenRequestFilter.RequestFilter requestFilter : tokenRequestFilter.getRequestFilters()) {
            if (requestFilter.getAccessToken() != null) {
                requestFilter.getAccessToken().setAccessToken(null);
            }
        }

        signupPolicyProperties.getHosts().clear();

        SignupPolicyProperties.HostPolicy freeHostPolicy = new SignupPolicyProperties.HostPolicy();
        freeHostPolicy.setAllowSignup(true);
        freeHostPolicy.setCreateOrganizationOnSignup(true);
        freeHostPolicy.getAllowedEmailDomains().add("*");
        signupPolicyProperties.getHosts().put("free.openissuer.test", freeHostPolicy);

        SignupPolicyProperties.HostPolicy business1Policy = new SignupPolicyProperties.HostPolicy();
        business1Policy.setAllowSignup(true);
        business1Policy.setCreateOrganizationOnSignup(false);
        business1Policy.getAllowedEmailDomains().add("business1.com");
        signupPolicyProperties.getHosts().put("business1.openissuer.test", business1Policy);
    }

    @Test
    public void userSignup() throws Exception {
        UserSignup userSignup = new UserSignup("Sonam", "Wangyal", "mugambo@1234sonam.com",
                "mugambo", "hello".toCharArray(), false, "my lucky company");

        User user = enqueueTokenAndUserResponses(userSignup);

        Organization org = new Organization(UUID.randomUUID(), userSignup.getOrganization(), user.getId());
        mockWebServer.enqueue(jsonResponse(200, getJson(org)));
        mockWebServer.enqueue(jsonResponse(200, getJson(Map.of("message", "organization added to subdomain"))));
        mockWebServer.enqueue(jsonResponse(200, getJson(Map.of("message", "default organization updated"))));
        mockWebServer.enqueue(jsonResponse(201, getJson(
                        Map.of("id", UUID.randomUUID(), "authzManagerRoleId", UUID.randomUUID(),
                                "userId", user.getId(), "organizationId", org.getId()))));

        String responseBody = signupWithHost("free.openissuer.test", userSignup);

        assertThat(responseBody).contains("your signup was successful");
        assertRequest("POST", "/oauth2/token");
        assertRequest("POST", "/users");
        assertRequest("GET", "/users/authentication-id/" + userSignup.getAuthenticationId());
        assertRequest("POST", "/organizations");
        assertRequest("POST", "/organizations/subdomain/free.openissuer.test/organizations/" + org.getId());
        assertRequest("PUT", "/organizations/" + org.getId() + "/users/" + user.getId() + "/default");
        assertRequest("POST", "/roles/authzmanagerroles/names/users/organizations");
    }

    @Test
    public void publicHostSignupCreatesOrganization() throws Exception {
        UserSignup userSignup = new UserSignup("Public", "User", "owner@any-domain.com",
                "public-owner", "hello".toCharArray(), false, "Public Org");

        User user = enqueueTokenAndUserResponses(userSignup);

        Organization org = new Organization(UUID.randomUUID(), userSignup.getOrganization(), user.getId());
        mockWebServer.enqueue(jsonResponse(200, getJson(org)));
        mockWebServer.enqueue(jsonResponse(200, getJson(Map.of("message", "organization added to subdomain"))));
        mockWebServer.enqueue(jsonResponse(200, getJson(Map.of("message", "default organization updated"))));
        mockWebServer.enqueue(jsonResponse(201, getJson(
                Map.of("id", UUID.randomUUID(), "authzManagerRoleId", UUID.randomUUID(),
                        "userId", user.getId(), "organizationId", org.getId()))));

        String responseBody = signupWithHost("free.openissuer.test", userSignup);

        assertThat(responseBody).contains("your signup was successful");

        assertRequest("POST", "/oauth2/token");
        assertRequest("POST", "/users");
        assertRequest("GET", "/users/authentication-id/" + userSignup.getAuthenticationId());
        assertRequest("POST", "/organizations");
        assertRequest("POST", "/organizations/subdomain/free.openissuer.test/organizations/" + org.getId());
        assertRequest("PUT", "/organizations/" + org.getId() + "/users/" + user.getId() + "/default");
        assertRequest("POST", "/roles/authzmanagerroles/names/users/organizations");
    }

    @Test
    public void publicHostSignupRejectsEmailDomainReservedForTenantHost() throws Exception {
        UserSignup userSignup = new UserSignup("Public", "User", "owner@business1.com",
                "public-business1-owner", "hello".toCharArray(), false, "Public Org");

        String responseBody = signupWithHost("free.openissuer.test", userSignup);

        assertThat(responseBody).contains("email domain is reserved for another subdomain");
        RecordedRequest recordedRequest = mockWebServer.takeRequest(200, TimeUnit.MILLISECONDS);
        Assertions.assertThat(recordedRequest).isNull();
    }

    @Test
    public void defaultHostSignupRejectsBeforeCreatingUser() throws Exception {
        UserSignup userSignup = new UserSignup("Local", "User", "local@any-domain.com",
                "local-owner", "hello".toCharArray(), false, "Local Org");

        String responseBody = signupWithHost("localhost", userSignup);

        assertThat(responseBody).contains("signup must be performed from a tenant subdomain");
        RecordedRequest recordedRequest = mockWebServer.takeRequest(200, TimeUnit.MILLISECONDS);
        Assertions.assertThat(recordedRequest).isNull();
    }

    @Test
    public void hostBoundSignupAttachesUserToExistingOrganization() throws Exception {
        UserSignup userSignup = new UserSignup("Bound", "User", "owner@business1.com",
                "bound-owner", "hello".toCharArray(), false, "Should Not Be Created");
        UUID boundOrganizationId = UUID.randomUUID();

        User user = enqueueTokenAndUserResponses(userSignup);
        mockWebServer.enqueue(jsonResponse(200, getJson(Map.of("id", boundOrganizationId))));
        mockWebServer.enqueue(jsonResponse(200, getJson(Map.of("message", "added user to organization"))));
        mockWebServer.enqueue(jsonResponse(200, getJson(Map.of("message", "default organization updated"))));

        String responseBody = signupWithHost("business1.openissuer.test", userSignup);

        assertThat(responseBody).contains("your signup was successful");

        assertRequest("POST", "/oauth2/token");
        assertRequest("POST", "/users");
        assertRequest("GET", "/users/authentication-id/" + userSignup.getAuthenticationId());
        assertRequest("GET", "/organizations/subdomain/business1.openissuer.test");
        assertRequest("POST", "/organizations/users");
        assertRequest("PUT", "/organizations/" + boundOrganizationId + "/users/" + user.getId() + "/default");

        RecordedRequest recordedRequest = mockWebServer.takeRequest(200, TimeUnit.MILLISECONDS);
        Assertions.assertThat(recordedRequest).isNull();
    }

    @Test
    public void hostBoundSignupRejectsEmailOutsideAllowedDomain() throws Exception {
        UserSignup userSignup = new UserSignup("Wrong", "Domain", "owner@other.com",
                "wrong-domain", "hello".toCharArray(), false, "Ignored Org");

        String responseBody = signupWithHost("business1.openissuer.test", userSignup);

        assertThat(responseBody).contains("email domain is not allowed for this subdomain");
        RecordedRequest recordedRequest = mockWebServer.takeRequest(200, TimeUnit.MILLISECONDS);
        Assertions.assertThat(recordedRequest).isNull();
    }

    private User enqueueTokenAndUserResponses(UserSignup userSignup) throws JsonProcessingException {
        mockWebServer.enqueue(jsonResponse(200, token));
        mockWebServer.enqueue(jsonResponse(200, "{\"message\": \"user created\"}"));

        UUID currentUserId = UUID.randomUUID();
        User user = new User();
        user.setId(currentUserId);
        user.setAuthenticationId(userSignup.getAuthenticationId());
        user.setFirstName(userSignup.getFirstName());
        user.setLastName(userSignup.getLastName());

        mockWebServer.enqueue(jsonResponse(200, getJson(user)));
        return user;
    }

    private EntityExchangeResult<String> signup(String host, UserSignup userSignup) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("firstName", userSignup.getFirstName());
        formData.add("lastName", userSignup.getLastName());
        formData.add("email", userSignup.getEmail());
        formData.add("authenticationId", userSignup.getAuthenticationId());
        formData.add("password", "1234567890");
        formData.add("active", "false");
        if (userSignup.getOrganization() != null) {
            formData.add("organization", userSignup.getOrganization());
        }

        return webTestClient.post().uri("/signup")
                .header("Host", host)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(formData)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();
    }

    private String signupWithHost(String host, UserSignup userSignup) throws Exception {
        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/signup")
                        .with(request -> {
                            request.setServerName(host);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("firstName", userSignup.getFirstName())
                        .param("lastName", userSignup.getLastName())
                        .param("email", userSignup.getEmail())
                        .param("authenticationId", userSignup.getAuthenticationId())
                        .param("password", "1234567890")
                        .param("active", "false")
                        .param("organization", userSignup.getOrganization() == null ? "" : userSignup.getOrganization()))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult asyncResult = mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andReturn();
        return asyncResult.getResponse().getContentAsString();
    }

    private MockResponse jsonResponse(int status, String body) {
        return new MockResponse().setHeader("Content-Type", MediaType.APPLICATION_JSON)
                .setResponseCode(status)
                .setBody(body);
    }

    private void assertRequest(String method, String pathPrefix) throws InterruptedException {
        RecordedRequest recordedRequest = mockWebServer.takeRequest(2, TimeUnit.SECONDS);
        Assertions.assertThat(recordedRequest).isNotNull();
        Assertions.assertThat(recordedRequest.getMethod()).isEqualTo(method);
        Assertions.assertThat(recordedRequest.getPath()).startsWith(pathPrefix);
    }



    public String getJson(Object o) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(o);
    }
}
