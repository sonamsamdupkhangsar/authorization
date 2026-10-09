package me.sonam.auth.init;

import me.sonam.auth.config.OrganizationSeedProperties;
import me.sonam.auth.rest.signup.Organization;
import me.sonam.auth.rest.signup.UserSignup;
import me.sonam.auth.webclient.OrganizationWebClient;
import me.sonam.auth.webclient.RoleWebClient;
import me.sonam.auth.webclient.UserWebClient;
import me.sonam.auth.jpa.entity.ClientOrganization;
import me.sonam.auth.jpa.repo.ClientOrganizationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.net.URI;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

@Component
public class OrganizationSeedSetup {
    private static final Logger LOG = LoggerFactory.getLogger(OrganizationSeedSetup.class);

    private final OrganizationSeedProperties organizationSeedProperties;
    private final OrganizationWebClient organizationWebClient;
    private final RoleWebClient roleWebClient;
    private final UserWebClient userWebClient;
    private final TaskScheduler taskScheduler;
    private final ClientOrganizationRepository clientOrganizationRepository;
    private final RegisteredClientRepository registeredClientRepository;

    @Value("${TENANT_PORTAL_CLIENT_ID:}")
    private String tenantPortalClientId;

    @Value("${ISSUER_URI:}")
    private String issuerUri;

    public OrganizationSeedSetup(OrganizationSeedProperties organizationSeedProperties,
                                 OrganizationWebClient organizationWebClient,
                                 RoleWebClient roleWebClient,
                                 UserWebClient userWebClient,
                                 TaskScheduler taskScheduler,
                                 ClientOrganizationRepository clientOrganizationRepository,
                                 RegisteredClientRepository registeredClientRepository) {
        this.organizationSeedProperties = organizationSeedProperties;
        this.organizationWebClient = organizationWebClient;
        this.roleWebClient = roleWebClient;
        this.userWebClient = userWebClient;
        this.taskScheduler = taskScheduler;
        this.clientOrganizationRepository = clientOrganizationRepository;
        this.registeredClientRepository = registeredClientRepository;
    }

    // Wait until the application is fully ready, then delay seeding to give downstream clients
    // and service discovery time to stabilize before making remote seed calls.
    @EventListener(ApplicationReadyEvent.class)
    public void scheduleSeeding() {
        if (getSeedUsers().isEmpty() && organizationSeedProperties.getOrganizations().isEmpty()) {
            LOG.info("organization seeding skipped because no seed users or organizations are configured");
            return;
        }

        long delaySeconds = Math.max(0, organizationSeedProperties.getDelaySeconds());
        Instant scheduledTime = Instant.now().plusSeconds(delaySeconds);
        LOG.info("scheduling organization seeding to run at {} after {} seconds", scheduledTime, delaySeconds);
        taskScheduler.schedule(this::seedOrganizations, scheduledTime);
    }

    // Seeds bootstrap users first and then creates any subdomain-bound organizations that do not
    // already exist in organization-rest-service.
    public void seedOrganizations() {
        LOG.info("seeding organizations");
        List<OrganizationSeedProperties.SeedUser> seedUsers = getSeedUsers();
        Map<String, UUID> seededUsers = seedUsers(seedUsers);

        organizationSeedProperties.getOrganizations().forEach(seedOrganization -> {
            if (!StringUtils.hasText(seedOrganization.getSubdomain())) {
                LOG.info("skipping seed organization without subdomain");
                return;
            }

            UUID creatorUserId = resolveCreatorUserId(seedOrganization, seededUsers);
            if (creatorUserId == null) {
                LOG.warn("skipping organization seed {} because creator user could not be resolved", seedOrganization.getName());
                return;
            }

            organizationWebClient.getOrganizationIdsBySubdomain(seedOrganization.getSubdomain())
                    .flatMap(existingIds -> {
                        if (!existingIds.isEmpty()) {
                            UUID existingId = existingIds.get(0);
                            LOG.info("organization already exists for subdomain {} with id {}",
                                    seedOrganization.getSubdomain(), existingId);
                            return Mono.just(existingId);
                        }
                        return Mono.empty();
                    })
                    .switchIfEmpty(organizationWebClient.updateOrganization(
                            new Organization(null, seedOrganization.getName(), creatorUserId),
                            HttpMethod.POST
                    ).doOnNext(org -> LOG.info("seeded organization {} for subdomain {}", org.getId(), seedOrganization.getSubdomain()))
                            .flatMap(org -> organizationWebClient.addOrganizationToSubdomain(seedOrganization.getSubdomain(), org.getId())
                                    .thenReturn(org))
                            .map(Organization::getId))
                    .then(associateTenantPortalClient(seedOrganization.getSubdomain()))
                    .block();
        });

        attachSeedUsersToOrganizationsAsOrgAdmins(seedUsers, seededUsers);
    }

    private Mono<Void> associateTenantPortalClient(String subdomain) {
        if (!StringUtils.hasText(tenantPortalClientId) || !isDefaultIssuerHost(subdomain)) {
            return Mono.empty();
        }

        return organizationWebClient.getOrganizationIdBySubdomain(subdomain)
                .flatMap(organizationId -> {
                    RegisteredClient client = registeredClientRepository.findByClientId(tenantPortalClientId);
                    if (client == null) {
                        LOG.warn("tenant portal client {} is not registered; skipping organization association", tenantPortalClientId);
                        return Mono.empty();
                    }
                    UUID clientId = UUID.fromString(client.getId());
                    if (!clientOrganizationRepository.existsByClientIdAndOrganizationId(clientId, organizationId).orElse(false)) {
                        clientOrganizationRepository.deleteByClientId(clientId);
                        clientOrganizationRepository.save(new ClientOrganization(clientId, organizationId));
                        LOG.info("associated tenant portal client {} with platform organization {}", tenantPortalClientId, organizationId);
                    } else {
                        LOG.info("tenant portal client {} already associated with platform organization {}", tenantPortalClientId, organizationId);
                    }
                    return Mono.empty();
                });
    }

    private boolean isDefaultIssuerHost(String subdomain) {
        if (!StringUtils.hasText(issuerUri)) {
            return false;
        }
        try {
            return subdomain.equals(URI.create(issuerUri).getHost());
        } catch (IllegalArgumentException exception) {
            LOG.warn("invalid ISSUER_URI {}; skipping tenant portal client association", issuerUri);
            return false;
        }
    }

    // Ensures each configured bootstrap user exists and returns a lookup map that later seed
    // organizations can use to resolve creatorAuthenticationId to a real user id.
    private List<OrganizationSeedProperties.SeedUser> getSeedUsers() {
        List<OrganizationSeedProperties.SeedUser> seedUsers = new ArrayList<>();
        organizationSeedProperties.getOrganizations().forEach(organization ->
                organization.getUsers().forEach(user -> {
                    if (!StringUtils.hasText(user.getOrganizationSubdomain())) {
                        user.setOrganizationSubdomain(organization.getSubdomain());
                    }
                    seedUsers.add(user);
                }));
        return seedUsers;
    }

    private Map<String, UUID> seedUsers(List<OrganizationSeedProperties.SeedUser> seedUsers) {
        Map<String, UUID> seededUsers = new HashMap<>();

        seedUsers.forEach(seedUser -> {
            if (!StringUtils.hasText(seedUser.getAuthenticationId())) {
                LOG.info("skipping seed user without authenticationId");
                return;
            }

            UUID userId = userWebClient.getUserId(seedUser.getAuthenticationId())
                    .onErrorResume(throwable -> userWebClient.signupUser(toUserSignup(seedUser))
                            .then(userWebClient.getUserId(seedUser.getAuthenticationId())))
                    .doOnNext(id -> LOG.info("resolved seeded user id {}", id))
                    .block();

            if (userId != null) {
                seededUsers.put(seedUser.getAuthenticationId(), userId);
            }
        });
        return seededUsers;
    }

    /*
     * Grants bootstrap access for configured host-bound organizations.
     *
     * Any seed user with organizationSubdomain is attached to that organization and then made
     * authzmanager OrgAdmin for the same organization. The OrgAdmin role is still the login
     * gate for authzmanager. If subdomainAdmin is true, the user also gets SubdomainAdmin for
     * that same subdomain so they can use subdomain-level admin views after login.
     *
     * Example:
     *   business2user@openissuer.test + business2.openissuer.test
     *   -> add user to Business 2
     *   -> assign OrgAdmin for Business 2
     *   -> make Business 2 the user's default organization
     *   -> allow login at business2.admin.openissuer.test
     */
    private void attachSeedUsersToOrganizationsAsOrgAdmins(List<OrganizationSeedProperties.SeedUser> seedUsers,
                                                           Map<String, UUID> seededUsers) {
        seedUsers.forEach(seedUser -> {
            if (!StringUtils.hasText(seedUser.getOrganizationSubdomain())) {
                return;
            }

            UUID userId = seededUsers.get(seedUser.getAuthenticationId());
            if (userId == null) {
                LOG.warn("skipping organization membership seed for {} because user id was not resolved",
                        seedUser.getAuthenticationId());
                return;
            }

            organizationWebClient.getOrganizationIdBySubdomain(seedUser.getOrganizationSubdomain())
                    .switchIfEmpty(Mono.error(new IllegalStateException("No organization bound to seed subdomain "
                            + seedUser.getOrganizationSubdomain())))
                    .flatMap(organizationId -> organizationWebClient.addUserToOrganization(userId, organizationId,
                                    seedUser.getOrganizationSubdomain(), true)
                            .doOnNext( response -> LOG.info("seeded user {} into organization subdomain {}",
                                    seedUser.getAuthenticationId(), seedUser.getOrganizationSubdomain()))
                            // OrgAdmin is required for this seed user to sign in to authzmanager for this tenant.
                            .then(roleWebClient.setUserAsRoleNameForOrganization(null, "OrgAdmin", userId, organizationId))
                            .doOnNext(roleId -> LOG.info("seeded user {} as OrgAdmin for organization subdomain {}",
                                    seedUser.getAuthenticationId(), seedUser.getOrganizationSubdomain()))
                            .then(assignSubdomainAdminIfConfigured(seedUser, userId))
                            .then(organizationWebClient.setDefaultOrganization(organizationId, userId))
                            .doOnNext(response -> LOG.info("set seeded user {} default organization to subdomain {}",
                                    seedUser.getAuthenticationId(), seedUser.getOrganizationSubdomain())))
                    .block();
        });
    }

    private Mono<?> assignSubdomainAdminIfConfigured(OrganizationSeedProperties.SeedUser seedUser, UUID userId) {
        if (!seedUser.isSubdomainAdmin()) {
            return Mono.empty();
        }

        return organizationWebClient.getSubdomainIdByHost(seedUser.getOrganizationSubdomain())
                .switchIfEmpty(Mono.error(new IllegalStateException("No subdomain found for "
                        + seedUser.getOrganizationSubdomain())))
                .flatMap(subdomainId -> roleWebClient.setUserAsRoleNameForSubdomain(null, "SubdomainAdmin",
                        userId, subdomainId))
                .doOnNext(roleId -> LOG.info("seeded user {} as SubdomainAdmin for subdomain {}",
                        seedUser.getAuthenticationId(), seedUser.getOrganizationSubdomain()));
    }

    // Converts the local YAML seed entry into the signup payload expected by user-rest-service.
    static UserSignup toUserSignup(OrganizationSeedProperties.SeedUser seedUser) {
        UserSignup userSignup = new UserSignup();
        userSignup.setFirstName(seedUser.getFirstName());
        userSignup.setLastName(seedUser.getLastName());
        userSignup.setEmail(seedUser.getEmail());
        userSignup.setAuthenticationId(seedUser.getAuthenticationId());
        userSignup.setPassword(seedUser.getPassword() == null ? null : seedUser.getPassword().toCharArray());
        userSignup.setActive(seedUser.isActive());
        userSignup.setActivationHost(seedUser.getOrganizationSubdomain());
        return userSignup;
    }

    // Supports either a fixed creatorUserId or a creatorAuthenticationId that was resolved during
    // the user seeding pass.
    private UUID resolveCreatorUserId(OrganizationSeedProperties.SeedOrganization seedOrganization, Map<String, UUID> seededUsers) {
        if (seedOrganization.getCreatorUserId() != null) {
            return seedOrganization.getCreatorUserId();
        }
        if (StringUtils.hasText(seedOrganization.getCreatorAuthenticationId())) {
            return seededUsers.get(seedOrganization.getCreatorAuthenticationId());
        }
        return null;
    }
}
