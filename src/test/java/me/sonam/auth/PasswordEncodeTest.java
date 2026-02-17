package me.sonam.auth;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PasswordEncodeTest {
    private static final Logger LOG = LoggerFactory.getLogger(PasswordEncodeTest.class);

    @Test
    public void passwordEncodeEmptyString() {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        LOG.error("encrypt empty string and see the result");
        final String password = "";
        String encodedPassword = passwordEncoder.encode(password);
        LOG.error("encoded password is {}", encodedPassword);

        boolean equals = passwordEncoder.matches("", encodedPassword);
        LOG.error("is the empty string equals to encodedPassword: {}", equals);
        assertFalse(equals);
    }

    @Test
    public void passwordEncodeSingleValueString() {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        LOG.error("encrypt string with single value");
        final String password = "1";
        String encodedPassword = passwordEncoder.encode(password);
        LOG.error("encoded 1 password is {}", encodedPassword);

        boolean equals = passwordEncoder.matches("1", encodedPassword);
        LOG.error("is the 1 string equals to encodedPassword: {}", equals);
        assertTrue(equals);
    }

}
