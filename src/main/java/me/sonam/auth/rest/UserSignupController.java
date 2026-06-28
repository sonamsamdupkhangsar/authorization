package me.sonam.auth.rest;

import jakarta.validation.Valid;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.sonam.auth.rest.signup.Organization;
import me.sonam.auth.rest.signup.User;
import me.sonam.auth.rest.signup.UserSignup;
import me.sonam.auth.service.HostOrganizationResolver;
import me.sonam.auth.service.LoginReturnContextService;
import me.sonam.auth.service.SignupPolicyService;
import me.sonam.auth.webclient.OrganizationWebClient;
import me.sonam.auth.webclient.RoleWebClient;
import me.sonam.auth.webclient.UserWebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
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
    private RoleWebClient roleWebClient;
    @Autowired
    private SignupPolicyService signupPolicyService;
    @Autowired
    private HostOrganizationResolver hostOrganizationResolver;
    @Autowired
    private LoginReturnContextService loginReturnContextService;

    public UserSignupController(UserWebClient userWebClient) {
        this.userWebClient = userWebClient;
    }

    // Renders the signup form used by both the public signup subdomain and host-bound signup flows.
    @GetMapping
    public Mono<String> getSignupForm(Model model, HttpServletRequest request, HttpServletResponse response) {
        final String PATH = "signupform";
        LOG.info("returning {}", PATH);

        model.addAttribute("userSignup", new UserSignup());
        loginReturnContextService.addReturnContext(model, request, response);
        return Mono.just(PATH);
    }

    // Validates the current host policy, creates the user, and then either creates a new
    // organization or attaches the user to the organization already bound to the host.
    @PostMapping
    public Mono<String> signupUserFromForm(@Valid @ModelAttribute("userSignup") UserSignup userSignup,
                                           BindingResult bindingResult, Model model,
                                           HttpServletRequest request, HttpServletResponse response) {
        final String PATH = "signupform";
        final String currentHost = hostOrganizationResolver.currentHost().orElse(null);
        LOG.info("signing up user: {}", userSignup);
        loginReturnContextService.addReturnContext(model, request, response);

        if (bindingResult.hasErrors()) {
            LOG.info("user didn't enter required fields");
            model.addAttribute("error", "Data validation failed");
            return Mono.just(PATH);
        }

        var emailValidationError = signupPolicyService.validateEmailForHost(currentHost, userSignup.getEmail());
        if (emailValidationError.isPresent()) {
            model.addAttribute("error", emailValidationError.get());
            model.addAttribute("userSignup", userSignup);
            return Mono.just(PATH);
        }

        if (!StringUtils.hasText(currentHost)) {
            model.addAttribute("error", "signup must be performed from a tenant subdomain");
            model.addAttribute("userSignup", userSignup);
            return Mono.just(PATH);
        }

        userSignup.setActivationHost(currentHost);
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
                    if (!signupPolicyService.shouldCreateOrganizationOnSignupForHost(currentHost)) {
                        return attachUserToHostOrganization(user, currentHost).thenReturn(PATH);
                    }

                    String name = userSignup.getOrganization();
                    if (name == null || name.isEmpty()) {
                        name = userSignup.getFirstName() + " " + userSignup.getLastName() + " Company";
                    }
                    Organization org = new Organization(null, name, user.getId());
                    LOG.info("create organization for user in signup: {}", org);
                    org.setDefaultOrganization(true);

                    //create organization and add this user to it
                    return organizationWebClient.updateOrganization(org, HttpMethod.POST)
                            .flatMap(createdOrganization -> organizationWebClient.addOrganizationToSubdomain(currentHost, createdOrganization.getId())
                                    .then(organizationWebClient.setDefaultOrganization(createdOrganization.getId(), user.getId()))
                                    .then(roleWebClient.setUserAsRoleNameForOrganization(null,
                                            "OrgAdmin", user.getId(), createdOrganization.getId()))
                                    .thenReturn(PATH));
                })
                .onErrorResume(throwable -> {
                                setErrorInModel(throwable, model, "failed to signup user");
                                model.addAttribute("userSignup", userSignup);
                                return Mono.just(PATH);
                });
    }

    // Uses the current signup host as the source of truth and adds the new user to that host's
    // existing organization instead of creating another organization.
    private Mono<Void> attachUserToHostOrganization(User user, String host) {
        LOG.info("attach user to host organization for host-bound signup flow, user: {}, host: {}", user, host);
        if (host == null) {
            return Mono.error(new IllegalStateException("No current host found for signup"));
        }

        return organizationWebClient.getOrganizationIdBySubdomain(host)
                .switchIfEmpty(Mono.error(new IllegalStateException("No organization bound to current host")))
                .flatMap(organizationId -> organizationWebClient.addUserToOrganization(user.getId(), organizationId, host, true)
                        .then(organizationWebClient.setDefaultOrganization(organizationId, user.getId()))
                        .then());
    }


    // Unwraps downstream WebClient errors into a user-facing signup error message in the model.
    private void setErrorInModel(Throwable throwable, Model model, String defaultErrMessage) {
        LOG.error("exception occurred in signup user", throwable);
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
