package me.sonam.auth.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import me.sonam.auth.webclient.AccountWebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Map;

@Controller
public class PasswordChangeController {
    private static final Logger LOG = LoggerFactory.getLogger(PasswordChangeController.class);

    private final AccountWebClient accountWebClient;
    private final String PASSWORD_PAGE = "/password/password";
    private final String PASSWORD_SECRET_PAGE = "/password/password-secret";

    public PasswordChangeController(AccountWebClient accountWebClient) {
        this.accountWebClient = accountWebClient;
    }

    @GetMapping("/password")
    public String forgotPassword() {
        LOG.info("returning {}", PASSWORD_PAGE);
        return PASSWORD_PAGE;
    }

    @GetMapping("/password/secret")
    public String getPasswordSecretPage() {
        LOG.info("returning {}", PASSWORD_SECRET_PAGE);
        return PASSWORD_SECRET_PAGE;
    }

    /**
     * this is called to change password by user when they don't remember it anymore.
     * This will call account-rest-service method to start the process for password change.
     * Account-rest-service will create accesscode for password change process and send
     * them a link with the code to click in the email.
     * @param email
     * @param model
     * @return
     */
    @PostMapping("/password")
    public Mono<String> emailSecret(String email, Model model) {
        LOG.info("password change for email: {}", email);

        return accountWebClient.emailMySecret(email).flatMap(s -> {
            LOG.info("secret sent to email for password change");
            model.addAttribute("message", "Check your email for changing your password.");
            return Mono.just(PASSWORD_PAGE);
        }).onErrorResume(throwable -> {
            LOG.error("error occurred in sending secret for password change", throwable);
            setErrorInModel(throwable, model, "error on calling emailMySecret endpoint  with error ");
            return Mono.just(PASSWORD_PAGE);
        });
    }

    @PostMapping("/password/secret")
    public Mono<String> passwordChange(@NotEmpty String password ,@NotEmpty String email, @NotEmpty String secret, Model model) {
        LOG.info("change password {} for email {} and secret: {}", password, email, secret);

        return accountWebClient.validateEmailLoginSecret(email, secret)
                .doOnNext(stringStringMap -> LOG.info("email and secret validated"))
                .flatMap(stringStringMap ->
                    accountWebClient.updateAuthenticationPassword(email, secret, password))
                .flatMap(stringStringMap -> {
                    LOG.info("password has been changed: {}", stringStringMap);
                    model.addAttribute("message", "password has been updated successfully");
                    return Mono.just(PASSWORD_SECRET_PAGE);
                })
                .onErrorResume(throwable -> {
                    setErrorInModel(throwable, model, "failed to update password");
                    return Mono.just(PASSWORD_SECRET_PAGE);
                });
    }


    private void setErrorInModel(Throwable throwable, Model model, String defaultErrMessage) {
        if (throwable instanceof WebClientResponseException webClientResponseException) {
            Map<String, String> map = webClientResponseException.getResponseBodyAs(
                    new ParameterizedTypeReference<>() {});

            if (map != null) {
                LOG.error("{}: {}", defaultErrMessage, map.get("error"));

                model.addAttribute("error", map.get("error"));
            }
            else {
                LOG.error("map is null on response for throwable", throwable);
                model.addAttribute("error", defaultErrMessage + throwable.getMessage());
            }
            LOG.error("{}: {}", defaultErrMessage, throwable.getMessage());
        } else {
            //set model error attribute to present back to user
            model.addAttribute("error", defaultErrMessage  + throwable.getMessage());
        }
    }
}
