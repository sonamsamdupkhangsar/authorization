package me.sonam.auth.config;

import me.sonam.auth.service.OidcUserInfoService;
import me.sonam.auth.util.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.util.Set;
import java.util.stream.Collectors;


@Configuration
public class IdTokenCustomizerConfig {
    private static final Logger LOG = LoggerFactory.getLogger(IdTokenCustomizerConfig.class);

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer(
            OidcUserInfoService userInfoService) {
        LOG.info("load user hello");

        return (context) -> {
            if (OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue())) {
                OidcUserInfo userInfo = userInfoService.loadUser(
                        context.getPrincipal().getName());
                LOG.info("userInfo: {}, principal.name: {}", userInfo, context.getPrincipal().getName());

                context.getClaims().claims(claims -> {
                    LOG.info("add all claims");
                    claims.putAll(userInfo.getClaims());
                });
            }
            else  if (context.getTokenType() == OAuth2TokenType.ACCESS_TOKEN) {
                LOG.info("principal.name: {}", context.getPrincipal().getName());

                if (context.getPrincipal() instanceof  UsernamePasswordAuthenticationToken) {
                    UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = context.getPrincipal();
                    UserId userId = (UserId) usernamePasswordAuthenticationToken.getPrincipal();

                    LOG.info("claims: {}", context.getClaims());
                    context.getClaims().claim("userId", userId.getUserId());
                }
                Set<String> authorities = context.getPrincipal().getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toSet());

                LOG.info("access token type, adding authorities: {}", authorities);
                authorities.forEach(role -> {
                    LOG.info("authority: {}",role );
                });
                if (!authorities.isEmpty()) {
                    LOG.info("add roles in claim map");
                    context.getClaims().claim("userRole", authorities);
                }


            }
        };
    }

}