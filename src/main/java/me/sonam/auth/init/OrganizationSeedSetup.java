package me.sonam.auth.init;

import me.sonam.auth.config.OrganizationSeedProperties;
import me.sonam.auth.rest.signup.Organization;
import me.sonam.auth.rest.signup.UserSignup;
import me.sonam.auth.webclient.OrganizationWebClient;
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
    private final UserWebClient userWebClient;
    private final TaskScheduler taskScheduler;

    public OrganizationSeedSetup(OrganizationSeedProperties organizationSeedProperties,
                                 OrganizationWebClient organizationWebClient,
                                 UserWebClient userWebClient,
                                 TaskScheduler taskScheduler) {
        this.organizationSeedProperties = organizationSeedProperties;
        this.organizationWebClient = organizationWebClient;
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

            organizationWebClient.getOrganizationIdBySubdomain(seedOrganization.getSubdomain())
                    .flatMap(existingId -> {
                        LOG.info("organization already exists for subdomain {} with id {}", seedOrganization.getSubdomain(), existingId);
                        return Mono.<Void>empty();
                    })
                    .switchIfEmpty(organizationWebClient.updateOrganization(
                            new Organization(null, seedOrganization.getName(),
                                    creatorUserId, seedOrganization.getSubdomain()),
                            HttpMethod.POST
                    ).doOnNext(org -> LOG.info("seeded organization {} for subdomain {}", org.getId(), seedOrganization.getSubdomain()))
                            .then())
                    .block();
        });
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
