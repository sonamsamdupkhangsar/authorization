package me.sonam.auth.webclient;

import me.sonam.auth.rest.signup.Organization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

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
        LOG.info("add user {} to organization {}", userId, organizationId);

        final StringBuilder stringBuilder = new StringBuilder(organizationEndpoint);
        stringBuilder.append("/users");

        LOG.info("add user to organization endpoint: {}", stringBuilder);

        WebClient.ResponseSpec responseSpec = webClientBuilder.build().post().uri(stringBuilder.toString())
                .bodyValue(Map.of("userId", userId, "organizationId", organizationId)).retrieve();

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
            LOG.info("userExistsInOrganization response: {}, map.get'message': {}", map, map.get("message"));
            LOG.info("map.get(message): {}", map.get("message").equals(true));
            if (map.get("message").equals(true)) {
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

}
