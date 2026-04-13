package me.sonam.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ConfigurationProperties(prefix = "organization-seed")
public class OrganizationSeedProperties {
    private long delaySeconds = 120;
    private final List<SeedUser> users = new ArrayList<>();
    private final List<SeedOrganization> organizations = new ArrayList<>();

    public long getDelaySeconds() {
        return delaySeconds;
    }

    public void setDelaySeconds(long delaySeconds) {
        this.delaySeconds = delaySeconds;
    }

    public List<SeedUser> getUsers() {
        return users;
    }

    public List<SeedOrganization> getOrganizations() {
        return organizations;
    }

    public static class SeedUser {
        private String firstName;
        private String lastName;
        private String email;
        private String authenticationId;
        private String password;
        private boolean active = true;

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getAuthenticationId() {
            return authenticationId;
        }

        public void setAuthenticationId(String authenticationId) {
            this.authenticationId = authenticationId;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }

    public static class SeedOrganization {
        private String name;
        private UUID creatorUserId;
        private String creatorAuthenticationId;
        private String subdomain;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public UUID getCreatorUserId() {
            return creatorUserId;
        }

        public void setCreatorUserId(UUID creatorUserId) {
            this.creatorUserId = creatorUserId;
        }

        public String getCreatorAuthenticationId() {
            return creatorAuthenticationId;
        }

        public void setCreatorAuthenticationId(String creatorAuthenticationId) {
            this.creatorAuthenticationId = creatorAuthenticationId;
        }

        public String getSubdomain() {
            return subdomain;
        }

        public void setSubdomain(String subdomain) {
            this.subdomain = subdomain;
        }
    }
}
