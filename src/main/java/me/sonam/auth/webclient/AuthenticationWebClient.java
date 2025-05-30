package me.sonam.auth.webclient;

import jakarta.servlet.http.HttpServletRequest;
import me.sonam.auth.service.exception.BadCredentialsException;
import me.sonam.auth.util.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
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

    private final String authenticateEndpoint;
    private final String authenticationIdCheckEndpoint;

    private final String ROLES = "roles";

    private final LoginAttemptWebClient loginAttemptWebClient;

    private final String authIdNotExist;
    private final String authNotActive;
    private final String authPasswordNotSet;

    public AuthenticationWebClient(WebClient.Builder webClientBuilder,
                                   String authenticateEndpoint, String authenticateIdCheckEndpoint,
                                   LoginAttemptWebClient loginAttemptWebClient, String authIdNotExist,
                                   String authNotActive, String authPasswordNotSet) {
        this.webClientBuilder = webClientBuilder;
        this.authenticateEndpoint = authenticateEndpoint;
        this.authenticationIdCheckEndpoint = authenticateIdCheckEndpoint;
        this.loginAttemptWebClient = loginAttemptWebClient;
        this.authIdNotExist = authIdNotExist;
        this.authNotActive = authNotActive;
        this.authPasswordNotSet = authPasswordNotSet;


    }

    public Mono<UsernamePasswordAuthenticationToken> getAuth(Authentication authentication, Map<String, Object> mapBody) {
        String password = authentication.getCredentials().toString();

        LOG.info("make authentication call out to endpoint: {}", authenticateEndpoint);

        WebClient.ResponseSpec responseSpec = webClientBuilder.build().post().uri(authenticateEndpoint)
                .bodyValue(mapBody).retrieve();

        //throws exception on authentication not found return with 401 http status
        return responseSpec.bodyToMono(Map.class).flatMap(map -> {
            LOG.info("authentication response contains: {}", map);

            final List<GrantedAuthority> grantedAuths = new ArrayList<>();

            if (map.get(ROLES) != null) {
                String roleList = map.get(ROLES).toString();
                roleList = roleList.replace("[", "");
                roleList = roleList.replace("]", "");

                LOG.debug("go thru each roleName from list and add to grantedAuths: {}", roleList);
                String[] roles = roleList.split(",");
                for(String role: roles) {
                    LOG.info("add role: {}", role);
                    if (!role.trim().isEmpty()) {
                        grantedAuths.add(new SimpleGrantedAuthority(role));
                    }
                }
            }
            UUID userId = UUID.fromString(map.get("userId").toString());
            final UserId principal = new UserId(userId.toString(), authentication.getPrincipal().toString(), password, grantedAuths);

            if(grantedAuths.isEmpty()) {
                LOG.info("roles is empty, add a default one for now.");
                grantedAuths.add(new SimpleGrantedAuthority("USER_ROLE"));
            }

            String ipAddress = "";
            if (authentication.getDetails() instanceof WebAuthenticationDetails) {
                WebAuthenticationDetails webDetails = (WebAuthenticationDetails) authentication.getDetails();
                // Access webDetails properties
                LOG.info("details is webAuthenitcationDetails type");
                ipAddress = webDetails.getRemoteAddress();
                LOG.info("ip address is {}", ipAddress);
            }

            LOG.info("returning using custom authenticator with grantedAuths added: {}", grantedAuths);
            return loginAttemptWebClient.loginSucccess(authentication.getPrincipal().toString(), userId, ipAddress)
                    .thenReturn(new UsernamePasswordAuthenticationToken(principal, password, grantedAuths));

        }).onErrorResume(throwable -> {
            LOG.error("error on authentication-rest-service to endpoint '{}' with error: {}", authenticateEndpoint,
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

                //HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
             //   String ipAddress = ".1.1.1.1";//request.getRemoteAddr();
                LOG.info("ipAddress {}", ipAddress);

                if (error.contains(authIdNotExist)) {
                    LOG.info("audit authId tried {}", authenticationId);
                    final String invalidUsername = "Invalid username";
                    return loginAttemptWebClient.loginFailed(authenticationId, ipAddress).
                            flatMap(s -> Mono.just("Invalid username. " + s));
                }
                else if (error.contains(authNotActive)) {
                    LOG.info("audit auth not active for {}", authenticationId);
                    return loginAttemptWebClient.loginFailed(authenticationId, ipAddress).thenReturn(error);
                }
                else if(error.contains(authPasswordNotSet)) {
                    LOG.info("audit password not set for {}", authenticationId);
                    return loginAttemptWebClient.loginFailed(authenticationId, ipAddress).thenReturn(error);
                }
                else {
                    LOG.error("different response {} for authId {}", error, authenticationId);
                    return Mono.error(new BadCredentialsException(error));
                }
            }
            else {
                LOG.info("error body does not contain error {}", throwable.getMessage());
                return Mono.error(new BadCredentialsException(webClientResponseException.getResponseBodyAsString()));
            }
        }
        else {
            LOG.info("throwable not instance of WebClientResponseException {}", throwable.getMessage());
            return Mono.error(new BadCredentialsException("Bad credentials"));
        }
    }
}
