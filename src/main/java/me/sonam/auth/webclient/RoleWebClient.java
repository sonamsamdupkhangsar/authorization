package me.sonam.auth.webclient;

import jakarta.ws.rs.BadRequestException;
import org.apache.tomcat.websocket.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RoleWebClient {
    private static final Logger LOG = LoggerFactory.getLogger(RoleWebClient.class);
    private final String roleEndpoint;
    private final WebClient.Builder webClientBuilder;

    public RoleWebClient(WebClient.Builder webClientBuilder, String roleEndpoint) {
        this.webClientBuilder = webClientBuilder;
        this.roleEndpoint = roleEndpoint;
    }

    public Mono<List<UUID>> getOrgAdminOrgIds(String accessToken, int page, int size) {
        LOG.info("get orgAdmin organizations for this user in accessToken");

        final StringBuilder stringBuilder = new StringBuilder(roleEndpoint);
        stringBuilder.append("/authzmanagerroles/users/organizations?page="+ page + "&size="+size);
        LOG.info("get org admin organizations endpoint: {}", stringBuilder);

        WebClient.ResponseSpec responseSpec = webClientBuilder.build().get().uri(stringBuilder.toString())
                .headers(httpHeaders -> httpHeaders.setBearerAuth(accessToken))
                .retrieve();
        return responseSpec.bodyToMono(new ParameterizedTypeReference<List<UUID>>() {});
    }

    public Mono<Integer> getOrgAdminOrgIdsCount(String accessToken) {
        LOG.info("get orgAdmin organizations count for this user in accessToken");

        final StringBuilder stringBuilder = new StringBuilder(roleEndpoint);
        stringBuilder.append("/authzmanagerroles/users/organizations/count");
        LOG.info("get org admin organizations endpoint: {}", stringBuilder);

        return webClientBuilder.build().get().uri(stringBuilder.toString())
                .headers(httpHeaders -> httpHeaders.setBearerAuth(accessToken))
                .retrieve().bodyToMono(new ParameterizedTypeReference<Map<String, Integer>>() {})
                .flatMap(stringIntegerMap -> {
                    LOG.info("got result for count of organizations {}", stringIntegerMap);
                    return Mono.just(stringIntegerMap.get("message"));
                }).onErrorResume(throwable -> {
                    LOG.error("error occured when getting count of organizations for logged-in user", throwable);
                    return Mono.just(0);
                });

    }

    public Mono<UUID> setUserAsRoleNameForOrganization(String accessToken, final String authzManagerRoleName, UUID userId, UUID organizationId) {
        LOG.info("set authzManagerRoleName for organization and userId");

        final StringBuilder stringBuilder = new StringBuilder(roleEndpoint);
        stringBuilder.append("/authzmanagerroles/names/users/organizations");
        LOG.info("set user as OrgAdmin role for orgId using endpoint: {}", stringBuilder);

        WebClient.RequestBodySpec requestBodySpec = webClientBuilder.build().post().uri(stringBuilder.toString());
        if (accessToken != null) {
            requestBodySpec.headers(httpHeaders -> httpHeaders.setBearerAuth(accessToken));
        }

        return requestBodySpec.bodyValue(Map.of("userId", userId, "organizationId", organizationId,
                        "authzManagerRoleName", authzManagerRoleName))
                .retrieve().bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .flatMap(map -> {
                    LOG.info("user set as authzManagerRoleName: {}", map);
                    if (map.get("id") != null) {
                        UUID id = UUID.fromString(map.get("id").toString());

                        return Mono.just(id);
                    }
                    else {
                        return Mono.error(new AuthenticationException("No id found"));
                    }
                }).onErrorResume(throwable -> {
                    LOG.error("error occurred when setting user as authzManagerRoleName for orgId", throwable);
                    return Mono.error(throwable);
                });
    }

    public Mono<UUID> setUserAsRoleNameForSubdomain(String accessToken, final String authzManagerRoleName, UUID userId,
                                                    UUID subdomainId) {
        LOG.info("set authzManagerRoleName for subdomain and userId");

        final StringBuilder stringBuilder = new StringBuilder(roleEndpoint);
        stringBuilder.append("/authzmanagerroles/names/users/subdomains");
        LOG.info("set user as SubdomainAdmin role for subdomainId using endpoint: {}", stringBuilder);

        WebClient.RequestBodySpec requestBodySpec = webClientBuilder.build().post().uri(stringBuilder.toString());
        if (accessToken != null) {
            requestBodySpec.headers(httpHeaders -> httpHeaders.setBearerAuth(accessToken));
        }

        return requestBodySpec.bodyValue(Map.of("userId", userId, "subdomainId", subdomainId,
                        "authzManagerRoleName", authzManagerRoleName))
                .retrieve().bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .flatMap(map -> {
                    LOG.info("user set as authzManagerRoleName for subdomain: {}", map);
                    if (map.get("id") != null) {
                        return Mono.just(UUID.fromString(map.get("id").toString()));
                    }
                    return Mono.error(new AuthenticationException("No id found"));
                }).onErrorResume(throwable -> {
                    LOG.error("error occurred when setting user as authzManagerRoleName for subdomainId", throwable);
                    return Mono.error(throwable);
                });
    }

    public Mono<Boolean> isOrgAdminInOrgId(String accessToken, UUID userId, UUID organizationId) {
        LOG.info("get orgAdmin organizations count for this user in accessToken");

        final StringBuilder stringBuilder = new StringBuilder(roleEndpoint);
        stringBuilder.append("/authzmanagerroles/users/").append(userId)
                .append("/organizations/").append(organizationId).append("/org-admin");
        LOG.info("is user orgAdmin in orgId using endpoint: {}", stringBuilder);

        WebClient.RequestHeadersUriSpec<?> requestHeadersUriSpec = webClientBuilder.build().get();

        if (accessToken != null) {
           requestHeadersUriSpec.headers(httpHeaders -> httpHeaders.setBearerAuth(accessToken));
        }

        return requestHeadersUriSpec.uri(stringBuilder.toString())
                .retrieve().bodyToMono(new ParameterizedTypeReference<Map<String, Boolean>>() {})
                .flatMap(map -> {
                    LOG.info("response for is user an OrgAdmin in orgId: {}", map);
                    if (map.get("message") != null) {
                        return Mono.just(map.get("message"));
                    }
                    else {
                        return Mono.error(new BadRequestException("There is no message in the response"));
                    }
                }).onErrorResume(throwable -> {
                    LOG.error("error occurred when checking if user is OrgAdmin for orgId", throwable);
                    return Mono.error(throwable);
                });
    }

    public Mono<UUID> getRoleIdForClientOrganizationUser(String accessToken, UUID clientId, UUID organizationId, UUID userId) {
        LOG.info("get roleId for a user using the clientId, organizationId, and userId");

        String endpoint = roleEndpoint + "/clients/{clientId}/organizations/{organizationId}/users/{userId}/roles"
                .replace("{clientId}", clientId.toString())
                .replace("{organizationId}", organizationId.toString())
                .replace("{userId}", userId.toString());


        LOG.info("get clientOrganizationUserWithRoles with endpoint: {}", endpoint);
        WebClient.RequestHeadersSpec<?> request = webClientBuilder.build().get().uri(endpoint)
                .accept(MediaType.APPLICATION_JSON);
        if (accessToken != null && !accessToken.isBlank()) {
            request.headers(httpHeaders -> httpHeaders.setBearerAuth(accessToken));
        }
        WebClient.ResponseSpec responseSpec = request.retrieve();

        return responseSpec.bodyToMono(UUID.class).map(s -> {
            LOG.info("got role for clientOrganizationUser roles: '{}'", s);
            return s;
        }).onErrorResume(throwable -> {
            LOG.debug("exception occurred in getting roleId for ClientOrganizationUserId", throwable);

            LOG.error("failed to get roleId for clientId, organizationId, userId {}", throwable.getMessage());
            return Mono.empty();
        });
    }

    public Mono<String> getRoleNameForClientOrganizationUser(String accessToken, UUID clientId, UUID organizationId, UUID userId) {
        LOG.info("get roleId for a user using the clientId, organizationId, and userId");

        String endpoint = roleEndpoint + "/clients/{clientId}/organizations/{organizationId}/users/{userId}/roles/name"
                .replace("{clientId}", clientId.toString())
                .replace("{organizationId}", organizationId.toString())
                .replace("{userId}", userId.toString());


        LOG.info("get clientOrganizationUserWithRoles with endpoint: {}", endpoint);
        WebClient.RequestHeadersSpec<?> request = webClientBuilder.build().get().uri(endpoint)
                .accept(MediaType.APPLICATION_JSON);
        if (accessToken != null && !accessToken.isBlank()) {
            request.headers(httpHeaders -> httpHeaders.setBearerAuth(accessToken));
        }
        WebClient.ResponseSpec responseSpec = request.retrieve();

        return responseSpec.bodyToMono(String.class).map(s -> {
            LOG.info("got role name for clientOrganizationUser roles: '{}'", s);
            return s;
        }).onErrorResume(throwable -> {
            LOG.debug("exception occurred in getting role name for ClientOrganizationUserId", throwable);

            LOG.error("failed to get role namefor clientId, organizationId, userId {}", throwable.getMessage());
            return Mono.empty();
        });
    }

    public Mono<Integer> getCountOfUsersWithUserClientOrganizationRoleByOrgId(String accessToken, UUID organizationId) {

        String endpoint = roleEndpoint + "/organizations/"+organizationId+"/count";


        LOG.info("get a count of client-organization-user with roles with the organization endpoint: {}", endpoint);
        WebClient.ResponseSpec responseSpec = webClientBuilder.build().get().uri(endpoint)
                .headers(httpHeaders -> httpHeaders.setBearerAuth(accessToken)).retrieve();

        return responseSpec.bodyToMono(new ParameterizedTypeReference<Map<String, Integer>>() {})
                        .flatMap(map -> {
                            LOG.info("response: {}", map);
                            return Mono.just(map.get("message"));
                        })
                .onErrorResume(throwable -> {
            LOG.debug("exception occurred in getting count for ClientOrganizationUserRole by orgId", throwable);

            LOG.error("failed to get count of roles with orgId {}, error message: {}", organizationId, throwable.getMessage());
            return Mono.error(throwable);
        });
    }

}
