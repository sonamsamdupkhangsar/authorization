package me.sonam.auth.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.util.Pair;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public class UserIdUtil {
    private static final Logger LOG = LoggerFactory.getLogger(UserId.class);

    public static Pair<UUID, String> getLoggedInUserId() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String userIdString = jwt.getClaim("userId");
        LOG.info("userId: {}", userIdString);
        return Pair.of(UUID.fromString(userIdString), jwt.getTokenValue());
    }
}
