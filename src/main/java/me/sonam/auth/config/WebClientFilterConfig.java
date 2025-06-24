package me.sonam.auth.config;

import jakarta.annotation.PostConstruct;
import me.sonam.auth.util.TokenFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientFilterConfig {
    private static final Logger LOG = LoggerFactory.getLogger(WebClientFilterConfig.class);

    @Autowired
    private WebClient.Builder webClientBuilder;

    @Autowired
    private TokenFilter tokenFilter;

    @PostConstruct
    public void addFilterToWebClient() {
        LOG.info("configure the renewTokenFilter only once in this config");
        webClientBuilder.filter(tokenFilter.renewTokenFilter()).build();
    }
}
