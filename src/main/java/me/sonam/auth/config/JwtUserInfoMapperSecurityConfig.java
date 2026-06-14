package me.sonam.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTParser;
import jakarta.annotation.Nullable;
import me.sonam.auth.multitenancy.TenantPerHostComponentRegistry;
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
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.token.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.ui.DefaultResourcesFilter;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.security.web.webauthn.registration.PublicKeyCredentialCreationOptionsFilter;
import org.springframework.security.web.webauthn.registration.WebAuthnRegistrationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URI;
import java.text.ParseException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
//import static org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer.authorizationServer;
//import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
//import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
//import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;


@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class JwtUserInfoMapperSecurityConfig {

    private static final Logger LOG = LoggerFactory.getLogger(JwtUserInfoMapperSecurityConfig.class);

    @Autowired
    private OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer;

    @Value("${allowedOrigins}")
    private String allowedOrigins; //csv allow origins

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity httpSecurity) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                new OAuth2AuthorizationServerConfigurer();
        return
             httpSecurity.with(authorizationServerConfigurer, Customizer.withDefaults())
         .oauth2AuthorizationServer(oAuth2AuthorizationServerConfigurer -> {
            RequestMatcher endpointsMatcher = oAuth2AuthorizationServerConfigurer.getEndpointsMatcher();

            Function<OidcUserInfoAuthenticationContext, OidcUserInfo> userInfoMapper = (context) -> {
                OidcUserInfoAuthenticationToken authentication = context.getAuthentication();
                JwtAuthenticationToken principal = (JwtAuthenticationToken) authentication.getPrincipal();

                return new OidcUserInfo(principal.getToken().getClaims());
            };

            securityMatcher( httpSecurity,  endpointsMatcher, oAuth2AuthorizationServerConfigurer, userInfoMapper);
        }).cors(Customizer.withDefaults()).build();
    }

    private void securityMatcher(HttpSecurity httpSecurity, RequestMatcher endpointsMatcher,
                                 OAuth2AuthorizationServerConfigurer authorizationServerConfigurer,
                                 Function<OidcUserInfoAuthenticationContext, OidcUserInfo> userInfoMapper) {
        httpSecurity.securityMatcher(endpointsMatcher)
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
    }

    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http,
                                                          UserCredentialRepository userCredentialRepository,
                                                          WebAuthnRelyingPartyOperations webAuthnRelyingPartyOperations)
            throws Exception {
        PublicKeyCredentialCreationOptionsFilter passkeyOptionsFilter =
                new PublicKeyCredentialCreationOptionsFilter(webAuthnRelyingPartyOperations);
        WebAuthnRegistrationFilter passkeyRegistrationFilter =
                new WebAuthnRegistrationFilter(userCredentialRepository, webAuthnRelyingPartyOperations);

        http
                .authorizeHttpRequests((authorize) ->
                        authorize.requestMatchers("/api/health/liveness").permitAll()
                                .requestMatchers("/api/health/readiness").permitAll()
                                .requestMatchers("/favicon.ico").permitAll()
                                .requestMatchers("/favicon.svg").permitAll()
                                .requestMatchers("/username").permitAll()
                                .requestMatchers("/password/secret").permitAll()
                                .requestMatchers("/password").permitAll()
                                .requestMatchers("/accounts/active").permitAll()
                                .requestMatchers("/accounts/lock").permitAll()
                                .requestMatchers("/accounts/lock/secret").permitAll()
                                .requestMatchers("/accounts/lock/email").permitAll()
                                .requestMatchers("/accounts/lock/email/secret").permitAll()
                                .requestMatchers("/users/username").permitAll()
                                .requestMatchers("/signup").permitAll()


                .anyRequest().authenticated()
                )
                .csrf(httpSecurityCsrfConfigurer -> httpSecurityCsrfConfigurer.disable())
                .oauth2ResourceServer(httpSecurityOAuth2ResourceServerConfigurer ->
                        httpSecurityOAuth2ResourceServerConfigurer.jwt(Customizer.withDefaults()))
                .formLogin(httpSecurityFormLoginConfigurer ->
                        httpSecurityFormLoginConfigurer.loginPage("/")
                )
                .addFilter(DefaultResourcesFilter.webauthn())
                .addFilterBefore(passkeyOptionsFilter, AuthorizationFilter.class)
                .addFilterAfter(passkeyRegistrationFilter, AuthorizationFilter.class);

      return http.cors(Customizer.withDefaults()).formLogin(formLogin ->
              formLogin.loginPage("/").permitAll()).build();
    }

    @Bean
    public JwtDecoder jwtDecoder(TenantPerHostComponentRegistry componentRegistry) {
        return new TenantAwareJwtDecoder(componentRegistry);
    }

    private static final class TenantAwareJwtDecoder implements JwtDecoder {
        private final TenantPerHostComponentRegistry componentRegistry;
        private final Map<String, JwtDecoder> decodersByIssuer = new ConcurrentHashMap<>();

        private TenantAwareJwtDecoder(TenantPerHostComponentRegistry componentRegistry) {
            this.componentRegistry = componentRegistry;
        }

        @Override
        public Jwt decode(String token) throws JwtException {
            String issuer = issuer(token);
            return decodersByIssuer.computeIfAbsent(issuer, this::decoderForIssuer).decode(token);
        }

        private String issuer(String token) {
            try {
                String issuer = JWTParser.parse(token).getJWTClaimsSet().getIssuer();
                if (issuer == null || issuer.isBlank()) {
                    throw new JwtException("missing issuer claim");
                }
                return issuer;
            }
            catch (ParseException e) {
                throw new JwtException("failed to parse jwt issuer", e);
            }
        }

        private JwtDecoder decoderForIssuer(String issuer) {
            String host = URI.create(issuer).getHost();
            JWKSet jwkSet = host == null ? null : componentRegistry.get(host, JWKSet.class);
            if (jwkSet == null) {
                throw new JwtException("JWKSet not found for token issuer: " + issuer);
            }

            NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSource(
                    (jwkSelector, context) -> jwkSelector.select(jwkSet)).build();
            jwtDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
            return jwtDecoder;
        }
    }

    @Bean
    public RequestCache requestCache() {
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.setRequestMatcher(request -> {
            String uri = request.getRequestURI();
            return !"/favicon.ico".equals(uri) && !"/favicon.svg".equals(uri);
        });
        return requestCache;
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
        return null;
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
