package me.sonam.auth.jpa.repo;

import me.sonam.auth.jpa.entity.ClientOrganization;
import me.sonam.auth.jpa.entity.ClientOrganizationId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientOrganizationRepository extends JpaRepository<ClientOrganization, ClientOrganizationId> {
    Optional<ClientOrganization> findByClientId(UUID clientId);//client.id field which is UUID type but stored as String
    Optional<Boolean> existsByClientId(UUID clientId);

    Optional<Long> deleteByClientId(UUID clientId);
    Optional<Boolean> existsByClientIdAndOrganizationId(UUID clientId, UUID organizationId);
    Optional<ClientOrganization> findByClientIdAndOrganizationIdIn(UUID clientId, List<UUID> organizationIds);

    Optional<Long> deleteByClientIdAndOrganizationId(UUID clientId, UUID organizationId);
    List<ClientOrganization> findByOrganizationIdIn(List<UUID> organizationIds);
    List<ClientOrganization> findByOrganizationId(UUID organizationId, Pageable pageable);
    long countByOrganizationId(UUID organizationId);
}
