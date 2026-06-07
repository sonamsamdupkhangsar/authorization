package me.sonam.auth.rest.signup;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Arrays;
import java.util.UUID;

public class UserSignup {
    @NotEmpty(message="firstName cannot be empty")
    @Size(min = 3, max = 50)
    private String firstName;
    @Size(min = 0, max = 50)
    private String lastName;
    @NotEmpty(message="email cannot be empty")
    @Size(max = 100)
    private String email;
    @NotEmpty(message="Username cannot be empty")
    @Size(min = 3, max = 50)
    private String authenticationId;

    private String organization;

    private UUID organizationId;

    private char[] password;

    private boolean active; //this field is used to activate a user using the admin

    private String activationHost;

    public UserSignup() {

    }

    public UserSignup(String firstName, String lastName, String email, String authenticationId, char[] password, boolean active, String organization) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.authenticationId = authenticationId;
        this.password = password;
        this.active = active;
        this.organization = organization;
    }

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
        return this.email;
    }

    public void setEmail(String email) {
        if (email != null) {
            this.email = email.toLowerCase();
        }
    }

    public String getAuthenticationId() {
        return authenticationId;
    }

    public void setAuthenticationId(String authenticationId) {
        if (authenticationId != null) {
            this.authenticationId = authenticationId.toLowerCase();
        }
    }

    public char[] getPassword() {
        return password;
    }

    public void setPassword(char[] password) {
        this.password = password;
    }
    public boolean isActive() {
        return this.active;
    }
    public void setActive(boolean active) {
        this.active = active;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public UUID getOrganizationId() {
        return this.organizationId;
    }
    @Override
    public String toString() {
        return "UserSignup{" +
                "firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", email='" + email + '\'' +
                ", authenticationId='" + authenticationId + '\'' +
                ", password=" + Arrays.toString(password) +
                ", active='" +active + '\'' +
                '}';
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public String getActivationHost() {
        return activationHost;
    }

    public void setActivationHost(String activationHost) {
        this.activationHost = activationHost;
    }
}
