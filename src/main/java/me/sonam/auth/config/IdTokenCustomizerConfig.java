package me.sonam.auth.config;

import me.sonam.auth.service.OidcUserInfoService;
import me.sonam.auth.util.UserId;
import me.sonam.auth.webclient.RoleWebClient;
import me.sonam.auth.webclient.SettingWebClient;
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
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.util.Set;
import java.net.URI;
import java.util.stream.Collectors;


@Configuration
public class IdTokenCustomizerConfig {
    private static final Logger LOG = LoggerFactory.getLogger(IdTokenCustomizerConfig.class);

    private final SettingWebClient settingWebClient;

    public IdTokenCustomizerConfig(SettingWebClient settingWebClient) {
        this.settingWebClient = settingWebClient;
    }

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
                    LOG.info("add all claims: {}", userInfo.getClaims());
                    claims.putAll(userInfo.getClaims());
                    claims.put("tenant_id", currentHost());
                    claims.put("profile", "");
                    if (userInfo.getPicture() != null && !userInfo.getPicture().isEmpty()) {
                        LOG.debug("set picture {}", userInfo.getPicture());
                        claims.put("picture", userInfo.getPicture());
                    }
                    else {
                        LOG.debug("missing picture");
                        claims.put("picture", "");
                    }
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
                context.getClaims().claim("tenant_id", currentHost());


            }
        };
    }

    private String currentHost() {
        if (AuthorizationServerContextHolder.getContext() == null || AuthorizationServerContextHolder.getContext().getIssuer() == null) {
            return "default";
        }
        return URI.create(AuthorizationServerContextHolder.getContext().getIssuer()).getHost();
    }

}
