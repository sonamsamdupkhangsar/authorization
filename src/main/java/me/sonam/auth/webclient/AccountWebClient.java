package me.sonam.auth.webclient;

import me.sonam.auth.service.exception.BadCredentialsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This contains the methods available for the account-rest-service as Java class methods
 */
public class AccountWebClient {
    private static final Logger LOG = LoggerFactory.getLogger(AccountWebClient.class);

    private final WebClient.Builder webClientBuilder;

    private final String emailUserName;
    private final String emailMySecret;
    private final String emailActiveLink;
    private final String validateEmailLoginSecret;
    private final String updatePassword;
    private final String emailSecretUnlockAccount;
    private final String lockAccount;
    private final String unLockAccount;
    private final String unLockAccountTimeExpire;
    private final String isAccountLockedEndpoint;

    public AccountWebClient(WebClient.Builder webClientBuilder,
                            String emailUserName, String emailMySecret, String emailActiveLink,
                            String validateEmailLoginSecret, String updatePassword,
                            String emailSecretUnlockAccount, String lockAccount,
                            String unLockAccount, String isAccountLockedEndpoint, String unLockAccountTimeExpire) {
        this.webClientBuilder = webClientBuilder;
        this.emailUserName = emailUserName;
        this.emailMySecret = emailMySecret;
        this.emailActiveLink = emailActiveLink;
        this.validateEmailLoginSecret = validateEmailLoginSecret;
        this.updatePassword = updatePassword;
        this.emailSecretUnlockAccount = emailSecretUnlockAccount;
        this.lockAccount = lockAccount;
        this.unLockAccount = unLockAccount;
        this.isAccountLockedEndpoint = isAccountLockedEndpoint;
        this.unLockAccountTimeExpire = unLockAccountTimeExpire;
    }

    public Mono<String> emailAccountActivationLink(String email, String activationHost) {
        LOG.info("email activation link endpoint: {} for email: {}", emailActiveLink, email);
        Map<String, String> body = new LinkedHashMap<>();
        body.put("email", email);
        if (activationHost != null && !activationHost.isBlank()) {
            body.put("activationHost", activationHost);
        }

        WebClient.ResponseSpec responseSpec = webClientBuilder.build().put().uri(emailActiveLink)
                .bodyValue(body)
                .retrieve();
        return responseSpec.bodyToMono(String.class);
    }

    public Mono<String> emailMySecret(String email, String activationHost) {
        LOG.info("emailMySecret endpoint: {} for email: {}", emailMySecret, email);
        Map<String, String> body = new LinkedHashMap<>();
        body.put("email", email);
        if (activationHost != null && !activationHost.isBlank()) {
            body.put("activationHost", activationHost);
        }

        WebClient.ResponseSpec responseSpec = webClientBuilder.build().put().uri(emailMySecret)
                .bodyValue(body)
                .retrieve();
        return responseSpec.bodyToMono(String.class).onErrorResume(throwable -> {
            if (throwable instanceof WebClientResponseException webClientResponseException
                    && webClientResponseException.getStatusCode().value() == 400) {
                LOG.warn("failed to call email my secret endpoint with bad request: {}",
                        webClientResponseException.getResponseBodyAsString());
            }
            else {
                LOG.error("failed to call email my secret endpoint", throwable);
            }
            return Mono.error(throwable);
        });
    }

    public Mono<Map<String, String>> updateAuthenticationPassword(String email, String secret, String password) {
        LOG.info("update password using email {} and endpoint account-rest-service {}", email, updatePassword);

        WebClient.ResponseSpec responseSpec = webClientBuilder.build().put().uri(updatePassword)
                .bodyValue(Map.of("email", email, "secret", secret, "password", password))
                .retrieve();
        return responseSpec.bodyToMono(new ParameterizedTypeReference<>() {});
    }

    public Mono<Map<String, String>> emailUsername(String email) {
        LOG.info("email {}, username endpoint: {}", email, emailUserName);

        WebClient.ResponseSpec responseSpec = webClientBuilder.build().put().uri(emailUserName).bodyValue(Map.of("email", email))
                .retrieve();
        return responseSpec.bodyToMono(new ParameterizedTypeReference<>() {});
    }

    /**
     * this is for emailing a secret to unlock account
     * @param email
     * @return
     */
    public Mono<Map<String, String>> emailSecretForAccountUnlock(String email, String activationHost) {
        LOG.info("email secret to unlock account using account-rest-service endpoint: {}",
                emailSecretUnlockAccount);
        Map<String, String> body = new LinkedHashMap<>();
        body.put("email", email);
        if (activationHost != null && !activationHost.isBlank()) {
            body.put("activationHost", activationHost);
        }

        WebClient.ResponseSpec responseSpec = webClientBuilder.build().put().uri(emailSecretUnlockAccount)
                .bodyValue(body)
                .retrieve();
        return responseSpec.bodyToMono(new ParameterizedTypeReference<>() {});
    }

    public Mono<Map<String, String>> lockAccount(String authenticationId) {
        LOG.info("lock account using authenticationId");

        LOG.info("lock account using endpoint: {}", lockAccount);

        WebClient.ResponseSpec responseSpec = webClientBuilder.build().put().uri(lockAccount)
                .bodyValue(Map.of("authenticationId", authenticationId))
                .retrieve();
        return responseSpec.bodyToMono(new ParameterizedTypeReference<>() {});
    }

    public Mono<Map<String, String>> unLockAccount(String email, String secret) {
        LOG.info("unlock account using email '{}' and secret: '{}'", email, secret);

        LOG.debug("unlock account endpoint {}", unLockAccount);
        WebClient.ResponseSpec responseSpec = webClientBuilder.build().put().uri(unLockAccount)
                .bodyValue(Map.of("email", email,
                        "secret", secret))
                .retrieve();
        return responseSpec.bodyToMono(new ParameterizedTypeReference<>() {});
    }

    public Mono<Map<String, String>> unLockAccountAfterTimedIntervalExpire(String authenticationId) {
        LOG.info("unlock account after timed interval expires using authenticationId  '{}''", authenticationId);

        LOG.debug("unlock account timed expire endpoint {}", unLockAccountTimeExpire);
        WebClient.ResponseSpec responseSpec = webClientBuilder.build().put().uri(unLockAccountTimeExpire)
                .bodyValue(Map.of("authenticationId", authenticationId))
                .retrieve();
        return responseSpec.bodyToMono(new ParameterizedTypeReference<Map<String, String>>() {})
                .doOnNext(map -> LOG.info("got response for unlock account time expire {}", map));
    }

    public Mono<Boolean> isAccountLocked(String authenticationId) {
        LOG.info("check if account is locked for authenticationId: {}", authenticationId);
        LOG.info("isAccountLockedEndpoint {}", isAccountLockedEndpoint);


        return webClientBuilder.build().put().uri(isAccountLockedEndpoint).bodyValue(Map.of("authenticationId", authenticationId))
                .retrieve().bodyToMono(new ParameterizedTypeReference<Map<String, Boolean>>() {})
                .flatMap(map -> {
                    LOG.info("map contains {}", map);

                    if (!map.containsKey("message")) {
                        return Mono.error(new BadCredentialsException("is account locked endpoint gave invalid response," +
                                " expecting message key with " +
                                "value of true or false string "+ map));
                    }
                    else {
                        if(!map.get("message").equals(true) && !map.get("message").equals(false)) {
                            return Mono.error(new BadCredentialsException("is account locked endpoint gave invalid response," +
                                    " expecting message value to be true or false: "+ map));
                        }
                    }

                    Boolean value = map.get("message");
                    return Mono.just(value);
                }).onErrorResume(throwable -> {
                    if (throwable instanceof WebClientResponseException webClientResponseException) {
                        LOG.error("error occured calling isAccountLocked endpoint {}: status={}, body={}",
                                isAccountLockedEndpoint,
                                webClientResponseException.getStatusCode(),
                                webClientResponseException.getResponseBodyAsString());
                    }
                    else {
                        LOG.error("error occured calling isAccountLocked endpoint {}: {}: {}",
                                isAccountLockedEndpoint,
                                throwable.getClass().getSimpleName(),
                                throwable.getMessage());
                        LOG.debug("isAccountLocked exception", throwable);
                    }
                    return Mono.error(throwable);
                });
    }
}
