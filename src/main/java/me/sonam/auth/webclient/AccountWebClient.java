package me.sonam.auth.webclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Map;

public class AccountWebClient {
    private static final Logger LOG = LoggerFactory.getLogger(AccountWebClient.class);

    private final WebClient.Builder webClientBuilder;

    private final String emailUserName;
    private final String emailMySecret;
    private final String emailActiveLink;
    private final String validateEmailLoginSecret;
    private final String updatePassword;

    public AccountWebClient(WebClient.Builder webClientBuilder,
                            String emailUserName, String emailMySecret, String emailActiveLink,
                            String validateEmailLoginSecret, String updatePassword) {
        this.webClientBuilder = webClientBuilder;
        this.emailUserName = emailUserName;
        this.emailMySecret = emailMySecret;
        this.emailActiveLink = emailActiveLink;
        this.validateEmailLoginSecret = validateEmailLoginSecret;
        this.updatePassword = updatePassword;
    }

    public Mono<String> emailAccountActivationLink(String email) {
        String urlEncodedEmail = URLEncoder.encode(email, Charset.defaultCharset());
        final String endpoint = emailActiveLink.replace("{email}", urlEncodedEmail);
        LOG.info("email using endpoint: {}", endpoint);

        WebClient.ResponseSpec responseSpec = webClientBuilder.build().put().uri(endpoint)
                .retrieve();
        return responseSpec.bodyToMono(String.class);
    }

    public Mono<String> emailMySecret(String email) {
        String urlEncodedEmail = URLEncoder.encode(email, Charset.defaultCharset());
        LOG.info("urlEncodedEmail: {}, and raw email: {}", urlEncodedEmail, email);
        LOG.info("emailMySecret endpoint: {}", emailMySecret);

        String endpoint = emailMySecret.replace("{email}", urlEncodedEmail);
        LOG.info("email '{}' using endpoint: {}", email, endpoint);

        WebClient.ResponseSpec responseSpec = webClientBuilder.build().put().uri(endpoint)
                .retrieve();
        return responseSpec.bodyToMono(String.class).onErrorResume(throwable -> {
            LOG.error("failed to call email my secret endpoint", throwable);
            return Mono.error(throwable);
        });
    }

    public Mono<Map<String, String>> validateEmailLoginSecret(String email, String secret) {
        LOG.info("call validate email login secret using account-rest-service");


        String endpoint = validateEmailLoginSecret.replace("{email}", URLEncoder.encode(email, Charset.defaultCharset())).replace("{secret}", secret);
        LOG.info("validate secret using endpoint: {}", endpoint);

        WebClient.ResponseSpec responseSpec = webClientBuilder.build().get().uri(endpoint)
                .retrieve();
        return responseSpec.bodyToMono(new ParameterizedTypeReference<>() {});
    }

    public Mono<Map<String, String>> updateAuthenticationPassword(String email, String secret, String password) {
        LOG.info("update password using email {} and endpoint account-rest-service {}", email, updatePassword);

        WebClient.ResponseSpec responseSpec = webClientBuilder.build().put().uri(updatePassword)
                .bodyValue(Map.of("email", email, "secret", secret, "password", password))
                .retrieve();
        return responseSpec.bodyToMono(new ParameterizedTypeReference<>() {});
    }

    public Mono<Map<String, String>> emailUsername(String email) {
        String urlEncodedEmail = URLEncoder.encode(email, Charset.defaultCharset());

        String endpoint = emailUserName.replace("{email}", urlEncodedEmail);
        LOG.info("email {}, username endpoint: {}", email, endpoint);

        WebClient.ResponseSpec responseSpec = webClientBuilder.build().put().uri(endpoint)
                .retrieve();
        return responseSpec.bodyToMono(new ParameterizedTypeReference<>() {});
    }
}
