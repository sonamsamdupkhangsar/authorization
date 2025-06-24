package me.sonam.auth.webclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

public class OrganizationWebClient {
    private static final Logger LOG = LoggerFactory.getLogger(OrganizationWebClient.class);

    private final WebClient.Builder webClientBuilder;

    private final String organizationUrl;

    public OrganizationWebClient(WebClient.Builder webClientBuilder, String organizationUrl) {
        this.webClientBuilder = webClientBuilder;
        this.organizationUrl = organizationUrl;
    }

    public Mono<Boolean> userExistInOrganization(UUID userId, UUID organizationId) {
        StringBuilder userExistsInOrganizationEndpoint = new StringBuilder(
                organizationUrl.replace("{organizationId}", organizationId.toString())
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

}
