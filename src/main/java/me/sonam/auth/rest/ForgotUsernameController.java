package me.sonam.auth.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.sonam.auth.service.LoginReturnContextService;
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
    private final LoginReturnContextService loginReturnContextService;
    private final String USERNAME_PAGE = "username";
    private final String EMAIL_ACCOUNT_ACTIVATE_LINK_PAGE = "account/active";

    public ForgotUsernameController(AccountWebClient accountWebClient, HostOrganizationResolver hostOrganizationResolver,
                                    LoginReturnContextService loginReturnContextService) {
        this.accountWebClient = accountWebClient;
        this.hostOrganizationResolver = hostOrganizationResolver;
        this.loginReturnContextService = loginReturnContextService;
    }

    @GetMapping("/loginHelp")
    public String getLoginHelp(Model model, HttpServletRequest request, HttpServletResponse response) {
        LOG.info("return login help page");
        loginReturnContextService.addReturnContext(model, request, response);
        return "loginHelp";
    }


    @GetMapping("/username")
    public String forgotUsername(Model model, HttpServletRequest request, HttpServletResponse response) {
        LOG.info("returning forgotUsername");
        loginReturnContextService.addReturnContext(model, request, response);
        return USERNAME_PAGE;
    }

    @PostMapping("/username")
    public Mono<String> emailUsername(String emailAddress, Model model,
                                      HttpServletRequest request, HttpServletResponse response) {
        LOG.info("forgot-username email requested");
        loginReturnContextService.addReturnContext(model, request, response);

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
    public String emailAccountActivateLink(Model model, HttpServletRequest request, HttpServletResponse response) {
        LOG.info("returning emailAccountActivateLink page");
        loginReturnContextService.addReturnContext(model, request, response);

        return EMAIL_ACCOUNT_ACTIVATE_LINK_PAGE;
    }

    @PostMapping("/accounts/active")
    public String handleEmailAccountActivateLink(String emailAddress, Model model,
                                                HttpServletRequest request, HttpServletResponse response) {
        LOG.info("send email account activate link if inactive");
        loginReturnContextService.addReturnContext(model, request, response);

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
