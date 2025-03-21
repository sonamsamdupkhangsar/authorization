package me.sonam.auth.webclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * This contains the attempt-rest-service endpoints as Java class methods.
 */
public class LoginAttemptWebClient {
    private static final Logger LOG = LoggerFactory.getLogger(LoginAttemptWebClient.class);

    private final WebClient.Builder webClientBuilder;

    private final String failed;
    private final String success;
    private final String delete;
    private AccountWebClient accountWebClient;

    public LoginAttemptWebClient(WebClient.Builder webClientBuilder, String failed,
                                 String success, AccountWebClient accountWebClient,
                                 String delete) {
        this.webClientBuilder = webClientBuilder;
        this.failed = failed;
        this.success = success;
        this.delete = delete;
        this.accountWebClient = accountWebClient;
    }

    public Mono<String> loginFailed(String username, String ipAddress) {
        Map<String, String> map = new HashMap<>();

        map.put("username", username);
        map.put("ipAddress", ipAddress);

        final String endpoint = failed.replace("{username}", username);
        LOG.info("calling loginFailed using endpoint: {}", endpoint);

        WebClient.ResponseSpec responseSpec = webClientBuilder.build().put().uri(endpoint)
                .bodyValue(map)
                .retrieve();
        return responseSpec.bodyToMono(new ParameterizedTypeReference<Map<String, String>>(){})
                .flatMap(map1 -> {
                    final String message = map1.get("message");

                    if (message.contains("You can retry login again in")) {
                        return accountWebClient.lockAccount(username).thenReturn(message)
                                .onErrorResume(error -> {
                                    LOG.error("error occurred on calling account lockAccount", error);
                                    return Mono.error(new Error(error));
                                });
                    }
                    return Mono.just(message);

                }).onErrorResume(throwable -> {
                    LOG.error("failed to call attempt-rest-service login failed endpoint", throwable);
                    if (throwable instanceof WebClientResponseException) {
                        WebClientResponseException webClientResponseException = (WebClientResponseException) throwable;
                        LOG.error("error body contains: {}", webClientResponseException.getResponseBodyAsString());
                        if (webClientResponseException.getResponseBodyAsString().contains("\"error\":")) {
                            String error = webClientResponseException.getResponseBodyAs(Map.class).get("error").toString();
                            return  Mono.just(error);
                        }
                        else {
                            return Mono.just(webClientResponseException.getResponseBodyAsString());
                        }
                    }

                    return Mono.error(throwable);
                });
    }

    public Mono<String> loginSucccess(String username, UUID userId, String ipAddress) {
        Map<String, String> map = new HashMap<>();

        map.put("username", username);
        map.put("ipAddress", ipAddress);
        map.put("userId", userId.toString());

        final String endpoint = success.replace("{username}", username);
        LOG.info("clear failed login attempts using endpoint: {}", endpoint);

        WebClient.ResponseSpec responseSpec = webClientBuilder.build().put().uri(endpoint)
                .bodyValue(map)
                .retrieve();
        return responseSpec.bodyToMono(String.class);
    }

    public Mono<String> deleteLoginAttempt(String username) {
        final String endpoint = delete.replace("{username}", username);

        LOG.info("delete login attempts using endpoint: {}", endpoint);

        WebClient.ResponseSpec responseSpec = webClientBuilder.build().delete().uri(endpoint)
                .retrieve();
        return responseSpec.bodyToMono(String.class)
                .doOnNext(s -> LOG.info("response from delete login attempt is {}", s))
                .onErrorResume(throwable -> {
                    LOG.error("failed to delete loginAttempt by username {}", throwable.getMessage());
                    LOG.debug("error stack trace ", throwable);
                    return Mono.error(throwable);
                });
    }
}
