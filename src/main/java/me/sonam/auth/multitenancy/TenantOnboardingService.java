package me.sonam.auth.multitenancy;

import me.sonam.auth.init.ClientSetup;
import me.sonam.auth.jpa.entity.TenantRegistration;
import me.sonam.auth.jpa.repo.TenantRegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TenantOnboardingService {
    private final AuthorizationServerMultitenancyProperties multitenancyProperties;
    private final TenantPerHostComponentRegistry componentRegistry;
    private final IssuerComponentRegistrar issuerComponentRegistrar;
    private final ClientSetup clientSetup;
    private final TenantRegistrationRepository tenantRegistrationRepository;

    public TenantOnboardingService(AuthorizationServerMultitenancyProperties multitenancyProperties,
                                   TenantPerHostComponentRegistry componentRegistry,
                                   IssuerComponentRegistrar issuerComponentRegistrar,
                                   ClientSetup clientSetup,
                                   TenantRegistrationRepository tenantRegistrationRepository) {
        this.multitenancyProperties = multitenancyProperties;
        this.componentRegistry = componentRegistry;
        this.issuerComponentRegistrar = issuerComponentRegistrar;
        this.clientSetup = clientSetup;
        this.tenantRegistrationRepository = tenantRegistrationRepository;
    }

    public synchronized Map<String, Object> registerTenant(TenantRegistrationRequest request) {
        Assert.hasText(request.getTenantName(), "tenantName is required");
        Assert.state(!request.getHosts().isEmpty(), "at least one host must be configured");
        Assert.hasText(request.getUrl(), "url is required");
        Assert.hasText(request.getUsername(), "username is required");
        Assert.hasText(request.getPasswordSecretRef(), "passwordSecretRef is required");
        Assert.hasText(request.getDriverClassName(), "driverClassName is required");

        if (multitenancyProperties.getTenants().containsKey(request.getTenantName())) {
            throw new IllegalStateException("tenant already exists: " + request.getTenantName());
        }

        request.getHosts().forEach(this::assertHostIsAvailable);

        AuthorizationServerMultitenancyProperties.Tenant tenant = new AuthorizationServerMultitenancyProperties.Tenant();
        tenant.setHosts(request.getHosts());
        tenant.setUrl(request.getUrl());
        tenant.setUsername(request.getUsername());
        tenant.setPasswordSecretRef(request.getPasswordSecretRef());
        tenant.setDriverClassName(request.getDriverClassName());

        DataSource dataSource = issuerComponentRegistrar.createDataSource(tenant);
        issuerComponentRegistrar.registerTenantComponents(dataSource, tenant.getHosts(), componentRegistry);
        multitenancyProperties.getTenants().put(request.getTenantName(), tenant);
        tenantRegistrationRepository.save(toEntity(request));
        tenant.getHosts().forEach(host -> clientSetup.seedIssuerClients(toIssuer(host)));

        return toResponse(request.getTenantName(), tenant);
    }

    public synchronized Map<String, Object> listTenants() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("defaultHosts", multitenancyProperties.getDefaultHosts());
        Map<String, Object> tenants = new LinkedHashMap<>();
        multitenancyProperties.getTenants().forEach((tenantName, tenant) -> tenants.put(tenantName, toResponse(tenantName, tenant)));
        response.put("tenants", tenants);
        return response;
    }

    public synchronized void loadPersistedTenants() {
        tenantRegistrationRepository.findAll().forEach(registration -> {
            if (multitenancyProperties.getTenants().containsKey(registration.getTenantName())) {
                return;
            }
            AuthorizationServerMultitenancyProperties.Tenant tenant = toTenant(registration);
            tenant.getHosts().forEach(this::assertHostIsAvailable);
            DataSource dataSource = issuerComponentRegistrar.createDataSource(tenant);
            issuerComponentRegistrar.registerTenantComponents(dataSource, tenant.getHosts(), componentRegistry);
            multitenancyProperties.getTenants().put(registration.getTenantName(), tenant);
            tenant.getHosts().forEach(host -> clientSetup.seedIssuerClients(toIssuer(host)));
        });
    }

    private void assertHostIsAvailable(String host) {
        if (multitenancyProperties.getDefaultHosts().contains(host)) {
            throw new IllegalStateException("host already mapped as default: " + host);
        }
        if (componentRegistry.get(host, RegisteredClientRepository.class) != null) {
            throw new IllegalStateException("host already registered: " + host);
        }
    }

    private String toIssuer(String host) {
        if ("localhost".equals(host) || "127.0.0.1".equals(host)) {
            return "http://" + host;
        }
        return "https://" + host;
    }

    private TenantRegistration toEntity(TenantRegistrationRequest request) {
        TenantRegistration tenantRegistration = new TenantRegistration();
        tenantRegistration.setTenantName(request.getTenantName());
        tenantRegistration.setHosts(String.join(",", request.getHosts()));
        tenantRegistration.setUrl(request.getUrl());
        tenantRegistration.setUsername(request.getUsername());
        tenantRegistration.setPasswordSecretRef(request.getPasswordSecretRef());
        tenantRegistration.setDriverClassName(request.getDriverClassName());
        tenantRegistration.setCreatedAt(Instant.now());
        return tenantRegistration;
    }

    private AuthorizationServerMultitenancyProperties.Tenant toTenant(TenantRegistration registration) {
        AuthorizationServerMultitenancyProperties.Tenant tenant = new AuthorizationServerMultitenancyProperties.Tenant();
        tenant.setHosts(parseHosts(registration.getHosts()));
        tenant.setUrl(registration.getUrl());
        tenant.setUsername(registration.getUsername());
        tenant.setPasswordSecretRef(registration.getPasswordSecretRef());
        tenant.setDriverClassName(registration.getDriverClassName());
        return tenant;
    }

    private List<String> parseHosts(String hosts) {
        return Arrays.stream(hosts.split(","))
                .map(String::trim)
                .filter(host -> !host.isEmpty())
                .collect(Collectors.toList());
    }

    private Map<String, Object> toResponse(String tenantName, AuthorizationServerMultitenancyProperties.Tenant tenant) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("tenantName", tenantName);
        response.put("hosts", tenant.getHosts());
        response.put("url", tenant.getUrl());
        response.put("username", tenant.getUsername());
        response.put("passwordSecretRef", tenant.getPasswordSecretRef());
        response.put("driverClassName", tenant.getDriverClassName());
        response.put("issuers", tenant.getHosts().stream().map(this::toIssuer).toList());
        return response;
    }
}
