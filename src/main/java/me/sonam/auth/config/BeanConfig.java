package me.sonam.auth.config;

import me.sonam.auth.webclient.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class BeanConfig {

    private static final Logger LOG = LoggerFactory.getLogger(BeanConfig.class);

    @Value("${account-rest-service.root}${account-rest-service.context}${account-rest-service.emailActivateLink}")
    private String emailActiveLink;

    @Value("${account-rest-service.root}${account-rest-service.context}${account-rest-service.emailMySecret}")
    private String emailMySecret;
    @Value("${account-rest-service.root}${account-rest-service.context}${account-rest-service.emailUsername}")
    private String emailUsername;
    @Value("${account-rest-service.root}${account-rest-service.context}${account-rest-service.validateEmailLoginSecret}")
    private String validateEmailLoginSecret;
    @Value("${account-rest-service.root}${account-rest-service.context}${account-rest-service.updatePassword}")
    private String updatePassword;
    @Value("${account-rest-service.root}${account-rest-service.context}${account-rest-service.emailSecretUnlockAccount}")
    private String emailSecretUnlockAccount;
    @Value("${account-rest-service.root}${account-rest-service.context}${account-rest-service.lockAccount}")
    private String lockAccount;
    @Value("${account-rest-service.root}${account-rest-service.context}${account-rest-service.unLockAccount}")
    private String unLockAccount;
    @Value("${account-rest-service.root}${account-rest-service.context}${account-rest-service.isAccountLocked}")
    private String isAccountLockedEndpoint;

    @Value("${authentication-rest-service.root}${authentication-rest-service.authenticate}")
    private String authenticateEndpoint;

    @Value("${attempt-rest-service.root}${attempt-rest-service.context}${attempt-rest-service.success}")
    private String loginAttemptSuccess;

    @Value("${attempt-rest-service.root}${attempt-rest-service.context}${attempt-rest-service.failed}")
    private String loginAttemptFail;

    @Value("${attempt-rest-service.root}${attempt-rest-service.context}${attempt-rest-service.delete}")
    private String deleteAttempt;


    @Value("${organization-rest-service.root}${organization-rest-service.userExistsInOrganization}")
    private String organizationEndpoint;

    @Value("${user-rest-service.root}${user-rest-service.userByAuthId}")
    private String userByAuthIdEp;

    @Value("${user-rest-service.root}${user-rest-service.userByAuthId}")
    private String userEndpoint;

    @Autowired
    private WebClient.Builder webClientBuilder;


    @Bean
    public PasswordEncoder encoder() {
        return new BCryptPasswordEncoder();
    }


    @Bean
    public UserWebClient userWebClient() {
        return new UserWebClient(webClientBuilder, userByAuthIdEp);
    }


    @Bean
    public AccountWebClient accountWebClient() {
        return new AccountWebClient(webClientBuilder, emailUsername, emailMySecret, emailActiveLink,
                validateEmailLoginSecret, updatePassword,  emailSecretUnlockAccount, lockAccount,
                unLockAccount, isAccountLockedEndpoint);
    }

    @Bean
    public AuthenticationWebClient authenticationWebClient() {
        return new AuthenticationWebClient(webClientBuilder, authenticateEndpoint, loginAttemptWebClient());
    }

    @Bean
    public LoginAttemptWebClient loginAttemptWebClient() {
        return new LoginAttemptWebClient(webClientBuilder, loginAttemptFail, loginAttemptSuccess, accountWebClient(), deleteAttempt);
    }

    @Bean
    public OrganizationWebClient organizationWebClient() {
        return new OrganizationWebClient(webClientBuilder, organizationEndpoint);
    }
}
