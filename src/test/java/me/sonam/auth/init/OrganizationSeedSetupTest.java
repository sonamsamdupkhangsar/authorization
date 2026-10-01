package me.sonam.auth.init;

import me.sonam.auth.config.OrganizationSeedProperties;
import me.sonam.auth.rest.signup.UserSignup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrganizationSeedSetupTest {
    @Test
    void mapsSeedTenantHostIntoUserSignupPayload() {
        OrganizationSeedProperties.SeedUser seedUser = new OrganizationSeedProperties.SeedUser();
        seedUser.setFirstName("Test");
        seedUser.setLastName("User");
        seedUser.setEmail("sonamhava@gmail.com");
        seedUser.setAuthenticationId("sonamhava@gmail.com");
        seedUser.setOrganizationSubdomain("pr-test-4.openissuer.com");
        seedUser.setActive(false);

        UserSignup signup = OrganizationSeedSetup.toUserSignup(seedUser);

        assertThat(signup.getActivationHost()).isEqualTo("pr-test-4.openissuer.com");
        assertThat(signup.isActive()).isFalse();
    }
}
