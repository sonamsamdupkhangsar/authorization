package me.sonam.auth.rest.admin;

import me.sonam.auth.multitenancy.TenantOnboardingService;
import me.sonam.auth.multitenancy.TenantRegistrationRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/tenants")
public class TenantOnboardingRestService {
    private final TenantOnboardingService tenantOnboardingService;

    public TenantOnboardingRestService(TenantOnboardingService tenantOnboardingService) {
        this.tenantOnboardingService = tenantOnboardingService;
    }

    @GetMapping
    public Map<String, Object> listTenants() {
        return tenantOnboardingService.listTenants();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createTenant(@RequestBody TenantRegistrationRequest request) {
        return tenantOnboardingService.registerTenant(request);
    }
}
