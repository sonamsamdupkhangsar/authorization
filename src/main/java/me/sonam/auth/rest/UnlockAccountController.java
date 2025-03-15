package me.sonam.auth.rest;

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

/**
 * This controller is for returning the {@link templates/account/unlock.html" and templates/account/unlock-secret.html
 * Thymeleaf pages.
 * The user will see the {@link /account/unlock.html} when the path is /unLockAccount.
 * When user enters their email in the textfield and submits the form it will call
 * {@link #emailUserToUnLockAccount(String, Model)} (String, Model)} method.  This method will call account-rest-service
 *  emailSecretForAccountUnlock, which will create a password-secret and send a email to the user to allow unlocking of
 *  their account.
 * In the email the user will see the secret and a link to unlock their account.  The user clicks on the link which is
 * the account/unlock-secret.html page where they will enter their secret.  If the secret is good and valid (has not expired
 * by a time of 30 mins or 1hr then their account will be set to unlocked.
 * #defaultSecurityFilterChain(HttpSecurity)} method for each path.
 */
@Controller
public class UnlockAccountController {
    private static final Logger LOG = LoggerFactory.getLogger(UnlockAccountController.class);

    private final AccountWebClient accountWebClient;
    private final String unLockAccounnt = "account/unlock";
    private final String unLockSecret = "account/unlock-secret";

    public UnlockAccountController(AccountWebClient accountWebClient) {
        this.accountWebClient = accountWebClient;
    }

    /**
     * This will return the unlock form with a textfield for email.
     * This email will be used in the {@link #emailUserToUnLockAccount(String, Model)} method.
     * @return
     */
    @GetMapping("/unLockAccount")
    public String getUnlockAccounnt() {
        LOG.info("returning {}", unLockAccounnt);
        return unLockAccounnt;
    }

    /**
     * This method is called to start the process for unlocking an account for a user.
     * This will send the user an email with a secret to unlock their account.
     * The user will have to click on the link and enter the secret which will be sent with
     * this method by the account-rest-service http call.
     * @param email
     * @param model
     * @return
     */
    @PostMapping("/emailUserToUnLockAccount")
    public Mono<String> emailUserToUnLockAccount(String email, Model model) {
        LOG.info("email secret to unlock account for email: {}", email);

        return accountWebClient.emailSecretForAccountUnlock(email).flatMap(s -> {
            LOG.info("Email has been sent with a secret to unlock the user account");
            model.addAttribute("message", "Check the associated email to unlock account.");
            return Mono.just(unLockAccounnt);
        }).onErrorResume(throwable -> {
            LOG.error("error occurred in sending email for unlocking account with secret", throwable);
            setErrorInModel(throwable, model, "error on calling emailSecretForAccountUnlock");
            return Mono.just(unLockAccounnt);
        });
    }

    /**
     * This method will unlock the account associated with email and with the secret.
     * The email and secret will be validated.  The reason must match in the account-rest-service
     * which contains the explanation for the secret (UNLOCK_ACCOUNT).
     */
    @PostMapping("/unLockAccount")
    public Mono<String> unLockAccount(String email, String secret, Model model) {
        LOG.info("unlock account with email and secret");

        return accountWebClient.unLockAccount(email, secret).flatMap(s -> {
            LOG.info("The account has been successfully unlocked, response: {}", s);
            model.addAttribute("message", "The account associated with the email has been unlocked.");
            return Mono.just(unLockSecret);
        }).onErrorResume(throwable -> {
            LOG.error("error occurred in unlocking account with  email and secret", throwable);
            setErrorInModel(throwable, model, "error occurred in unlocking account with email and secret");
            return Mono.just(unLockSecret);
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
