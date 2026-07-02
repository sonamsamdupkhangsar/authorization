package me.sonam.auth.webclient;

import me.sonam.auth.rest.signup.Organization;
import me.sonam.auth.util.CustomRestPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class OrganizationWebClient {
    private static final Logger LOG = LoggerFactory.getLogger(OrganizationWebClient.class);

    private final WebClient.Builder webClientBuilder;

    private final String userExistsInOrganizationEndpoint;
    private final String organizationEndpoint;
    private final String organizationBySubdomainEndpoint;

    public OrganizationWebClient(WebClient.Builder webClientBuilder, String organizationEndpoint,
                                 String userExistsInOrganizationEndpoint, String organizationBySubdomainEndpoint) {
        this.webClientBuilder = webClientBuilder;
        this.organizationEndpoint = organizationEndpoint;
        this.userExistsInOrganizationEndpoint = userExistsInOrganizationEndpoint;
        this.organizationBySubdomainEndpoint = organizationBySubdomainEndpoint;
    }
    public Mono<Map<String, String>> addUserToOrganization(UUID userId, UUID organizationId) {
        return addUserToOrganization(userId, organizationId, null, false);
    }

    public Mono<Map<String, String>> addUserToOrganization(UUID userId, UUID organizationId,
                                                           String subdomain, boolean restrictToSubdomain) {
        LOG.info("add user {} to organization {}", userId, organizationId);

        final StringBuilder stringBuilder = new StringBuilder(organizationEndpoint);
        stringBuilder.append("/users");

        LOG.info("add user to organization endpoint: {}", stringBuilder);

        Map<String, Object> body = subdomain == null
                ? Map.of("userId", userId, "organizationId", organizationId)
                : Map.of("userId", userId, "organizationId", organizationId,
                "subdomain", subdomain, "restrictToSubdomain", restrictToSubdomain);

        WebClient.ResponseSpec responseSpec = webClientBuilder.build().post().uri(stringBuilder.toString())
                .bodyValue(body).retrieve();

        return responseSpec.bodyToMono(new ParameterizedTypeReference<Map<String, String>>() {});
    }
    // use httpMethod for update or post
    public Mono<Organization> updateOrganization(Organization organization, HttpMethod httpMethod) {
        LOG.info("create organization with endpoint: {} for org: {}", organizationEndpoint, organization);

        WebClient.ResponseSpec responseSpec = webClientBuilder.build().method(httpMethod).uri(organizationEndpoint)
                .bodyValue(organization)
                .retrieve();
        return responseSpec.bodyToMono(Organization.class).flatMap(organization1-> {
            LOG.info("saved organization");
            return Mono.just(organization1);
        });
    }
    public Mono<Boolean> userExistInOrganization(UUID userId, UUID organizationId) {
        StringBuilder userExistsInOrganizationEndpoint = new StringBuilder(
                this.userExistsInOrganizationEndpoint.replace("{organizationId}", organizationId.toString())
                        .replace("{userId}", userId.toString()));

        LOG.info("make userExistsInOrganizationEndpoint call to endpoint: {}", userExistsInOrganizationEndpoint);

        WebClient.ResponseSpec responseSpec = webClientBuilder.build().get()
                .uri(userExistsInOrganizationEndpoint.toString()).retrieve();

        //throws exception if user does not exist in organization
        return responseSpec.bodyToMono(Map.class).map(map -> {
            Object message = map.get("message");
            LOG.info("userExistsInOrganization response received, exists: {}", Boolean.TRUE.equals(message));
            if (Boolean.TRUE.equals(message)) {
                LOG.info("return true");
                return true;
            }
            else {
                LOG.info("return false");
                return false;
            }

        }).onErrorResume(throwable -> {
            LOG.error("error on userExistsInOrganizationEndpoint to endpoint '{}' with error: {}", userExistsInOrganizationEndpoint,
                    throwable.getMessage());

            if (throwable instanceof WebClientResponseException) {
                WebClientResponseException webClientResponseException = (WebClientResponseException) throwable;
                LOG.error("error body contains: {}", webClientResponseException.getResponseBodyAsString());
                return Mono.just(false);
            }
            else {
                return Mono.just(false);
            }
        });
    }

    public Mono<UUID> getOrganizationIdBySubdomain(String subdomain) {
        String endpoint = this.organizationBySubdomainEndpoint.replace("{subdomain}", subdomain);
        LOG.info("get organization by subdomain endpoint: {}", endpoint);

        return webClientBuilder.build().get().uri(endpoint).retrieve()
                .bodyToMono(Map.class)
                .flatMap(map -> {
                    Object id = map.get("id");
                    if (id == null) {
                        return Mono.empty();
                    }
                    return Mono.just(UUID.fromString(id.toString()));
                })
                .onErrorResume(throwable -> {
                    LOG.error("failed to get organization by subdomain {}: {}", subdomain, throwable.getMessage());
                    if (throwable instanceof WebClientResponseException webClientResponseException) {
                        LOG.error("organization by subdomain error body contains: {}",
                                webClientResponseException.getResponseBodyAsString());
                    }
                    return Mono.empty();
                });
    }

    public Mono<List<UUID>> getOrganizationIdsBySubdomain(String subdomain) {
        String endpoint = this.organizationBySubdomainEndpoint.replace("{subdomain}", subdomain)
                + "/organizations?page=0&size=100";
        LOG.info("get organizations by subdomain endpoint: {}", endpoint);

        return webClientBuilder.build().get().uri(endpoint).retrieve()
                .bodyToMono(new ParameterizedTypeReference<CustomRestPage<Map<String, Object>>>() {})
                .map(organizationPage -> organizationPage.content().stream()
                        .map(organization -> organization.get("id"))
                        .filter(id -> id != null)
                        .map(id -> UUID.fromString(id.toString()))
                        .toList())
                .onErrorResume(throwable -> {
                    LOG.error("failed to get organizations by subdomain {}: {}", subdomain, throwable.getMessage());
                    if (throwable instanceof WebClientResponseException webClientResponseException) {
                        LOG.error("organizations by subdomain error body contains: {}",
                                webClientResponseException.getResponseBodyAsString());
                    }
                    return Mono.just(List.of());
                });
    }

    public Mono<UUID> getSubdomainIdByHost(String subdomain) {
        String endpoint = this.organizationEndpoint + "/subdomains/" + subdomain;
        LOG.info("get subdomain by host endpoint: {}", endpoint);

        return webClientBuilder.build().get().uri(endpoint).retrieve()
                .bodyToMono(Map.class)
                .flatMap(map -> {
                    Object id = map.get("id");
                    if (id == null) {
                        return Mono.empty();
                    }
                    return Mono.just(UUID.fromString(id.toString()));
                })
                .onErrorResume(throwable -> {
                    LOG.error("failed to get subdomain by host {}: {}", subdomain, throwable.getMessage());
                    if (throwable instanceof WebClientResponseException webClientResponseException) {
                        LOG.error("subdomain by host error body contains: {}",
                                webClientResponseException.getResponseBodyAsString());
                    }
                    return Mono.empty();
                });
    }

    public Mono<Boolean> userExistsInSubdomainOrganization(String subdomain, UUID userId, UUID organizationId) {
        String endpoint = this.organizationBySubdomainEndpoint.replace("{subdomain}", subdomain)
                + "/users/" + userId + "/organizations/" + organizationId;
        LOG.info("check user exists in subdomain organization endpoint: {}", endpoint);

        return webClientBuilder.build().get().uri(endpoint).retrieve()
                .bodyToMono(Map.class)
                .map(map -> Boolean.TRUE.equals(map.get("message")))
                .onErrorResume(throwable -> {
                    LOG.error("failed to check subdomain {} user {} organization {}: {}",
                            subdomain, userId, organizationId, throwable.getMessage());
                    if (throwable instanceof WebClientResponseException webClientResponseException) {
                        LOG.error("check user exists in subdomain organization error body contains: {}",
                                webClientResponseException.getResponseBodyAsString());
                    }
                    return Mono.just(false);
                });
    }

    public Mono<UUID> getDefaultOrganizationIdBySubdomainAndUserId(String subdomain, UUID userId) {
        String endpoint = this.organizationBySubdomainEndpoint.replace("{subdomain}", subdomain)
                + "/users/" + userId + "/default-organization-id";
        LOG.info("get default organization by subdomain and user endpoint: {}", endpoint);

        return webClientBuilder.build().get().uri(endpoint).retrieve()
                .bodyToMono(Map.class)
                .flatMap(map -> {
                    Object organizationId = map.get("message");
                    if (organizationId == null) {
                        return Mono.empty();
                    }
                    return Mono.just(UUID.fromString(organizationId.toString()));
                })
                .onErrorResume(throwable -> {
                    LOG.error("failed to get default organization by subdomain {} and user {}: {}",
                            subdomain, userId, throwable.getMessage());
                    if (throwable instanceof WebClientResponseException webClientResponseException) {
                        LOG.error("get default organization by subdomain and user error body contains: {}",
                                webClientResponseException.getResponseBodyAsString());
                    }
                    return Mono.empty();
                });
    }

    public Mono<UUID> getDefaultOrganizationIdForUser(UUID userId) {
        String endpoint = organizationEndpoint + "/users/" + userId + "/default-organization-id";
        LOG.info("get default organization by user endpoint: {}", endpoint);

        return webClientBuilder.build().get().uri(endpoint).retrieve()
                .bodyToMono(Map.class)
                .flatMap(map -> {
                    Object organizationId = map.get("message");
                    if (organizationId == null) {
                        return Mono.empty();
                    }
                    return Mono.just(UUID.fromString(organizationId.toString()));
                })
                .onErrorResume(throwable -> {
                    LOG.error("failed to get default organization by user {}: {}", userId, throwable.getMessage());
                    if (throwable instanceof WebClientResponseException webClientResponseException) {
                        LOG.error("get default organization by user error body contains: {}",
                                webClientResponseException.getResponseBodyAsString());
                    }
                    return Mono.empty();
                });
    }

    public Mono<String> setDefaultOrganization(UUID organizationId, UUID userId) {
        String endpoint = organizationEndpoint + "/" + organizationId + "/users/" + userId + "/default";
        LOG.info("set default organization endpoint: {}", endpoint);

        return webClientBuilder.build().put().uri(endpoint).retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, String>>() {})
                .map(map -> map.get("message"))
                .onErrorResume(throwable -> {
                    LOG.error("failed to set default organization {} for user {}: {}",
                            organizationId, userId, throwable.getMessage());
                    if (throwable instanceof WebClientResponseException webClientResponseException) {
                        LOG.error("set default organization error body contains: {}",
                                webClientResponseException.getResponseBodyAsString());
                    }
                    return Mono.error(throwable);
                });
    }

    public Mono<String> addOrganizationToSubdomain(String subdomain, UUID organizationId) {
        String endpoint = this.organizationBySubdomainEndpoint.replace("{subdomain}", subdomain)
                + "/organizations/" + organizationId;
        LOG.info("add organization {} to subdomain {} endpoint: {}", organizationId, subdomain, endpoint);

        return webClientBuilder.build().post().uri(endpoint).retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, String>>() {})
                .map(map -> map.get("message"))
                .onErrorResume(throwable -> {
                    LOG.error("failed to add organization {} to subdomain {}: {}",
                            organizationId, subdomain, throwable.getMessage());
                    if (throwable instanceof WebClientResponseException webClientResponseException) {
                        LOG.error("add organization to subdomain error body contains: {}",
                                webClientResponseException.getResponseBodyAsString());
                    }
                    return Mono.error(throwable);
                });
    }

}
