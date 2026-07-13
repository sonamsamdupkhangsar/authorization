package me.sonam.auth.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.sonam.auth.service.HostOrganizationResolver;
import me.sonam.auth.service.LoginReturnContextService;
import me.sonam.auth.service.SignupPolicyService;
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
    private final LoginReturnContextService loginReturnContextService;
    private final HostOrganizationResolver hostOrganizationResolver;
    private final SignupPolicyService signupPolicyService;
    private final String PASSWORD_PAGE = "/password/password";
    private final String PASSWORD_SECRET_PAGE = "/password/password-secret";

    public PasswordChangeController(AccountWebClient accountWebClient, LoginReturnContextService loginReturnContextService,
                                    HostOrganizationResolver hostOrganizationResolver,
                                    SignupPolicyService signupPolicyService) {
        this.accountWebClient = accountWebClient;
        this.loginReturnContextService = loginReturnContextService;
        this.hostOrganizationResolver = hostOrganizationResolver;
        this.signupPolicyService = signupPolicyService;
    }

    @GetMapping("/password")
    public String forgotPassword(Model model, HttpServletRequest request, HttpServletResponse response) {
        LOG.info("returning {}", PASSWORD_PAGE);
        loginReturnContextService.addReturnContext(model, request, response);
        addAccountSelfServicePolicy(model);
        return PASSWORD_PAGE;
    }

    @GetMapping("/password/secret")
    public String getPasswordSecretPage(Model model, HttpServletRequest request, HttpServletResponse response) {
        LOG.info("returning {}", PASSWORD_SECRET_PAGE);
        loginReturnContextService.addReturnContext(model, request, response);
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
    public Mono<String> emailSecret(String email, Model model, HttpServletRequest request, HttpServletResponse response) {
        LOG.info("password change secret requested");
        loginReturnContextService.addReturnContext(model, request, response);
        if (blockAccountSelfService(model)) {
            return Mono.just(PASSWORD_PAGE);
        }

        return accountWebClient.emailMySecret(email, hostOrganizationResolver.currentHost().orElse(null)).flatMap(s -> {
            LOG.info("secret sent to email for password change");
            model.addAttribute("message", "Check your email for changing your password.");
            return Mono.just(PASSWORD_PAGE);
        }).onErrorResume(throwable -> {
            logAccountRestError("error occurred in sending secret for password change", throwable);
            setErrorInModel(throwable, model, "error on calling emailMySecret endpoint  with error ");
            return Mono.just(PASSWORD_PAGE);
        });
    }

    @PostMapping("/password/secret")
    public Mono<String> passwordChange(@NotEmpty String password ,@NotEmpty String email, @NotEmpty String secret,
                                       Model model, HttpServletRequest request, HttpServletResponse response) {
        LOG.info("password change submitted");
        loginReturnContextService.addReturnContext(model, request, response);

        return accountWebClient.updateAuthenticationPassword(email, secret, password)
                .flatMap(stringStringMap -> {
                    LOG.info("password changed successfully");
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
