package me.sonam.auth;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class RegExpTest {
    private static final Logger LOG = LoggerFactory.getLogger(RegExpTest.class);

    @Test
    public void urlEncodedEmailPath() {
        final String path = "/accounts/email/testuser%40sonamemail/password-secret";

        final String exp = "/accounts/email/(.)*/password-secret";
        boolean matchOutPath = path.matches(exp);

        LOG.info("matchOutput is {}, path {}, exp {}", matchOutPath, path, exp);
        assertThat(matchOutPath).isTrue();
    }

    @Test
    public void httpAddressStringMatch() {
        final String path = "http://localhost:9001/issuer/forgotPassword";
        final String exp = ".*/forgotPassword";
        boolean matchOutPath = path.matches(exp);

        LOG.info("forgotPassword matches with full httpAddress? is {}, path {}, exp {}", matchOutPath, path, exp);
        assertThat(matchOutPath).isTrue();
    }

    @Test
    public void parseUuidToString() {
        final String uuidString = "7d05b15a-1c61-4b61-b875-27fdd0a526a3";
        UUID uuid = UUID.fromString(uuidString);
        LOG.error("uuid is {}", uuid);
        assertThat(uuid.toString()).isEqualTo("7d05b15a-1c61-4b61-b875-27fdd0a526a3");
    }

}
