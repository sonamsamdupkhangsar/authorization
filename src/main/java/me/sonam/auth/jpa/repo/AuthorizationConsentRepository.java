package me.sonam.auth.jpa.repo;

import java.util.Optional;

import me.sonam.auth.jpa.entity.AuthorizationConsent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuthorizationConsentRepository extends JpaRepository<AuthorizationConsent, AuthorizationConsent.AuthorizationConsentId> {
    Optional<AuthorizationConsent> findByRegisteredClientIdAndPrincipalName(String registeredClientId, String principalName);
    Optional<AuthorizationConsent> findByTenantIdAndRegisteredClientIdAndPrincipalName(String tenantId, String registeredClientId, String principalName);
    long deleteByTenantIdAndRegisteredClientId(String tenantId, String registeredClientId);
    void deleteByRegisteredClientIdAndPrincipalName(String registeredClientId, String principalName);
    void deleteByTenantIdAndRegisteredClientIdAndPrincipalName(String tenantId, String registeredClientId, String principalName);
}
