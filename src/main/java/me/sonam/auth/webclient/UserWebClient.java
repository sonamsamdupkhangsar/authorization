package me.sonam.auth.webclient;

import me.sonam.auth.rest.signup.User;
import me.sonam.auth.rest.signup.UserSignup;
import me.sonam.auth.service.exception.BadCredentialsException;
import org.apache.tomcat.websocket.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

public class UserWebClient {
    private static final Logger LOG = LoggerFactory.getLogger(UserWebClient.class);

    private final WebClient.Builder webClientBuilder;
    private final String userRestServiceEndpoint;
    private final String userByAuthIdEp;

    public UserWebClient(WebClient.Builder webClientBuilder,
                         String userByAuthIdEp,
                         String userRestServiceEndpoint) {
        this.webClientBuilder = webClientBuilder;
        this.userByAuthIdEp = userByAuthIdEp;
        this.userRestServiceEndpoint = userRestServiceEndpoint;
    }

    public Mono<Map> signupUser(UserSignup userSignup) {
        LOG.info("create user with endpoint: {}", userRestServiceEndpoint);

        WebClient.RequestBodySpec requestBodySpec = webClientBuilder.build().post().uri(userRestServiceEndpoint);

        WebClient.ResponseSpec responseSpec = requestBodySpec
                .bodyValue(userSignup)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve();

        return responseSpec.bodyToMono(Map.class);

    }
    /**
     * this is for finding user by authenticationId for application purpose
     * @param authenticationId
     * @return
     */
    public Mono<User> findByAuthenticationId(String authenticationId) {
        LOG.info("find user by authenticationId: {}", authenticationId);

        StringBuilder stringBuilder = new StringBuilder(userRestServiceEndpoint).append("/authentication-id/")
                .append(authenticationId);

        LOG.info("find user with authentication-id with endpoint: {}", stringBuilder);
        WebClient.ResponseSpec responseSpec = webClientBuilder.build().get().uri(stringBuilder.toString())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve();

        return responseSpec.bodyToMono(User.class).onErrorResume(throwable -> {
            LOG.error("user not found with authenticationId: {}", authenticationId, throwable.getMessage());
            String errorMessage = throwable.getMessage();
            if (throwable instanceof WebClientResponseException) {
                WebClientResponseException webClientResponseException = (WebClientResponseException) throwable;
                LOG.error("error body contains: {}", webClientResponseException.getResponseBodyAsString());
                if (webClientResponseException.getResponseBodyAsString().contains("\"error\":"))
                {
                    Map<String, String> errorResponseMap = webClientResponseException.getResponseBodyAs(Map.class);
                    if (errorResponseMap != null) {
                        errorMessage = errorResponseMap.get("error");
                    }
                    else {
                        errorMessage = webClientResponseException.getResponseBodyAsString();
                    }
                }
            }

            return Mono.error(new AuthenticationException(errorMessage));//Mono.just(new User());
        });
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
