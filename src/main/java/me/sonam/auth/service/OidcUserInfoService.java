package me.sonam.auth.service;


import me.sonam.auth.service.exception.BadCredentialsException;
import me.sonam.auth.webclient.UserWebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * This will perform a user info lookup by calling user-rest-service with authenticationId {@code id_token}.
 */
@Service
public class OidcUserInfoService {
    private static final Logger LOG = LoggerFactory.getLogger(OidcUserInfoService.class);
    private UserWebClient userWebClient;

    public OidcUserInfoService(UserWebClient userWebClient) {
        this.userWebClient = userWebClient;
    }

    public OidcUserInfo loadUser(String username) {
        LOG.info("loadUser by username: {}", username);

        try {
            OidcUserInfo oidcUserInfo = getOidcUserInfoMap(username).flatMap(map ->
                    Mono.just(new OidcUserInfo(map))).block();

            LOG.info("oidcUserInfo.claims: {}, oidcUserInfo: {}", oidcUserInfo.getClaims(), oidcUserInfo);
            return oidcUserInfo;
        }
        catch (Exception e) {
            LOG.error("failed to build oidcUserInfo");
            throw new BadCredentialsException("failed to get oidcUserInfo, maybe the user does not exist with authenticationId: "+ username);
        }
    }

    private Mono<Map<String, Object>> getOidcUserInfoMap(String authenticationId) {

        return userWebClient.getUserByAuthenticationId(authenticationId).flatMap(stringStringMap -> {
            LOG.info("got userInfo from user-rest-service: {}", stringStringMap);
            Map<String, Object> oidcUserInfoMap = buildOidcUserInfo(authenticationId, stringStringMap);
            return Mono.just(oidcUserInfoMap);
        }).onErrorResume(throwable -> {
            LOG.error("failed to get user by authentication identifier");
            LOG.debug("exception occurred in get user by authenticationId", throwable);

            return Mono.error(throwable);
        });
    }

    private static Map<String, Object> buildOidcUserInfo(String authenticationId, Map<String, String> map) {
         OidcUserInfo.Builder builder = OidcUserInfo.builder();

         builder.subject(authenticationId)
                 .name(map.get("firstName"))
                .givenName(map.get("firstName"))
                .familyName(map.get("lastName"))
                .middleName(map.get("middleName"))
                .nickname(map.get("nickname"))
                .preferredUsername(authenticationId)
                .profile(map.get("profile"))
                .picture(map.get("profilePhoto"))
                .website(map.get("website"))
                 .email(map.get("email"))
                .emailVerified(Boolean.parseBoolean(map.get("emailVerified")))
                .claim("userId", map.get("id"))
                .gender(map.get("gender"))
                .birthdate(map.get("dateOfBirth"))
                .zoneinfo(map.get("timeZone"))
                .locale(map.get("locale"))
                .phoneNumber(map.get("phoneNumber"))
                .phoneNumberVerified(Boolean.parseBoolean(map.get("phoneNumberVerified")))
                .claim("address", map.get("address"))
                .updatedAt(map.get("updatedAt"))
                .build();

         return builder.build().getClaims();
    }
}
