package me.sonam.auth.util;

import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class JwtUtil {
    public static Jwt jwt(String subjectName, UUID userId) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(10),
                Map.of("alg", "none"), Map.of("sub", subjectName, "userId", userId.toString()));
    }
    public static Jwt jwt(String subjectName) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(10),
                Map.of("alg", "none"), Map.of("sub", subjectName));
    }

    public static Consumer<HttpHeaders> addJwt(Jwt jwt) {
        return headers -> headers.setBearerAuth(jwt.getTokenValue());
    }

}
