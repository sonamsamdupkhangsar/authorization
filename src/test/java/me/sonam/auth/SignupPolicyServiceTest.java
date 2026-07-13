package me.sonam.auth;

import me.sonam.auth.config.SignupPolicyProperties;
import me.sonam.auth.service.HostOrganizationResolver;
import me.sonam.auth.service.SignupPolicyService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SignupPolicyServiceTest {

    @Test
    void environmentFallbackDisablesSignupForDemoHost() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("authorization-server.signup-policy.hosts.demo.openissuer.test.allow-signup", "false")
                .withProperty("authorization-server.signup-policy.hosts.demo.openissuer.test.create-organization-on-signup", "false");

        SignupPolicyService signupPolicyService = new SignupPolicyService(
                new SignupPolicyProperties(), mock(HostOrganizationResolver.class), environment);

        assertThat(signupPolicyService.validateSignupAllowedForHost("demo.openissuer.test"))
                .contains("signup is not allowed on this subdomain");
        assertThat(signupPolicyService.shouldCreateOrganizationOnSignupForHost("demo.openissuer.test"))
                .isFalse();
    }

    @Test
    void accountSelfServiceFlagDisablesSignupAndAccountHelpForDemoHost() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("authorization-server.signup-policy.hosts.demo.openissuer.test.allow-account-self-service", "false");

        SignupPolicyService signupPolicyService = new SignupPolicyService(
                new SignupPolicyProperties(), mock(HostOrganizationResolver.class), environment);

        assertThat(signupPolicyService.validateSignupAllowedForHost("demo.openissuer.test"))
                .contains("account self-service is disabled on this subdomain");
        assertThat(signupPolicyService.validateAccountSelfServiceAllowedForHost("demo.openissuer.test"))
                .contains("account self-service is disabled on this subdomain");
    }
}
