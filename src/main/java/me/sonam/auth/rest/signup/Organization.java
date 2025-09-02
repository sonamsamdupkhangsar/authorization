package me.sonam.auth.rest.signup;

import jakarta.validation.constraints.NotEmpty;

import java.util.UUID;

/**
 * this is Organization class used for entering data into organization-rest-service
 */
public class Organization {
    private UUID id;
    @NotEmpty(message = "name cannot be empty")
    private String name;
    private UUID creatorUserId;
    private boolean defaultOrganization;
    private boolean previousDefaultOrganization;//this is used to check if it was previously a default org

    public Organization() {

    }

    public Organization(UUID id, String name, UUID creatorUserId) {
        this.id = id;
        this.name = name;
        this.creatorUserId = creatorUserId;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public UUID getCreatorUserId() {
        return creatorUserId;
    }

    public boolean isDefaultOrganization() {
        return this.defaultOrganization;
    }
    public void setDefaultOrganization(boolean value) {
        this.defaultOrganization = value;
    }
    public boolean isPreviousDefaultOrganization() {
        return this.previousDefaultOrganization;
    }
    public void setPreviousDefaultOrganization(boolean value) {
        this.previousDefaultOrganization = value;
    }

    @Override
    public String toString() {
        return "Organization{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", creatorUserId=" + creatorUserId +
                ", defaultOrganization="+defaultOrganization+
                '}';
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public void setCreatorUserId(UUID creatorUserId) {
        this.creatorUserId = creatorUserId;
    }
}
