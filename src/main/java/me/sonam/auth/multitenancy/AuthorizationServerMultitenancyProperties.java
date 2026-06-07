package me.sonam.auth.multitenancy;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "authorization-server.multitenancy")
public class AuthorizationServerMultitenancyProperties {
    private List<String> defaultHosts = new ArrayList<>();
    private Map<String, Tenant> tenants = new LinkedHashMap<>();

    public List<String> getDefaultHosts() {
        return defaultHosts;
    }

    public void setDefaultHosts(List<String> defaultHosts) {
        this.defaultHosts = defaultHosts;
    }

    public Map<String, Tenant> getTenants() {
        return tenants;
    }

    public void setTenants(Map<String, Tenant> tenants) {
        this.tenants = tenants;
    }

    public static class Tenant {
        private List<String> hosts = new ArrayList<>();
        private String url;
        private String username;
        private String passwordSecretRef;
        private String driverClassName;
        private Integer maximumPoolSize;

        public List<String> getHosts() {
            return hosts;
        }

        public void setHosts(List<String> hosts) {
            this.hosts = hosts;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPasswordSecretRef() {
            return passwordSecretRef;
        }

        public void setPasswordSecretRef(String passwordSecretRef) {
            this.passwordSecretRef = passwordSecretRef;
        }

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }

        public Integer getMaximumPoolSize() {
            return maximumPoolSize;
        }

        public void setMaximumPoolSize(Integer maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
        }
    }
}
