package me.sonam.auth.init;

import me.sonam.auth.config.OrganizationSeedProperties;
import me.sonam.auth.rest.signup.Organization;
import me.sonam.auth.rest.signup.UserSignup;
import me.sonam.auth.webclient.OrganizationWebClient;
import me.sonam.auth.webclient.RoleWebClient;
import me.sonam.auth.webclient.UserWebClient;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class OrganizationSeedSetup {
    private static final Logger LOG = LoggerFactory.getLogger(OrganizationSeedSetup.class);

    private final OrganizationSeedProperties organizationSeedProperties;
    private final OrganizationWebClient organizationWebClient;
    private final RoleWebClient roleWebClient;
    private final UserWebClient userWebClient;
    private final TaskScheduler taskScheduler;

    public OrganizationSeedSetup(OrganizationSeedProperties organizationSeedProperties,
                                 OrganizationWebClient organizationWebClient,
                                 RoleWebClient roleWebClient,
                                 UserWebClient userWebClient,
                                 TaskScheduler taskScheduler) {
        this.organizationSeedProperties = organizationSeedProperties;
        this.organizationWebClient = organizationWebClient;
        this.roleWebClient = roleWebClient;
        this.userWebClient = userWebClient;
        this.taskScheduler = taskScheduler;
    }

    // Wait until the application is fully ready, then delay seeding to give downstream clients
    // and service discovery time to stabilize before making remote seed calls.
    @EventListener(ApplicationReadyEvent.class)
    public void scheduleSeeding() {
        if (organizationSeedProperties.getUsers().isEmpty() && organizationSeedProperties.getOrganizations().isEmpty()) {
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
        Map<String, UUID> seededUsers = seedUsers();

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
                    .then()
                    .block();
        });

        attachSeedUsersToOrganizationsAsOrgAdmins(seededUsers);
    }

    // Ensures each configured bootstrap user exists and returns a lookup map that later seed
    // organizations can use to resolve creatorAuthenticationId to a real user id.
    private Map<String, UUID> seedUsers() {
        Map<String, UUID> seededUsers = new HashMap<>();

        organizationSeedProperties.getUsers().forEach(seedUser -> {
            if (!StringUtils.hasText(seedUser.getAuthenticationId())) {
                LOG.info("skipping seed user without authenticationId");
                return;
            }

            UUID userId = userWebClient.getUserId(seedUser.getAuthenticationId())
                    .onErrorResume(throwable -> userWebClient.signupUser(toUserSignup(seedUser))
                            .then(userWebClient.getUserId(seedUser.getAuthenticationId())))
                    .doOnNext(id -> LOG.info("resolved seeded user {} with id {}", seedUser.getAuthenticationId(), id))
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
     * authzmanager OrgAdmin for the same organization. The OrgAdmin role is the important
     * part for admin login: authzmanager allows login only when the user is OrgAdmin for the
     * organization resolved from the current tenant host.
     *
     * Example:
     *   business2user@openissuer.test + business2.openissuer.test
     *   -> add user to Business 2
     *   -> assign OrgAdmin for Business 2
     *   -> make Business 2 the user's default organization
     *   -> allow login at business2.admin.openissuer.test
     */
    private void attachSeedUsersToOrganizationsAsOrgAdmins(Map<String, UUID> seededUsers) {
        organizationSeedProperties.getUsers().forEach(seedUser -> {
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
                            .then(organizationWebClient.setDefaultOrganization(organizationId, userId))
                            .doOnNext(response -> LOG.info("set seeded user {} default organization to subdomain {}",
                                    seedUser.getAuthenticationId(), seedUser.getOrganizationSubdomain())))
                    .block();
        });
    }

    // Converts the local YAML seed entry into the signup payload expected by user-rest-service.
    private UserSignup toUserSignup(OrganizationSeedProperties.SeedUser seedUser) {
        UserSignup userSignup = new UserSignup();
        userSignup.setFirstName(seedUser.getFirstName());
        userSignup.setLastName(seedUser.getLastName());
        userSignup.setEmail(seedUser.getEmail());
        userSignup.setAuthenticationId(seedUser.getAuthenticationId());
        userSignup.setPassword(seedUser.getPassword() == null ? null : seedUser.getPassword().toCharArray());
        userSignup.setActive(seedUser.isActive());
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
