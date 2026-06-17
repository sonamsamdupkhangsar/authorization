package me.sonam.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@Profile({"local-https", "localDevTest"})
public class TokenWebClientConfig {
    private static final Logger LOG = LoggerFactory.getLogger(TokenWebClientConfig.class);

    @Bean
    WebClient.Builder tokenWebClientBuilder() {
        LOG.info("creating non-loadBalanced token webclient");
        return WebClient.builder();
    }
}
