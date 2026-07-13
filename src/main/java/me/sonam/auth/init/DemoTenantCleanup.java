package me.sonam.auth.init;

import jakarta.transaction.Transactional;
import me.sonam.auth.config.DemoCleanupProperties;
import me.sonam.auth.jpa.entity.Client;
import me.sonam.auth.jpa.repo.AuthorizationConsentRepository;
import me.sonam.auth.jpa.repo.AuthorizationRepository;
import me.sonam.auth.jpa.repo.ClientOrganizationRepository;
import me.sonam.auth.jpa.repo.ClientRepository;
import me.sonam.auth.jpa.repo.HClientUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Component
public class DemoTenantCleanup {
    private static final Logger LOG = LoggerFactory.getLogger(DemoTenantCleanup.class);

    private final DemoCleanupProperties demoCleanupProperties;
    private final ClientRepository clientRepository;
    private final AuthorizationRepository authorizationRepository;
    private final AuthorizationConsentRepository authorizationConsentRepository;
    private final ClientOrganizationRepository clientOrganizationRepository;
    private final HClientUserRepository clientUserRepository;
    private final TaskScheduler taskScheduler;

    public DemoTenantCleanup(DemoCleanupProperties demoCleanupProperties,
                             ClientRepository clientRepository,
                             AuthorizationRepository authorizationRepository,
                             AuthorizationConsentRepository authorizationConsentRepository,
                             ClientOrganizationRepository clientOrganizationRepository,
                             HClientUserRepository clientUserRepository,
                             TaskScheduler taskScheduler) {
        this.demoCleanupProperties = demoCleanupProperties;
        this.clientRepository = clientRepository;
        this.authorizationRepository = authorizationRepository;
        this.authorizationConsentRepository = authorizationConsentRepository;
        this.clientOrganizationRepository = clientOrganizationRepository;
        this.clientUserRepository = clientUserRepository;
        this.taskScheduler = taskScheduler;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void scheduleCleanup() {
        if (!demoCleanupProperties.isEnabled()) {
            LOG.info("demo tenant cleanup is disabled");
            return;
        }

        long initialDelaySeconds = Math.max(0, demoCleanupProperties.getInitialDelaySeconds());
        long fixedDelaySeconds = Math.max(60, demoCleanupProperties.getFixedDelaySeconds());
        Instant firstRun = Instant.now().plusSeconds(initialDelaySeconds);

        LOG.info("scheduling demo tenant cleanup for tenantHost {} firstRun {} fixedDelaySeconds {}",
                demoCleanupProperties.getTenantHost(), firstRun, fixedDelaySeconds);
        taskScheduler.scheduleWithFixedDelay(this::cleanupDemoTenantClients, firstRun, Duration.ofSeconds(fixedDelaySeconds));
    }

    @Transactional
    public void cleanupDemoTenantClients() {
        String tenantHost = demoCleanupProperties.getTenantHost();
        Set<String> preservedClientIds = demoCleanupProperties.preservedClientIdSet();

        if (!StringUtils.hasText(tenantHost)) {
            LOG.warn("demo tenant cleanup skipped because tenantHost is blank");
            return;
        }

        if (preservedClientIds.isEmpty()) {
            LOG.warn("demo tenant cleanup skipped because preservedClientIds is empty");
            return;
        }

        LOG.info("running demo tenant cleanup for tenantHost {} preserving clientIds {}", tenantHost, preservedClientIds);

        CleanupCounts counts = new CleanupCounts();
        clientRepository.findByTenantId(tenantHost).stream()
                .filter(client -> !preservedClientIds.contains(client.getClientId()))
                .forEach(client -> cleanupClient(tenantHost, client, counts));

        LOG.info("demo tenant cleanup complete tenantHost {} clientsDeleted {} authorizationsDeleted {} "
                        + "authorizationConsentsDeleted {} clientOrganizationsDeleted {} clientUsersDeleted {}",
                tenantHost,
                counts.clientsDeleted,
                counts.authorizationsDeleted,
                counts.authorizationConsentsDeleted,
                counts.clientOrganizationsDeleted,
                counts.clientUsersDeleted);
    }

    private void cleanupClient(String tenantHost, Client client, CleanupCounts counts) {
        String registeredClientId = client.getId();
        UUID clientUuid = UUID.fromString(registeredClientId);

        counts.authorizationsDeleted += authorizationRepository
                .deleteByTenantIdAndRegisteredClientId(tenantHost, registeredClientId);
        counts.authorizationConsentsDeleted += authorizationConsentRepository
                .deleteByTenantIdAndRegisteredClientId(tenantHost, registeredClientId);
        counts.clientUsersDeleted += clientUserRepository.deleteByClientId(clientUuid);
        counts.clientOrganizationsDeleted += clientOrganizationRepository.deleteByClientId(clientUuid).orElse(0L);

        clientRepository.deleteById(registeredClientId);
        counts.clientsDeleted++;

        LOG.info("deleted demo tenant client tenantHost {} clientId {} registeredClientId {}",
                tenantHost, client.getClientId(), registeredClientId);
    }

    private static class CleanupCounts {
        private long clientsDeleted;
        private long authorizationsDeleted;
        private long authorizationConsentsDeleted;
        private long clientOrganizationsDeleted;
        private long clientUsersDeleted;
    }
}
