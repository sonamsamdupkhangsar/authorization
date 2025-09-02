package me.sonam.auth.webclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Pageable;
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

    public Mono<List<UUID>> getSuperAdminOrgIds(String accessToken, int page, int size) {
        LOG.info("get superAdmin organizations for this user in accessToken");

        final StringBuilder stringBuilder = new StringBuilder(roleEndpoint);
        stringBuilder.append("/authzmanagerroles/users/organizations?page="+ page + "&size="+size);
        LOG.info("get super admin organizations endpoint: {}", stringBuilder);

        WebClient.ResponseSpec responseSpec = webClientBuilder.build().get().uri(stringBuilder.toString())
                .headers(httpHeaders -> httpHeaders.setBearerAuth(accessToken))
                .retrieve();
        return responseSpec.bodyToMono(new ParameterizedTypeReference<List<UUID>>() {});
    }

    public Mono<Integer> getSuperAdminOrgIdsCount(String accessToken) {
        LOG.info("get superAdmin organizations count for this user in accessToken");

        final StringBuilder stringBuilder = new StringBuilder(roleEndpoint);
        stringBuilder.append("/authzmanagerroles/users/organizations/count");
        LOG.info("get super admin organizations endpoint: {}", stringBuilder);

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
}
