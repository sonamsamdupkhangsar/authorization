package me.sonam.auth.webclient;

import me.sonam.auth.service.exception.BadCredentialsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.Charset;
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
    private final String isAccountLockedEndpoint;

    public AccountWebClient(WebClient.Builder webClientBuilder,
                            String emailUserName, String emailMySecret, String emailActiveLink,
                            String validateEmailLoginSecret, String updatePassword,
                            String emailSecretUnlockAccount, String lockAccount,
                            String unLockAccount, String isAccountLockedEndpoint) {
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
        return responseSpec.bodyToMono(new ParameterizedTypeReference<Map<String, String>>() {
        }).flatMap(map -> {
                LOG.info("received response {}", map);
                return Mono.just(map);
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
        String urlEncodedEmail = URLEncoder.encode(email, Charset.defaultCharset());

        String endpoint = emailUserName.replace("{email}", urlEncodedEmail);
        LOG.info("email {}, username endpoint: {}", email, endpoint);

        WebClient.ResponseSpec responseSpec = webClientBuilder.build().put().uri(endpoint)
                .retrieve();
        return responseSpec.bodyToMono(new ParameterizedTypeReference<>() {});
    }

    /**
     * this is for emailing a secret to unlock account
     * @param email
     * @return
     */
    public Mono<Map<String, String>> emailSecretForAccountUnlock(String email) {
        LOG.info("email secret to unlock account using account-rest-service");

        String endpoint = emailSecretUnlockAccount.replace("{email}", email);

        WebClient.ResponseSpec responseSpec = webClientBuilder.build().put().uri(endpoint)
                .retrieve();
        return responseSpec.bodyToMono(new ParameterizedTypeReference<>() {});
    }

    public Mono<Map<String, String>> lockAccount(String authenticationId) {
        LOG.info("lock account using authenticationId");

        String endpoint = lockAccount.replace("{authenticationId}", authenticationId);
        LOG.info("lock account using endpoint: {}", endpoint);

        WebClient.ResponseSpec responseSpec = webClientBuilder.build().put().uri(endpoint)
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

    public Mono<Boolean> isAccountLocked(String authenticationId) {
        LOG.info("check if account is locked for authenticationId: {}", authenticationId);
        LOG.info("isAccountLockedEndpoint {}", isAccountLockedEndpoint);
        final String endpoint = isAccountLockedEndpoint.replace("{authenticationId}", authenticationId);

        return webClientBuilder.build().get().uri(endpoint)
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
                    LOG.error("error occured calling isAccountLocked endpoint {}", endpoint, throwable);
                    return Mono.error(throwable);
                });
    }
}
