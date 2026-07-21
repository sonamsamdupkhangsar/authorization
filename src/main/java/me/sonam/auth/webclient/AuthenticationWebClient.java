package me.sonam.auth.webclient;

import me.sonam.auth.service.exception.BadCredentialsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * This contains the authentication-rest-service rest endpoints as Java methods.
 */
public class AuthenticationWebClient {
    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationWebClient.class);

    private final WebClient.Builder webClientBuilder;

    private final String verifyPasswordEndpoint;
    private final String authenticationIdCheckEndpoint;

    private final String authIdNotExist;
    private final String authNotActive;
    private final String authPasswordNotSet;

    public AuthenticationWebClient(WebClient.Builder webClientBuilder,
                                   String verifyPasswordEndpoint, String authenticateIdCheckEndpoint,
                                   String authIdNotExist, String authNotActive, String authPasswordNotSet) {
        this.webClientBuilder = webClientBuilder;
        this.verifyPasswordEndpoint = verifyPasswordEndpoint;
        this.authenticationIdCheckEndpoint = authenticateIdCheckEndpoint;
        this.authIdNotExist = authIdNotExist;
        this.authNotActive = authNotActive;
        this.authPasswordNotSet = authPasswordNotSet;


    }

    public Mono<UUID> verifyPassword(Map<String, Object> mapBody) {
        LOG.info("verify password using endpoint: {}", verifyPasswordEndpoint);

        WebClient.ResponseSpec responseSpec = webClientBuilder.build().post().uri(verifyPasswordEndpoint)
                .bodyValue(mapBody).retrieve();

        //throws exception on authentication not found return with 401 http status
        return responseSpec.bodyToMono(Map.class).map(map -> {
            LOG.info("authentication response received");
            return UUID.fromString(map.get("userId").toString());
        }).onErrorResume(throwable -> {
            LOG.error("error on authentication-rest-service to endpoint '{}' with error: {}", verifyPasswordEndpoint,
                    throwable.getMessage());
            LOG.debug("exception", throwable);

            if (throwable instanceof WebClientResponseException) {
                WebClientResponseException webClientResponseException = (WebClientResponseException) throwable;
                LOG.error("error body contains: {}", webClientResponseException.getResponseBodyAsString());
                if (webClientResponseException.getResponseBodyAsString().contains("\"error\":")) {
                    String error = webClientResponseException.getResponseBodyAs(Map.class).get("error").toString();
                    return Mono.error(new BadCredentialsException(error));
                }
                else {
                    return Mono.error(new BadCredentialsException(webClientResponseException.getResponseBodyAsString()));
                }
            }
            else {
                return Mono.error(new BadCredentialsException("Bad credentials"));
            }
        });
    }

    public Mono<String> checkUsername(String ipAddress, String authenticationId) {
        LOG.info("check authenticationId endpoint: {}", authenticationIdCheckEndpoint);

        WebClient.ResponseSpec responseSpec = webClientBuilder.build().put().uri(authenticationIdCheckEndpoint)
                .bodyValue(Map.of("authenticationId", authenticationId)).retrieve();

        //throws exception on authentication not found return with 401 http status
        return responseSpec.bodyToMono(new ParameterizedTypeReference<Map<String, String>>() {})
                .flatMap(map -> {
                    LOG.debug("got response for authenticationId check {}", map);
                    String message = map.get("message");
                    if (message != null) {
                        return Mono.just(message);
                    }
                    else {
                        return Mono.just("no message");
                    }
                }).onErrorResume(throwable -> logError(authenticationId, ipAddress,"error in calling checkUsername: {}", throwable));
        //auth no active in error, bad auth in error also
    }

    private Mono<String> logError(String authenticationId, String ipAddress, String errorString, Throwable throwable) {
        LOG.error(errorString, throwable.getMessage());
        if (throwable instanceof WebClientResponseException) {
            WebClientResponseException webClientResponseException = (WebClientResponseException) throwable;
            LOG.error("error body contains: {}", webClientResponseException.getResponseBodyAsString());
            if (webClientResponseException.getResponseBodyAsString().contains("\"error\":")) {
                String error = webClientResponseException.getResponseBodyAs(Map.class).get("error").toString();

                if (error.contains(authIdNotExist)) {
                    LOG.info("authentication identifier was not found");
                    return Mono.just("Invalid username");

                }
                else if (error.contains(authNotActive)) {
                    LOG.info("authentication account is not active");
                    return Mono.just(error);
                }
                else if(error.contains(authPasswordNotSet)) {
                    LOG.info("authentication account has no password set");
                    return Mono.just(error);
                }
                else {
                    LOG.error("authentication service returned an unexpected response category");
                    return Mono.just("Bad credentials");
                }
            }
            else {
                LOG.info("error body does not contain error {}", throwable.getMessage());
                return Mono.just("Bad credentials");
            }
        }
        else {
            LOG.info("throwable not instance of WebClientResponseException {}", throwable.getMessage());
            return Mono.just("Bad credentials");
        }
    }
}
