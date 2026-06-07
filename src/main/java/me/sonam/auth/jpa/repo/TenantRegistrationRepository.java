package me.sonam.auth.jpa.repo;

import me.sonam.auth.jpa.entity.TenantRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantRegistrationRepository extends JpaRepository<TenantRegistration, String> {
}
