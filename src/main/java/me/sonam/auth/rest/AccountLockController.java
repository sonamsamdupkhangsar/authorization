package me.sonam.auth.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.sonam.auth.service.HostOrganizationResolver;
import me.sonam.auth.service.LoginReturnContextService;
import me.sonam.auth.service.SignupPolicyService;
import me.sonam.auth.webclient.AccountWebClient;
import me.sonam.auth.webclient.LoginAttemptWebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * This controller is for returning the {@link templates/account/lock.html" and templates/account/lock-secret.html
 * Thymeleaf pages.
 * The user will see the {@link /account/lock.html} when the path is /lockAccount.
 * When user enters their email in the textfield and submits the form it will call
 * {@link #emailUserToUnLockAccount(String, Model)} (String, Model)} method.  This method will call account-rest-service
 *  emailSecretForAccountlock, which will create a password-secret and send a email to the user to allow un locking of
 *  their account.
 * In the email the user will see the secret and a link to unlock their account.  The user clicks on the link which is
 * the account/lock-secret.html page where they will enter their secret.  If the secret is good and valid (has not expired
 * by a time of 30 mins or 1hr then their account will be set to unlocked.
 * #defaultSecurityFilterChain(HttpSecurity)} method for each path.
 */
@Controller
public class AccountLockController {
    private static final Logger LOG = LoggerFactory.getLogger(AccountLockController.class);

    private final LoginAttemptWebClient loginAttemptWebClient;
    private final AccountWebClient accountWebClient;
    private final LoginReturnContextService loginReturnContextService;
    private final HostOrganizationResolver hostOrganizationResolver;
    private final SignupPolicyService signupPolicyService;
    private final String lockAccounnt = "account/lock";
    private final String lockSecret = "account/lock-secret";

    public AccountLockController(AccountWebClient accountWebClient, LoginAttemptWebClient loginAttemptWebClient,
                                 LoginReturnContextService loginReturnContextService,
                                 HostOrganizationResolver hostOrganizationResolver,
                                 SignupPolicyService signupPolicyService) {
        this.accountWebClient = accountWebClient;
        this.loginAttemptWebClient = loginAttemptWebClient;
        this.loginReturnContextService = loginReturnContextService;
        this.hostOrganizationResolver = hostOrganizationResolver;
        this.signupPolicyService = signupPolicyService;
    }

    /**
     * This will return the lock page form with a textfield for email.
     * This email will be used in the {@link #emailUserToUnLockAccount(String, Model)} method.
     * @return
     */
    @GetMapping("/accounts/lock")
    public String getLockAccountPage(Model model, HttpServletRequest request, HttpServletResponse response) {
        LOG.info("returning {}", lockAccounnt);
        loginReturnContextService.addReturnContext(model, request, response);
        addAccountSelfServicePolicy(model);
        return lockAccounnt;
    }

    @GetMapping("/accounts/lock/secret")
    public String getLockAccountWithSecretPage(Model model, HttpServletRequest request, HttpServletResponse response) {
        LOG.info("returning {}", lockAccounnt);
        loginReturnContextService.addReturnContext(model, request, response);
        return lockSecret;
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
    @PostMapping("/accounts/lock/email")
    public Mono<String> emailUserToUnLockAccount(String email, Model model,
                                                 HttpServletRequest request, HttpServletResponse response) {
        LOG.info("account-unlock email requested");
        loginReturnContextService.addReturnContext(model, request, response);
        if (blockAccountSelfService(model)) {
            return Mono.just(lockAccounnt);
        }

        return accountWebClient.emailSecretForAccountUnlock(email,
                hostOrganizationResolver.currentHost().orElse(null)).flatMap(s -> {
            LOG.info("Email has been sent with a secret to unlock the user account");
            model.addAttribute("message", "Check the associated email to unlock account.");
            return Mono.just(lockAccounnt);
        }).onErrorResume(throwable -> {
            logAccountRestError("error occurred in sending email for unlocking account with secret", throwable);
            setErrorInModel(throwable, model, "error on calling emailSecretForAccountUnlock");
            return Mono.just(lockAccounnt);
        });
    }

    /**
     * This method will unlock the account associated with email and with the secret.
     * The email and secret will be validated.  The reason must match in the account-rest-service
     * which contains the explanation for the secret (UNLOCK_ACCOUNT).
     */
    @PostMapping("/accounts/lock/email/secret")
    public Mono<String> unLockAccount(String email, String secret, Model model,
                                      HttpServletRequest request, HttpServletResponse response) {
        LOG.info("unlock account with email and secret");
        loginReturnContextService.addReturnContext(model, request, response);

        return accountWebClient.unLockAccount(email, secret).flatMap(map -> {
            LOG.info("The account has been successfully unlocked, response: {}", map);
            model.addAttribute("message", "The account associated with the email has been unlocked.");
            String authenticationId = map.get("authenticationId");

            return loginAttemptWebClient.deleteLoginAttempt(authenticationId).thenReturn(lockSecret);
        }).onErrorResume(throwable -> {
            LOG.error("error occurred in unlocking account with  email and secret", throwable);
            setErrorInModel(throwable, model, "error occurred in unlocking account with email and secret");
            return Mono.just(lockSecret);
        });
    }

    private void setErrorInModel(Throwable throwable, Model model, String defaultErrMessage) {
        if (throwable instanceof WebClientResponseException webClientResponseException) {
            Map<String, String> map = webClientResponseException.getResponseBodyAs(
                    new ParameterizedTypeReference<>() {});

            if (map != null) {
                logAccountRestError(defaultErrMessage + ": " + map.get("error"), throwable);

                model.addAttribute("error", map.get("error"));
            }
            else {
                logAccountRestError("map is null on response for throwable", throwable);
                model.addAttribute("error", defaultErrMessage + throwable.getMessage());
            }
        } else {
            //set model error attribute to present back to user
            model.addAttribute("error", defaultErrMessage  + throwable.getMessage());
        }
    }

    private void addAccountSelfServicePolicy(Model model) {
        signupPolicyService.validateAccountSelfServiceAllowedForCurrentHost().ifPresent(error -> {
            model.addAttribute("accountSelfServiceDisabled", true);
            model.addAttribute("error", error);
        });
    }

    private boolean blockAccountSelfService(Model model) {
        var error = signupPolicyService.validateAccountSelfServiceAllowedForCurrentHost();
        if (error.isEmpty()) {
            return false;
        }
        model.addAttribute("accountSelfServiceDisabled", true);
        model.addAttribute("error", error.get());
        return true;
    }

    private void logAccountRestError(String message, Throwable throwable) {
        if (throwable instanceof WebClientResponseException webClientResponseException
                && webClientResponseException.getStatusCode().value() == 400) {
            LOG.warn("{}: {}", message, webClientResponseException.getResponseBodyAsString());
        }
        else {
            LOG.error(message, throwable);
        }
    }

}
