package me.sonam.auth;

import me.sonam.auth.util.TokenRequestFilter;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.Charset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * This will test the forgotUsernameController.
 */
@EnableAutoConfiguration
@ExtendWith(SpringExtension.class)
@SpringBootTest( webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class ForgotUsernamePasswordIntegTest {
    private static final Logger LOG = LoggerFactory.getLogger(ForgotUsernamePasswordIntegTest.class);

    @Autowired
    private MockMvc mockMvc;
    private static MockWebServer mockWebServer;
    @Autowired
    private TokenRequestFilter tokenRequestFilter;

    final String clientCredentialResponse = "{" +
            "    \"access_token\": \"eyJraWQiOiJhNzZhN2I0My00YTAzLTQ2MzAtYjVlMi0wMTUzMGRlYzk0MGUiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJwcml2YXRlLWNsaWVudCIsImF1ZCI6InByaXZhdGUtY2xpZW50IiwibmJmIjoxNjg3MTA0NjY1LCJzY29wZSI6WyJtZXNzYWdlLnJlYWQiLCJtZXNzYWdlLndyaXRlIl0sImlzcyI6Imh0dHA6Ly9sb2NhbGhvc3Q6OTAwMSIsImV4cCI6MTY4NzEwNDk2NSwiaWF0IjoxNjg3MTA0NjY1LCJhdXRob3JpdGllcyI6WyJtZXNzYWdlLnJlYWQiLCJtZXNzYWdlLndyaXRlIl19.Wx03Q96TR17gL-BCsG6jPxpdt3P-UkcFAuE6pYmZLl5o9v1ag9XR7MX71pfJcIhjmoog8DUTJXrq-ZB-IxIbMhIGmIHIw57FfnbBzbA8mjyBYQOLFOh9imLygtO4r9uip3UR0Ut_YfKMMi-vPfeKzVDgvaj6N08YNp3HNoAnRYrEJLZLPp1CUQSqIHEsGXn2Sny6fYOmR3aX-LcSz9MQuyDDr5AQcC0fbcpJva6aSPvlvliYABxfldDfpnC-i90F6azoxJn7pu3wTC7sjtvS0mt0fQ2NTDYXFTtHm4Bsn5MjZbOruih39XNsLUnp4EHpAh6Bb9OKk3LSBE6ZLXaaqQ\"," +
            "    \"scope\": \"message.read message.write\"," +
            "    \"token_type\": \"Bearer\"," +
            "    \"expires_in\": 299" +
            "}";

    @BeforeEach
    public void cleanOutAccessTokens() {
        for(TokenRequestFilter.RequestFilter tokenRequestFilter: tokenRequestFilter.getRequestFilters()) {
            tokenRequestFilter.getAccessToken().setAccessToken(null);
        }
    }


    @BeforeAll
    static void setupMockWebServer() throws IOException {
        LOG.info("starting mock web server");
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
        LOG.info("mock the port for account-rest-service");
        r.add("account-rest-service.root", () -> "http://localhost:"+mockWebServer.getPort());
        r.add("auth-server.root", () -> "http://localhost:"+ mockWebServer.getPort());
    }

    @Test
    public void forgotUsername() throws Exception {
        LOG.info("call forgotUsername endpoint");

        LOG.info("assert that the page returned is Email username help");
        this.mockMvc.perform(get("/username")).andDo(print()).andExpect(status().isOk())
                .andExpect(content().string(containsString("Email username help")));
    }

    @Test
    public void forgotPassword() throws Exception {
        LOG.info("call forgotPassword endpoint");

        LOG.info("assert that the page returned is Change password help");
        this.mockMvc.perform(get("/password")).andDo(print()).andExpect(status().isOk());
    }

    @Test
    public void emailUsername() throws Exception {
        LOG.info("email username");

        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(200).setBody(clientCredentialResponse));

        LOG.info("add mock response for email username call into queue");
        final String emailMsg = " {\"message\":\"email successfully sent\"}";
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setResponseCode(201).setBody(emailMsg));//"Account created successfully.  Check email for activating account"));

        final String email = "dummy@xyqkl.com";
        final String urlEncodedEmail = URLEncoder.encode(email, Charset.defaultCharset());
        LOG.info("urlEncodedEmail: {}", urlEncodedEmail);

        this.mockMvc.perform(post("/username")
                        .param("emailAddress", email))
                .andDo(print()).andExpect(status().isOk());
               // .andExpect(content().string(containsString("Your username has been sent to your email address.")));

        LOG.info("serve the queued mock response for email username http callout");
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/issuer/oauth2/token");

        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("PUT");
        //looks like the urlEncoded is getting urlEncoded again in the account put call so double it
        assertThat(request.getPath()).startsWith("/accounts/email/authentication-id");
    }

    @Test
    public void emailMySecretForPasswordChange() throws Exception {
        LOG.info("email username");

        LOG.info("add mock response for email username call into queue");

        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(200).setBody(clientCredentialResponse));

        final String emailMsg = " {\"message\":\"email successfully sent\"}";
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setResponseCode(201).setBody(emailMsg));

        this.mockMvc.perform(post("/password")
                        .param("email", "sonam@sonam.com"))

                .andDo(print()).andExpect(status().isOk());
                //.andExpect(content().string(containsString("Check your email for changing your password.")));

        LOG.info("serve the queued mock response for email username http callout");
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/issuer/oauth2/token");

        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("PUT");
        assertThat(request.getPath()).startsWith("/accounts/email/");
    }

    @Test
    public void emailMySecretForPasswordChangeThrowException() throws Exception {
        LOG.info("email username");

        LOG.info("add mock response for email username call into queue");
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(200).setBody(clientCredentialResponse));

        final String emailMsg = " {\"error\":\"Account is not active or does not exist\"}";
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(400).setBody(emailMsg));//"Account created successfully.  Check email for activating account"));

        this.mockMvc.perform(post("/password")
                        .param("email", "sonam@sonam.com"))
                .andDo(print()).andExpect(status().isOk());
                //.andExpect(content().string(containsString("Account is not active or does not exist")));

        LOG.info("serve the queued mock response for email username http callout");
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/issuer/oauth2/token");

        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("PUT");
        assertThat(request.getPath()).startsWith("/accounts/email/");
    }

    @Test
    public void passwordChangeTest() throws Exception {
        LOG.info("test the password change post method");

        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(200).setBody(clientCredentialResponse));

        final String passwordUpdated = "{\"message\":\"password updated\"}";
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setResponseCode(200).setBody(passwordUpdated));

        final String email = "sonam@sonam.com";
        final String secret = "x2340c";

        this.mockMvc.perform(post("/password/secret").param("email", email)
                        .param("secret", secret).param("password", "mysecretpassword"))
                .andDo(print()).andExpect(status().isOk());
        //.andExpect(content().string(containsString("Check your email for changing your password.")));

        LOG.info("take recorded requests");

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/issuer/oauth2/token");

        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("PUT");
        assertThat(request.getPath()).matches("/accounts/password-secret");
    }

}