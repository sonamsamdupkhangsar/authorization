package me.sonam.auth.rest;

import jakarta.validation.Valid;

import me.sonam.auth.rest.signup.Organization;
import me.sonam.auth.rest.signup.UserSignup;
import me.sonam.auth.webclient.OrganizationWebClient;
import me.sonam.auth.webclient.SettingWebClient;
import me.sonam.auth.webclient.UserWebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * This controller is for signing up user using the user-rest-service and other microservices
 */
@Controller
@RequestMapping("/signup")
public class UserSignupController {
    private static final Logger LOG = LoggerFactory.getLogger(UserSignupController.class);
    @Autowired
    private final UserWebClient userWebClient;

    @Autowired
    private OrganizationWebClient organizationWebClient;

    @Autowired
    private SettingWebClient settingWebClient;

    public UserSignupController(UserWebClient userWebClient) {
        this.userWebClient = userWebClient;
    }
    @GetMapping
    public Mono<String> getSignupForm(Model model) {
        final String PATH = "signupform";
        LOG.info("returning {}", PATH);

        model.addAttribute("userSignup", new UserSignup());
        return Mono.just(PATH);
    }

    @PostMapping
    public Mono<String> signupUserFromForm(@Valid @ModelAttribute("userSignup") UserSignup userSignup,
                                           BindingResult bindingResult, Model model) {
        final String PATH = "signupform";
        LOG.info("signing up user: {}", userSignup);

        if (bindingResult.hasErrors()) {
            LOG.info("user didn't enter required fields");
            model.addAttribute("error", "Data validation failed");
            return Mono.just(PATH);
        }
        return  userWebClient.signupUser(userSignup)
                .flatMap(s -> {

                    LOG.info("user signup successful with message: {}",s);
                    StringBuilder stringBuilder = new StringBuilder(userSignup.getFirstName())
                            .append(", your signup was successful!").append(
                                    " Please check your email '").append(userSignup.getEmail())
                            .append("' to activate your account.");

                    model.addAttribute("message", stringBuilder.toString());
                    return Mono.just(PATH);
                })
                .flatMap(s ->  userWebClient.findByAuthenticationId(userSignup.getAuthenticationId()))
                .flatMap(user -> {
                    String name = userSignup.getOrganization();
                    if (name == null || name.isEmpty()) {
                        name = userSignup.getFirstName() + " " + userSignup.getLastName() + " Company";
                    }
                    Organization org = new Organization(null, name, user.getId());
                    LOG.info("create organization user signup: {}", org);
                    org.setDefaultOrganization(true);

                    //create organization and add this user to it
                    return organizationWebClient.updateOrganization(org, HttpMethod.POST);
                })
                .flatMap(organization -> settingWebClient.addDefaultOrganization(null, organization.getId()))
                .thenReturn(PATH)
                .onErrorResume(throwable -> {
                                setErrorInModel(throwable, model, "failed to signup user");
                                model.addAttribute("userSignup", userSignup);
                                return Mono.just(PATH);
                });
    }


    private void setErrorInModel(Throwable throwable, Model model, String defaultErrMessage) {
        LOG.error("exception occured in signup user", throwable);
        LOG.error(defaultErrMessage);

        if (throwable instanceof WebClientResponseException webClientResponseException) {
            Map<String, String> map = webClientResponseException.getResponseBodyAs(
                    new ParameterizedTypeReference<>() {});

            if (map != null) {
                LOG.error("{}: {}", defaultErrMessage, map.get("error"));

                if (map.get("error") != null) {
                    model.addAttribute("error", map.get("error"));
                }
                else {
                    LOG.error("there is no error key in the map, add map itself to error message");
                    model.addAttribute("error", map);
                }
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
