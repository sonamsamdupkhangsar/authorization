package me.sonam.auth.config;

import me.sonam.auth.webclient.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class BeanConfig {

    private static final Logger LOG = LoggerFactory.getLogger(BeanConfig.class);

    @Value("${account-rest-service.emailActivateLink}")
    private String emailActiveLink;

    @Value("${account-rest-service.emailMySecret}")
    private String emailMySecret;
    @Value("${account-rest-service.emailUsername}")
    private String emailUsername;
    @Value("${account-rest-service.validateEmailLoginSecret}")
    private String validateEmailLoginSecret;
    @Value("${account-rest-service.updatePassword}")
    private String updatePassword;
    @Value("${account-rest-service.emailSecretUnlockAccount}")
    private String emailSecretUnlockAccount;
    @Value("${account-rest-service.lockAccount}")
    private String lockAccount;
    @Value("${account-rest-service.unLockAccount}")
    private String unLockAccount;
    @Value("${account-rest-service.isAccountLocked}")
    private String isAccountLockedEndpoint;

    @Value("${account-rest-service.unLockAccountTimeExpire}")
    private String unLockAccountTimeExpireEndpoint;

    @Value("${authentication-rest-service.verifyPassword}")
    private String verifyPasswordEndpoint;

    @Value("${authentication-rest-service.authenticationId}")
    private String authenticateIdCheckEndpoint;

    @Value("${attempt-rest-service.success}")
    private String loginAttemptSuccess;

    @Value("${attempt-rest-service.failed}")
    private String loginAttemptFail;

    @Value("${attempt-rest-service.delete}")
    private String deleteAttempt;

    @Value("${attempt-rest-service.check}")
    private String checkLoginAttempt;


    @Value("${organization-rest-service.root}${organization-rest-service.userExistsInOrganization}")
    private String userExistsInOrganizationEndpoint;
    @Value("${organization-rest-service.root}${organization-rest-service.organizationBySubdomain}")
    private String organizationBySubdomainEndpoint;

    @Value("${organization-rest-service.context}")
    private String organizationEndpoint;


    @Value("${user-rest-service.userByAuthId}")
    private String userByAuthIdEp;

    @Value("${user-rest-service.context}")
    private String userEndpoint;

    @Value("${role-rest-service.context}")
    private String roleEndpoint;

    @Value("${setting-rest-service.users}")
    private String userSettingEndpoint;

    @Value("${setting-rest-service.defaultOrganization}")
    private String defaultOrganizationSettingEndpoint;

    @Value("${authIdNotExist}")
    private String authIdNotExist;

    @Value("${authNotActive}")
    private String authNotActive;

    @Value("${authPasswordNotSet}")
    private String authPasswordNotSet;

    @Autowired
    @Qualifier("serviceWebClientBuilder")
    private WebClient.Builder webClientBuilder;


    @Bean
    public PasswordEncoder encoder() {
        return new BCryptPasswordEncoder();
    }


    @Bean
    public UserWebClient userWebClient() {
        return new UserWebClient(webClientBuilder, userByAuthIdEp, userEndpoint);
    }


    @Bean
    public AccountWebClient accountWebClient() {
        return new AccountWebClient(webClientBuilder, emailUsername, emailMySecret, emailActiveLink,
                validateEmailLoginSecret, updatePassword,  emailSecretUnlockAccount, lockAccount,
                unLockAccount, isAccountLockedEndpoint, unLockAccountTimeExpireEndpoint);
    }

    @Bean
    public AuthenticationWebClient authenticationWebClient() {
        return new AuthenticationWebClient(webClientBuilder, verifyPasswordEndpoint, authenticateIdCheckEndpoint,
                authIdNotExist,
                authNotActive, authPasswordNotSet);
    }

    @Bean
    public LoginAttemptWebClient loginAttemptWebClient() {
        return new LoginAttemptWebClient(webClientBuilder, loginAttemptFail, loginAttemptSuccess, accountWebClient(), deleteAttempt, checkLoginAttempt);
    }

    @Bean
    public OrganizationWebClient organizationWebClient() {
        return new OrganizationWebClient(webClientBuilder, organizationEndpoint,
                userExistsInOrganizationEndpoint, organizationBySubdomainEndpoint);
    }

    @Bean
    public RoleWebClient roleWebClient() {
        return new RoleWebClient(webClientBuilder, roleEndpoint);
    }

    @Bean
    public SettingWebClient settingWebClient() {
        return new SettingWebClient(webClientBuilder, userSettingEndpoint, defaultOrganizationSettingEndpoint);
    }

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(1);
        taskScheduler.setThreadNamePrefix("organization-seed-");
        taskScheduler.initialize();
        return taskScheduler;
    }
}
