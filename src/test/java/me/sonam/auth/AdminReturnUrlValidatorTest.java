package me.sonam.auth;

import me.sonam.auth.util.AdminReturnUrlValidator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class AdminReturnUrlValidatorTest {
    private final AdminReturnUrlValidator validator = new AdminReturnUrlValidator(true);

    @Test
    void allowsLocalNextUiReturnUrl() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("summit.openissuer.com");

        assertThat(validator.validate("http://localhost:3000/admin/profile?tab=passkeys", request))
                .contains("http://localhost:3000/admin/profile?tab=passkeys");
    }

    @Test
    void rejectsUntrustedReturnUrl() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("summit.openissuer.com");

        assertThat(validator.validate("https://example.com/admin/profile", request)).isEmpty();
    }
}
