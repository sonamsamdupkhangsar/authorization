package me.sonam.auth.mocks;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestConfiguration
public class TestSecurityConfig {

    @Bean
    @Primary // Ensure this mock bean takes precedence over the real one
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        ReactiveJwtDecoder mockDecoder = mock(ReactiveJwtDecoder.class);

        // Configure the mock to return a valid, pre-defined JWT when any token is decoded
        Jwt mockJwt = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .claim("sub", "testuser")
                .claim("scope", "read write")
                .build();

        when(mockDecoder.decode(anyString())).thenReturn(Mono.just(mockJwt));

        return mockDecoder;
    }
}
