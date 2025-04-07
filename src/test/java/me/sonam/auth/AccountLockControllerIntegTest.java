package me.sonam.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.sonam.auth.rest.AccountLockController;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.ui.Model;

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
public class AccountLockControllerIntegTest {
    private static final Logger LOG = LoggerFactory.getLogger(AccountLockControllerIntegTest.class);

    @Autowired
    private MockMvc mockMvc;
    private static MockWebServer mockWebServer;

    @Autowired
    private ObjectMapper objectMapper;

    final String clientCredentialResponse = "{" +
            "    \"access_token\": \"eyJraWQiOiJhNzZhN2I0My00YTAzLTQ2MzAtYjVlMi0wMTUzMGRlYzk0MGUiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJwcml2YXRlLWNsaWVudCIsImF1ZCI6InByaXZhdGUtY2xpZW50IiwibmJmIjoxNjg3MTA0NjY1LCJzY29wZSI6WyJtZXNzYWdlLnJlYWQiLCJtZXNzYWdlLndyaXRlIl0sImlzcyI6Imh0dHA6Ly9sb2NhbGhvc3Q6OTAwMSIsImV4cCI6MTY4NzEwNDk2NSwiaWF0IjoxNjg3MTA0NjY1LCJhdXRob3JpdGllcyI6WyJtZXNzYWdlLnJlYWQiLCJtZXNzYWdlLndyaXRlIl19.Wx03Q96TR17gL-BCsG6jPxpdt3P-UkcFAuE6pYmZLl5o9v1ag9XR7MX71pfJcIhjmoog8DUTJXrq-ZB-IxIbMhIGmIHIw57FfnbBzbA8mjyBYQOLFOh9imLygtO4r9uip3UR0Ut_YfKMMi-vPfeKzVDgvaj6N08YNp3HNoAnRYrEJLZLPp1CUQSqIHEsGXn2Sny6fYOmR3aX-LcSz9MQuyDDr5AQcC0fbcpJva6aSPvlvliYABxfldDfpnC-i90F6azoxJn7pu3wTC7sjtvS0mt0fQ2NTDYXFTtHm4Bsn5MjZbOruih39XNsLUnp4EHpAh6Bb9OKk3LSBE6ZLXaaqQ\"," +
            "    \"scope\": \"message.read message.write\"," +
            "    \"token_type\": \"Bearer\"," +
            "    \"expires_in\": 299" +
            "}";

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
        r.add("attempt-rest-service.root", () -> "http://localhost:"+ mockWebServer.getPort());
    }

    @Test
    public void getUnLockAccountHtmlPage() throws Exception {
        LOG.info("get account/lock.html page");

        LOG.info("assert that the page returned is unlocking user account page.");
        this.mockMvc.perform(get("/accounts/lock")).andDo(print()).andExpect(status().isOk())
                .andExpect(content().string(containsString("To unlock your account enter your email address")));
    }

    @Test
    public void getUnLockAccountSecretHtmlPage() throws Exception {
        LOG.info("get account/lock-secret.html page");

        LOG.info("assert that the page returned is unlocking with secret form user account page.");
        this.mockMvc.perform(get("/accounts/lock/secret")).andDo(print()).andExpect(status().isOk())
                .andExpect(content().string(containsString("To unlock your account enter your email address and the secret.")));
    }


    /**
     *
     * This will test the endpoint 'emailUserToUnLockAccount' which will send a user an email
     * with a secret and a http link to unlock account associated with email and secret.
     * This starts the process of unlocking an account when the user enters their email address.
     * @throws Exception
     */
    @Test
    public void emailUserToUnLockAccount() throws Exception {
        LOG.info("email username");

        //1
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(200).setBody(clientCredentialResponse));

        //2
        LOG.info("add mock response for email username call into queue");
        final String emailMsg = " {\"message\":\"email successfully sent\"}";
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setResponseCode(201).setBody(emailMsg));//"Account created successfully.  Check email for activating account"));

        final String email = "dummy@xyqkl.com";
        final String urlEncodedEmail = URLEncoder.encode(email, Charset.defaultCharset());
        LOG.info("urlEncodedEmail: {}", urlEncodedEmail);

        this.mockMvc.perform(post("/accounts/lock/email")
                        .param("email", email))
                .andDo(print()).andExpect(status().isOk());
                //.andExpect(content().string(containsString("Check the associated email to unlock account.")));


        LOG.info("serve the queued mock response for email username http callout");
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/issuer/oauth2/token");

        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("PUT");
        //looks like the urlEncoded is getting urlEncoded again in the account put call so double it
        assertThat(request.getPath()).startsWith("/accounts/lock/email/password-secret");
    }

    /**
     * This method will test the process of actually unlocking an account associated with a email address and the secret.
     * This method will test the unLockAccount method @{{@link AccountLockController#unLockAccount(String, String, Model)}}
     * @throws Exception if error occurred
     */
    @Test
    public void unLockAccountWithEmailAndSecret() throws Exception {
        LOG.info("email username");
        final String email = "dummy@xyqkl.com";

        //1
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(200).setBody(clientCredentialResponse));

        //2
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setResponseCode(201).setBody("{\"message\":\"account unlocked for "+email+"\", " +
                        "\"authenticationId\": \"sonam\"}"));//"Account created successfully.  Check email for activating account"));

        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(200).setBody(clientCredentialResponse));

        //2
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setResponseCode(200).setBody("{\"message\":\"deleted by username\"}"));// loginAttempt data with username deleted response


        this.mockMvc.perform(post("/accounts/lock/email/secret").
                param("email", email).param("secret", "dummy-secret"))
                .andDo(print()).andExpect(status().isOk());

        LOG.info("serve the queued mock response for email username http callout");
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/issuer/oauth2/token");

        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("PUT");
        assertThat(request.getPath()).startsWith("/accounts/lock/false");

        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/issuer/oauth2/token");

        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("DELETE");
        assertThat(request.getPath()).startsWith("/attempts/sonam");
    }

}