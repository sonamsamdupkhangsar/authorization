package me.sonam.auth.rest;

import me.sonam.auth.webclient.AccountWebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * This controller is for returning the forgotUsername and forgotPassword html Thymeleaf page.
 * When user request their username on 'forgotUsername' page it will call {@link #emailUsername(String, Model)} method.
 * When user request password change on 'forgotPassword' page it will call {@link #passwordChange(String, Model)} method.
 * Permissions need to be set in the {@link me.sonam.auth.config.JwtUserInfoMapperSecurityConfig
 * #defaultSecurityFilterChain(HttpSecurity)} method for each path.
 */
@Controller
public class ForgotUsernameController {
    private static final Logger LOG = LoggerFactory.getLogger(ForgotUsernameController.class);

    private final AccountWebClient accountWebClient;

    public ForgotUsernameController(AccountWebClient accountWebClient) {
        this.accountWebClient = accountWebClient;
    }

    @GetMapping("/loginHelp")
    public String getLoginHelp() {
        LOG.info("return login help page");
        return "/loginHelp";
    }


    /**
     * @param email
     * @param secret
     * @param model
     * @return
     */
    @GetMapping("/password/{email}/{secret}")
    public Mono<String> passwordChange(@PathVariable("email") String email, @PathVariable("secret")String secret, Model model) {
        LOG.info("validating email '{}' and secret", email);

       return accountWebClient.validateEmailLoginSecret(email, secret)
                .flatMap(stringStringMap -> {
                    LOG.info("email and secret validated successfully");
                    model.addAttribute("email", email);
                    model.addAttribute("secret", secret);
                    model.addAttribute("message", "email and secret validated successfully");

                    return Mono.just("/passwordChange");
                })
                .onErrorResume(throwable -> {
                    setErrorInModel(throwable, model, "failed to validate email login secret");
                    return Mono.just("/passwordChange");
                });

    }

    @PostMapping("/password/{email}/{secret}")
    public Mono<String> passwordChange(@RequestParam String password , @PathVariable("email") String email, @PathVariable("secret") String secret, Model model) {
        LOG.info("change password: password: {}", password);

        return accountWebClient.updateAuthenticationPassword(email, secret, password)
                .flatMap(stringStringMap -> {
                    LOG.info("password has been changed: {}", stringStringMap);
                    model.addAttribute("message", "password has been updated successfully");
                    return Mono.just("/passwordChange");
                })
                .onErrorResume(throwable -> {
                    setErrorInModel(throwable, model, "failed to update password");
                    return Mono.just("/passwordChange");
                });

    }

    @GetMapping("/forgotUsername")
    public String forgotUsername() {
        LOG.info("returning forgotUsername");
        return "forgotUsername";
    }

    @PostMapping("/forgotUsername")
    public Mono<String> emailUsername(String emailAddress, Model model) {
        LOG.info("email username for email: {}", emailAddress);

      return  accountWebClient.emailUsername(emailAddress).flatMap(s -> {
                LOG.info("add message attribute");
                model.addAttribute("message", "Your username has been sent to your email address.");
                return Mono.just("forgotUsername");
            }).onErrorResume(throwable -> {
                setErrorInModel(throwable,model, "failed to call email username account-rest-service: "+ throwable.getMessage());
                return Mono.just("forgotUsername");
        });
    }


    @GetMapping("/forgotPassword")
    public String forgotPassword() {
        LOG.info("returning forgotPassword");
        return "forgotPassword";
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
    @PostMapping("/forgotPassword")
    public Mono<String> passwordChange(String email, Model model) {
        LOG.info("password change for email: {}", email);

        return accountWebClient.emailMySecret(email).flatMap(s -> {
            LOG.info("secret sent to email for password change");
            model.addAttribute("message", "Check your email for changing your password.");
            return Mono.just("forgotPassword");
        }).onErrorResume(throwable -> {
            LOG.error("error occurred in sending secret for password change", throwable);
            setErrorInModel(throwable, model, "error on calling emailMySecret endpoint  with error ");
            return Mono.just("forgotPassword");
        });
    }

    @GetMapping("/emailAccountActivateLink")
    public String emailAccountActivateLink() {
        LOG.info("returning emailAccountActivateLink page");

        return "emailAccountActivateLink";
    }

    @PostMapping("/emailAccountActivateLink")
    public String handleEmailAccountActivateLink(String emailAddress, Model model) {
        LOG.info("send email account activate link if inactive");

        return accountWebClient.emailAccountActivationLink(emailAddress).doOnNext(s -> {
            LOG.info("email sent");
            model.addAttribute("message", "email sent successfully, check your email");
        }).onErrorResume(throwable -> {
            setErrorInModel(throwable, model, "Failed to send email ");
            return Mono.just("/");
        }).thenReturn("/emailAccountActivateLink").block();

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
