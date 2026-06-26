package me.sonam.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Direct WebClient builders for environments that should not use Spring Cloud
 * LoadBalancer, such as Kubernetes DNS names and integration-test endpoints.
 */
@Configuration
@Profile("non-eureka")
public class DirectWebClientConfig {
    private static final Logger LOG = LoggerFactory.getLogger(DirectWebClientConfig.class);

    @Bean
    @Primary
    WebClient.Builder webClientBuilder() {
        LOG.info("creating direct service WebClient for non-Eureka service discovery");
        return WebClient.builder();
    }

    @Bean
    WebClient.Builder tokenWebClientBuilder() {
        LOG.info("creating direct token WebClient for non-Eureka service discovery");
        return WebClient.builder();
    }
}
