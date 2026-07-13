package me.sonam.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "client-limits")
public class ClientLimitProperties {
    private int defaultMaxClients = 5;
    private final Map<String, Integer> hosts = new HashMap<>();

    public int getDefaultMaxClients() {
        return defaultMaxClients;
    }

    public void setDefaultMaxClients(int defaultMaxClients) {
        this.defaultMaxClients = defaultMaxClients;
    }

    public Map<String, Integer> getHosts() {
        return hosts;
    }

    public int maxClientsForIssuer(String issuer) {
        String host = normalizeHost(issuer);
        if (StringUtils.hasText(host) && hosts.containsKey(host)) {
            return hosts.get(host);
        }
        return defaultMaxClients;
    }

    private String normalizeHost(String issuer) {
        if (!StringUtils.hasText(issuer)) {
            return null;
        }

        String trimmedIssuer = issuer.trim();
        try {
            URI uri = URI.create(trimmedIssuer);
            if (StringUtils.hasText(uri.getHost())) {
                return uri.getHost();
            }
        }
        catch (IllegalArgumentException ignored) {
            // Treat non-URI values as host names below.
        }

        int schemeIndex = trimmedIssuer.indexOf("://");
        String host = schemeIndex >= 0 ? trimmedIssuer.substring(schemeIndex + 3) : trimmedIssuer;
        int slashIndex = host.indexOf('/');
        if (slashIndex >= 0) {
            host = host.substring(0, slashIndex);
        }
        int colonIndex = host.indexOf(':');
        if (colonIndex >= 0) {
            host = host.substring(0, colonIndex);
        }
        return StringUtils.hasText(host) ? host : null;
    }
}
