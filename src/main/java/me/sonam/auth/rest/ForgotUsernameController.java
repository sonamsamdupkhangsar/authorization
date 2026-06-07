package me.sonam.auth.rest;

import me.sonam.auth.webclient.AccountWebClient;
import me.sonam.auth.service.HostOrganizationResolver;
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
    private final HostOrganizationResolver hostOrganizationResolver;
    private final String USERNAME_PAGE = "username";
    private final String EMAIL_ACCOUNT_ACTIVATE_LINK_PAGE = "account/active";

    public ForgotUsernameController(AccountWebClient accountWebClient, HostOrganizationResolver hostOrganizationResolver) {
        this.accountWebClient = accountWebClient;
        this.hostOrganizationResolver = hostOrganizationResolver;
    }

    @GetMapping("/loginHelp")
    public String getLoginHelp() {
        LOG.info("return login help page");
        return "loginHelp";
    }


    @GetMapping("/username")
    public String forgotUsername() {
        LOG.info("returning forgotUsername");
        return USERNAME_PAGE;
    }

    @PostMapping("/username")
    public Mono<String> emailUsername(String emailAddress, Model model) {
        LOG.info("email username for email: {}", emailAddress);

      return  accountWebClient.emailUsername(emailAddress).flatMap(s -> {
                LOG.info("add message attribute");
                model.addAttribute("message", "Your username has been sent to your email address.");
                return Mono.just(USERNAME_PAGE);
            }).onErrorResume(throwable -> {
                setErrorInModel(throwable,model, "failed to call email username account-rest-service: "+ throwable.getMessage());
                return Mono.just(USERNAME_PAGE);
        });
    }

    @GetMapping("/accounts/active")
    public String emailAccountActivateLink() {
        LOG.info("returning emailAccountActivateLink page");

        return EMAIL_ACCOUNT_ACTIVATE_LINK_PAGE;
    }

    @PostMapping("/accounts/active")
    public String handleEmailAccountActivateLink(String emailAddress, Model model) {
        LOG.info("send email account activate link if inactive");

        return accountWebClient.emailAccountActivationLink(emailAddress,
                hostOrganizationResolver.currentHost().orElse(null)).doOnNext(s -> {
            LOG.info("email sent");
            model.addAttribute("message", "email sent successfully, check your email");
        }).onErrorResume(throwable -> {
            setErrorInModel(throwable, model, "Failed to send email ");
            return Mono.just("/");
        }).thenReturn(EMAIL_ACCOUNT_ACTIVATE_LINK_PAGE).block();

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
