package me.sonam.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient builders for Spring/Eureka service discovery.
 *
 * Regular service calls use Spring Cloud LoadBalancer. Local HTTPS keeps service
 * calls load-balanced but uses a direct token client so token requests can hit
 * the HTTPS issuer URL instead of a Eureka-resolved HTTP instance.
 */
@Configuration
@Profile("eureka")
public class EurekaWebClientConfig {
    private static final Logger LOG = LoggerFactory.getLogger(EurekaWebClientConfig.class);

    @LoadBalanced
    @Bean
    @Primary
    WebClient.Builder webClientBuilder() {
        LOG.info("creating load-balanced service WebClient for Eureka service discovery");
        return WebClient.builder();
    }

    @LoadBalanced
    @Bean
    @Profile("!local-https")
    WebClient.Builder tokenWebClientBuilder() {
        LOG.info("creating load-balanced token WebClient for Eureka service discovery");
        return WebClient.builder();
    }

    @Bean("tokenWebClientBuilder")
    @Profile("local-https")
    WebClient.Builder directTokenWebClientBuilder() {
        LOG.info("creating direct token WebClient for local HTTPS");
        return WebClient.builder();
    }
}
