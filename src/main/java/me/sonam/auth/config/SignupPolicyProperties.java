package me.sonam.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "authorization-server.signup-policy")
public class SignupPolicyProperties {
    private final Map<String, HostPolicy> hosts = new LinkedHashMap<>();

    public Map<String, HostPolicy> getHosts() {
        return hosts;
    }

    public void setHosts(Map<String, HostPolicy> hosts) {
        this.hosts.clear();
        if (hosts != null) {
            this.hosts.putAll(hosts);
        }
    }

    public static class HostPolicy {
        private boolean allowSignup = true;
        private boolean allowAccountSelfService = true;
        private boolean createOrganizationOnSignup = true;
        private final List<String> allowedEmailDomains = new ArrayList<>();

        public boolean isAllowSignup() {
            return allowSignup;
        }

        public void setAllowSignup(boolean allowSignup) {
            this.allowSignup = allowSignup;
        }

        public boolean isAllowAccountSelfService() {
            return allowAccountSelfService;
        }

        public void setAllowAccountSelfService(boolean allowAccountSelfService) {
            this.allowAccountSelfService = allowAccountSelfService;
        }

        public boolean isCreateOrganizationOnSignup() {
            return createOrganizationOnSignup;
        }

        public void setCreateOrganizationOnSignup(boolean createOrganizationOnSignup) {
            this.createOrganizationOnSignup = createOrganizationOnSignup;
        }

        public List<String> getAllowedEmailDomains() {
            return allowedEmailDomains;
        }

        public void setAllowedEmailDomains(List<String> allowedEmailDomains) {
            this.allowedEmailDomains.clear();
            if (allowedEmailDomains != null) {
                this.allowedEmailDomains.addAll(allowedEmailDomains);
            }
        }
    }
}
