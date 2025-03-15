package me.sonam.auth.webclient;

import me.sonam.auth.service.exception.BadCredentialsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

public class UserWebClient {
    private static final Logger LOG = LoggerFactory.getLogger(UserWebClient.class);

    private final WebClient.Builder webClientBuilder;

    private final String userByAuthIdEp;

    public UserWebClient(WebClient.Builder webClientBuilder,
                         String userByAuthIdEp) {
        this.webClientBuilder = webClientBuilder;
        this.userByAuthIdEp = userByAuthIdEp;
    }

    public Mono<Map<String, String>> getUserByAuthenticationId(String authenticationId) {
        final String userInfoEndpoint = userByAuthIdEp.replace("{authenticationId}", authenticationId);
        LOG.info("get user data with authenticationId: {}", userInfoEndpoint);

        WebClient.ResponseSpec responseSpec = webClientBuilder.build().get().uri(userInfoEndpoint)
                .retrieve();

        return responseSpec.bodyToMono(new ParameterizedTypeReference<Map<String, String>>() {
        }).onErrorResume(throwable -> {
            LOG.error("error on getting user info from user-rest-service endpoint '{}' with error: {}",
                    userInfoEndpoint, throwable.getMessage());
            return Mono.error(new RuntimeException("user info call failed, error: " + throwable.getMessage()));
        });
    }

    public Mono<UUID> getUserId(String authenticationId) {
        StringBuilder userByAuthId = new StringBuilder(userByAuthIdEp.replace("{authenticationId}",
                authenticationId));

        LOG.info("make user call out to endpoint: {}", userByAuthId);

        WebClient.ResponseSpec responseSpec = webClientBuilder.build().get().uri(userByAuthId.toString())
                .retrieve();

        //throws exception on authentication not found return with 401 http status
        return responseSpec.bodyToMono(Map.class).map(map -> {
            LOG.info("user found: {}", map);
            return UUID.fromString(map.get("id").toString());
        }).onErrorResume(throwable -> {
            LOG.error("error on get user by authId to endpoint '{}' with error: {}", userByAuthId,
                    throwable.getMessage());

            if (throwable instanceof WebClientResponseException) {
                WebClientResponseException webClientResponseException = (WebClientResponseException) throwable;
                LOG.error("error body contains: {}", webClientResponseException.getResponseBodyAsString());
                return Mono.error(new BadCredentialsException("Failed to get user by authId"));
            } else {
                return Mono.error(new BadCredentialsException("Failed to get user by authId"));
            }
        });
    }



}
