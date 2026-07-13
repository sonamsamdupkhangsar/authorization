package me.sonam.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@ConfigurationProperties(prefix = "demo-cleanup")
public class DemoCleanupProperties {
    private boolean enabled;
    private long initialDelaySeconds = 300;
    private long fixedDelaySeconds = 21_600;
    private String tenantHost = "demo.openissuer.com";
    private String preservedClientIds = "nextauth-demo";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getInitialDelaySeconds() {
        return initialDelaySeconds;
    }

    public void setInitialDelaySeconds(long initialDelaySeconds) {
        this.initialDelaySeconds = initialDelaySeconds;
    }

    public long getFixedDelaySeconds() {
        return fixedDelaySeconds;
    }

    public void setFixedDelaySeconds(long fixedDelaySeconds) {
        this.fixedDelaySeconds = fixedDelaySeconds;
    }

    public String getTenantHost() {
        return tenantHost;
    }

    public void setTenantHost(String tenantHost) {
        this.tenantHost = tenantHost;
    }

    public String getPreservedClientIds() {
        return preservedClientIds;
    }

    public void setPreservedClientIds(String preservedClientIds) {
        this.preservedClientIds = preservedClientIds;
    }

    public Set<String> preservedClientIdSet() {
        if (!StringUtils.hasText(preservedClientIds)) {
            return Set.of();
        }

        return Arrays.stream(preservedClientIds.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
