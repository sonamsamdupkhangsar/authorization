package me.sonam.auth.multitenancy;

import java.util.ArrayList;
import java.util.List;

public class TenantRegistrationRequest {
    private String tenantName;
    private List<String> hosts = new ArrayList<>();
    private String url;
    private String username;
    private String passwordSecretRef;
    private String driverClassName;

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

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
}
