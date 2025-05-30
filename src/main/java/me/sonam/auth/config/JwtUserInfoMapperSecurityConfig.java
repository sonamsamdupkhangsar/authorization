package me.sonam.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer.authorizationServer;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class JwtUserInfoMapperSecurityConfig {

    private static final Logger LOG = LoggerFactory.getLogger(JwtUserInfoMapperSecurityConfig.class);

    @Autowired
    private OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer;

    @Value("${ISSUER_URI}")
    private String issuerUri;

    @Value("${allowedOrigins}")
    private String allowedOrigins; //csv allow origins


    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {

        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = authorizationServer();

        RequestMatcher endpointsMatcher = authorizationServerConfigurer
              //  .tokenGenerator(tokenGenerator())
                .getEndpointsMatcher();

        Function<OidcUserInfoAuthenticationContext, OidcUserInfo> userInfoMapper = (context) -> {
            OidcUserInfoAuthenticationToken authentication = context.getAuthentication();
            JwtAuthenticationToken principal = (JwtAuthenticationToken) authentication.getPrincipal();

            return new OidcUserInfo(principal.getToken().getClaims());
        };

        http.securityMatcher(endpointsMatcher)
                .with(authorizationServerConfigurer, (authorizationServer) ->
                        authorizationServer.oidc((oidc) -> oidc
                                .userInfoEndpoint((userInfo) -> userInfo
                                        .userInfoMapper(userInfoMapper)
                                ))
                )
                .authorizeHttpRequests((authorize) -> authorize
                        .anyRequest().authenticated()
                )
                .csrf(csrf -> csrf.ignoringRequestMatchers(endpointsMatcher))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(Customizer.withDefaults())
			)
			.exceptionHandling((exceptions) -> exceptions
                .defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)
                )
        );

        return http.cors(Customizer.withDefaults()).build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests((authorize) ->
                        authorize.requestMatchers("/api/health/liveness").permitAll()
                                .requestMatchers("/api/health/readiness").permitAll()
                                .requestMatchers("/username").permitAll()
                                .requestMatchers("/password/secret").permitAll()
                                .requestMatchers("/password").permitAll()
                                .requestMatchers("/accounts/active").permitAll()
                                .requestMatchers("/accounts/lock").permitAll()
                                .requestMatchers("/accounts/lock/secret").permitAll()
                                .requestMatchers("/accounts/lock/email").permitAll()
                                .requestMatchers("/accounts/lock/email/secret").permitAll()
                                .requestMatchers("/users/username").permitAll()


                .anyRequest().authenticated()
                )
                .csrf(httpSecurityCsrfConfigurer -> httpSecurityCsrfConfigurer.disable())
                .oauth2ResourceServer(httpSecurityOAuth2ResourceServerConfigurer ->
                        httpSecurityOAuth2ResourceServerConfigurer.jwt(Customizer.withDefaults()))
                .formLogin(httpSecurityFormLoginConfigurer ->
                        httpSecurityFormLoginConfigurer.loginPage("/")
                );

      return http.cors(Customizer.withDefaults()).formLogin(formLogin ->
              formLogin.loginPage("/").permitAll()).build();
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        KeyPair keyPair = generateRsaKey();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .build();
        JWKSet jwkSet = new JWKSet(rsaKey);
        return new ImmutableJWKSet<>(jwkSet);
    }

    private static KeyPair generateRsaKey() {
        KeyPair keyPair;
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            keyPair = keyPairGenerator.generateKeyPair();
        }
        catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        return keyPair;
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder().issuer(issuerUri).build();
    }

    @Bean
    public RequestCache requestCache() {
        return new HttpSessionRequestCache();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfig = new CorsConfiguration();

        allowedOrigins = allowedOrigins.replace(" ", ""); //remove whitespaces between csv
        List<String> list = Arrays.asList(allowedOrigins.split(","));
        LOG.info("adding allowedOrigins: {}", list);
        List<String> allowedOrigins = new ArrayList<>();

        for(String origin: list) {
            if (origin.equals("*")) {
                corsConfig.setAllowedOriginPatterns(List.of(origin));
            }
            else {
                allowedOrigins.add(origin);
            }
        }
        corsConfig.setAllowedOrigins(allowedOrigins);
        //corsConfig.addAllowedMethod("*");
        corsConfig.setAllowedMethods(Arrays.asList("GET", "PUT", "POST", "OPTIONS"));
        corsConfig.addAllowedHeader("*");
        corsConfig.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);
        return source;
    }

    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

   //@Bean
    OAuth2TokenGenerator<?> tokenGenerator() {
        JwtGenerator jwtGenerator = new JwtGenerator(new NimbusJwtEncoder(jwkSource()));
        LOG.info("tokenCustomizer: {}", tokenCustomizer);

        jwtGenerator.setJwtCustomizer(tokenCustomizer);

        OAuth2TokenGenerator<OAuth2RefreshToken> refreshTokenGenerator = new CustomRefreshTokenGenerator();
        return new DelegatingOAuth2TokenGenerator(jwtGenerator, refreshTokenGenerator);
    }

    private static final class CustomRefreshTokenGenerator implements OAuth2TokenGenerator<OAuth2RefreshToken> {
        private final OAuth2RefreshTokenGenerator delegate = new OAuth2RefreshTokenGenerator();

        @Nullable
        @Override
        public OAuth2RefreshToken generate(OAuth2TokenContext context) {
            if (context.getAuthorizedScopes().contains(OidcScopes.OPENID) &&
                    !context.getAuthorizedScopes().contains("offline_access")) {
                LOG.info("returning null for refresh token if not offline_access");
                return null;
            }
            return this.delegate.generate(context);
        }

    }
}

