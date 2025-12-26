package me.sonam.auth;

import me.sonam.auth.jpa.entity.ClientOrganization;
import me.sonam.auth.webtestclient.ClientCredentialWebClient;
import me.sonam.auth.webtestclient.ClientOrganizationWebTestClient;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@EnableAutoConfiguration
@AutoConfigureMockMvc
@SpringBootTest(classes = {DefaultAuthorizationServerApplication.class}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
@AutoConfigureWebTestClient
public class ClientOrganizationRestIntegTest {
    private static final Logger LOG = LoggerFactory.getLogger(ClientOrganizationRestIntegTest.class);

    private ClientOrganizationWebTestClient clientOrganizationWebTestClient;

    private ClientCredentialWebClient clientCredentialWebClient;

    @Autowired
    private WebTestClient webTestClient;

    private String clientClientId = "messaging-client";
    private String secret = "secret";
    private static MockWebServer mockWebServer;

    @BeforeEach
    public  void init() {
        this.clientOrganizationWebTestClient = new ClientOrganizationWebTestClient(webTestClient);
        this.clientCredentialWebClient = new ClientCredentialWebClient(webTestClient);
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
    }

    @Test
    public void associateClientWithOrganization() {
        LOG.info("associate client with organization");

        UUID clientId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();

        String accessToken = clientCredentialWebClient.getAccessToken(clientClientId, secret);
        assertThat(accessToken).isNotNull();

        final String stringResponse = saveClientOrganization(accessToken, clientId, organizationId);
        assertThat(stringResponse).isEqualTo("clientOrganization saved");
    }

    private String saveClientOrganization(String accessToken, UUID clientId, UUID organizationId) {
        ClientOrganization clientOrganization = new ClientOrganization(clientId, organizationId);
        return clientOrganizationWebTestClient.associateClientWithOrganization(accessToken, clientOrganization, HttpStatus.CREATED);
    }

    @Test
    public void findClientIdAndOrganizationId() {
        UUID clientId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();

        String accessToken = clientCredentialWebClient.getAccessToken(clientClientId, secret);
        assertThat(accessToken).isNotNull();

        final String stringResponse = saveClientOrganization(accessToken, clientId, organizationId);
        assertThat(stringResponse).isEqualTo("clientOrganization saved");

        ClientOrganization clientOrganization = clientOrganizationWebTestClient.findRow(accessToken, clientId, organizationId, HttpStatus.OK);
        assertThat(clientOrganization.getOrganizationId()).isEqualTo(organizationId);
        assertThat(clientOrganization.getClientId()).isEqualTo(clientId);
    }

    @Test
    public void deleteClientOrganization() {
        UUID clientId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();

        String accessToken = clientCredentialWebClient.getAccessToken(clientClientId, secret);
        assertThat(accessToken).isNotNull();

        final String stringResponse = saveClientOrganization(accessToken, clientId, organizationId);
        assertThat(stringResponse).isEqualTo("clientOrganization saved");

        String response = clientOrganizationWebTestClient.delete(accessToken, clientId, organizationId, HttpStatus.OK);
        assertThat(response).isEqualTo("deleted clientId OrganizationId row");

        LOG.debug("assert that there is no row with that clientId and organizationId after deletion");
        String string = clientOrganizationWebTestClient.findEmptyRow(accessToken, clientId, organizationId, HttpStatus.OK);
        LOG.info("string {}", string);
        assertThat(string).isNull();
    }

    @Test
    public void getOrganizationIdForClientId() {
        UUID clientId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();

        String accessToken = clientCredentialWebClient.getAccessToken(clientClientId, secret);
        assertThat(accessToken).isNotNull();

        final String stringResponse = saveClientOrganization(accessToken, clientId, organizationId);
        assertThat(stringResponse).isEqualTo("clientOrganization saved");

        UUID orgId = clientOrganizationWebTestClient.getOrganizationIdForClientId(accessToken, clientId, HttpStatus.OK);
        assertThat(orgId).isEqualTo(organizationId);
    }
}
