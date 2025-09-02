package me.sonam.auth.service;

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
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Map;
@Service
public class UserSignupService {
    private static final Logger LOG = LoggerFactory.getLogger(UserSignupService.class);

    @Autowired
    private final UserWebClient userWebClient;

    @Autowired
    private OrganizationWebClient organizationWebClient;

    @Autowired
    private SettingWebClient settingWebClient;

    public UserSignupService(UserWebClient userWebClient) {
        this.userWebClient = userWebClient;
    }

    public Mono<String> signupUser(UserSignup userSignup) {
        return  userWebClient.signupUser( userSignup)
                .doOnNext(s -> {

                    LOG.info("user signup successful with message: {}",s);
                    StringBuilder stringBuilder = new StringBuilder(userSignup.getFirstName())
                            .append(", your signup was successful!").append(
                                    " Please check your email '").append(userSignup.getEmail())
                            .append("' to activate your account.");

                    LOG.info("default user signup success {}", stringBuilder);
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
                }).
                flatMap(organization -> settingWebClient.addDefaultOrganization(null, organization.getId()))
                .thenReturn("user signedup");
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
