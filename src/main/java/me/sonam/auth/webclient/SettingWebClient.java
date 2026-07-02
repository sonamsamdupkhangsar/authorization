package me.sonam.auth.webclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

public class SettingWebClient {
    private static final Logger LOG = LoggerFactory.getLogger(SettingWebClient.class);

    private final WebClient.Builder webClientBuilder;
    private final String userSettingEndpoint;
    private final String defaultOrganizationSettingEndpoint;

    public SettingWebClient(WebClient.Builder webClientBuilder, String userSettingEndpoint, String defaultOrganizationSettingEndpoint) {
        this.webClientBuilder = webClientBuilder;
        this.userSettingEndpoint = userSettingEndpoint;
        this.defaultOrganizationSettingEndpoint = defaultOrganizationSettingEndpoint;
    }
    
    /**
     * get defaultOrganizationId for the userId
     * @param userId logged-in user-id
     * @return defaultOrganizationId UUID if there is or null if not set
     */
    public Mono<UUID> getDefaultOrganization(String accessToken, UUID userId) {
        LOG.info("get defaultOrganizationId for userId: {}", userId);

        final String endpoint = defaultOrganizationSettingEndpoint.replace("{userId}", userId.toString());


        LOG.info("get defaultOrganizationId for userId at endpoint: {}", endpoint);

        //get all user setting and find the defaultOrganizationId field
        WebClient.ResponseSpec responseSpec = webClientBuilder.build().get().uri(endpoint)
                .headers(httpHeaders -> {
                    if (StringUtils.hasText(accessToken)) {
                        httpHeaders.setBearerAuth(accessToken);
                    }
                }).accept(MediaType.APPLICATION_JSON).retrieve();
        return responseSpec.bodyToMono(new ParameterizedTypeReference<Map<String, Object>>(){})
                .doOnNext(stringObjectMap -> LOG.info("got data: {}", stringObjectMap))
                .flatMap(this::getDefaultOrganizationId)
                .onErrorResume(throwable -> {
            LOG.debug("exception occurred in getting defaultOrganizationId", throwable);
            LOG.error("failed to get defaultOrganizationId {}", throwable.getMessage());

            return Mono.error(throwable);
        });
    }

    public Mono<String> addDefaultOrganization(String accessToken, UUID userId, UUID organizationId) {
        LOG.info("add defaultOrganizationId for userId: {}, orgId: {} using endpoint: {}", userId, organizationId, userSettingEndpoint);

        WebClient.ResponseSpec responseSpec = webClientBuilder.build().put().uri(userSettingEndpoint)
                .headers(httpHeaders -> {
                    if (StringUtils.hasText(accessToken)) {
                        httpHeaders.setBearerAuth(accessToken);
                    }
                })
                .bodyValue(Map.of("userId", userId, "defaultOrganizationId", organizationId)).retrieve();

        return responseSpec.bodyToMono(new ParameterizedTypeReference<Map<String, String>>(){})
                .flatMap(this::addRemoveOrganization).onErrorResume(throwable -> {
            LOG.debug("exception occurred in getting defaultOrganizationId", throwable);
            LOG.error("failed to get defaultOrganizationId {}", throwable.getMessage());

            return Mono.error(throwable);
        });
    }

    public Mono<String> removeDefaultOrganization(String accessToken, UUID userId) {
        final String endpoint = userSettingEndpoint + "/" + userId;
        LOG.info("remove defaultOrganizationId for userId: {} using endpoint: {}", userId, endpoint);

        WebClient.ResponseSpec responseSpec = webClientBuilder.build().delete().uri(endpoint)
                .headers(httpHeaders -> {
                    if (StringUtils.hasText(accessToken)) {
                        httpHeaders.setBearerAuth(accessToken);
                    }
                })
                .retrieve();

        return responseSpec.bodyToMono(new ParameterizedTypeReference<Map<String, String>>(){})
                .flatMap(this::addRemoveOrganization).onErrorResume(throwable -> {
                    LOG.debug("exception occurred in removing defaultOrganizationId", throwable);
                    LOG.error("failed to remove defaultOrganizationId {}", throwable.getMessage());

                    return Mono.error(throwable);
                });
    }


    private Mono<String> addRemoveOrganization(Map<String, String> map) {
        LOG.info("map: {}", map);

        return Mono.just(map.get("message"));
    }

    private Mono<UUID> getDefaultOrganizationId(Map<String, Object> map) {
        LOG.info("default organization response received");

        if (map != null) {
            Object message = map.get("message");
            if (!(message instanceof Map<?, ?> objectMap)) {
                return Mono.error(new IllegalStateException("default organization response is missing message data"));
            }

            Object defaultOrganizationId = objectMap.get("defaultOrganizationId");
            if (defaultOrganizationId != null) {
                return Mono.just(UUID.fromString(defaultOrganizationId.toString()));
            } else {
                LOG.error("no defaultOrganizationId value found");
                return Mono.empty();
            }
        } else {
            LOG.error("got null for user setting {}", map);
            return Mono.empty();
        }
    }
}
