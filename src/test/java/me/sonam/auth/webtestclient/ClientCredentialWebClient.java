package me.sonam.auth.webtestclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Base64;
import java.util.List;
import java.util.Map;


public class ClientCredentialWebClient {
    private static final Logger LOG = LoggerFactory.getLogger(ClientCredentialWebClient.class);

    private WebTestClient webTestClient;

    public ClientCredentialWebClient(WebTestClient webTestClient) {
        this.webTestClient = webTestClient;
    }

    public String getAccessToken(String clientId, String secret) {
        LOG.info("requesting client-credentials token for clientId: {}", clientId);

        String base64ClientSecret = Base64.getEncoder().encodeToString((clientId +
                ":" + secret).getBytes());

        MultiValueMap<String, Object> mvm = new LinkedMultiValueMap<>();
        mvm.add("grant_type", "client_credentials");
        mvm.add("scopes", List.of("message.read", "message.write"));

        LOG.info("request oauth access token first");
        EntityExchangeResult<Map<String, String>> tokenEntityExchangeResult = webTestClient.post()
                .uri("/oauth2/token")
                .headers(httpHeaders -> httpHeaders.setBasicAuth(base64ClientSecret))
                .bodyValue(mvm)
                .exchange().expectStatus().isOk().expectBody(new ParameterizedTypeReference<Map<String, String>>() {
                })
                .returnResult();

        LOG.info("client-credentials token response received");

        final Map<String, String> map = tokenEntityExchangeResult.getResponseBody();
        return map.get("access_token");
    }

}
