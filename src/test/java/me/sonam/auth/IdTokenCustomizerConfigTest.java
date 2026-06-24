package me.sonam.auth;

import me.sonam.auth.config.IdTokenCustomizerConfig;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class IdTokenCustomizerConfigTest {
    @Test
    void splitsApplicationRolesFromAuthenticationFactors() {
        Set<String> authorities = Set.of("admin", "FACTOR_PASSWORD", "FACTOR_WEBAUTHN");

        assertThat(IdTokenCustomizerConfig.userRoles(authorities))
                .containsExactly("admin");
        assertThat(IdTokenCustomizerConfig.authFactors(authorities))
                .containsExactlyInAnyOrder("FACTOR_PASSWORD", "FACTOR_WEBAUTHN");
    }
}
