package me.sonam.auth.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "tenant_registration")
public class TenantRegistration {
    @Id
    private String tenantName;
    @Column(length = 2000, nullable = false)
    private String hosts;
    @Column(nullable = false)
    private String url;
    @Column(nullable = false)
    private String username;
    @Column(nullable = false)
    private String passwordSecretRef;
    @Column(nullable = false)
    private String driverClassName;
    @Column(nullable = false)
    private Instant createdAt;

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public String getHosts() {
        return hosts;
    }

    public void setHosts(String hosts) {
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
