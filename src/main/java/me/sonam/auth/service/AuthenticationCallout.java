package me.sonam.auth.service;

import me.sonam.auth.jpa.entity.ClientOrganization;
import me.sonam.auth.jpa.repo.ClientOrganizationRepository;
import me.sonam.auth.jpa.repo.HClientUserRepository;
import me.sonam.auth.service.exception.AuthorizationException;
import me.sonam.auth.service.exception.BadCredentialsException;
import me.sonam.auth.webclient.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * This class is used for making authentication callout to external authentication-rest-service
 * for authenticating username and password.
 */
@Service
public class AuthenticationCallout implements AuthenticationProvider {
    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationCallout.class);

    private final RequestCache requestCache;
    private final WebClient.Builder webClientBuilder;

    @Autowired
    private ClientOrganizationRepository clientOrganizationRepository;

    @Autowired
    private HClientUserRepository clientUserRepository;

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    @Autowired
    private final LoginAttemptWebClient loginAttemptWebClient;
    @Autowired
    private final OrganizationWebClient organizationWebClient;

    @Autowired
    private final AuthenticationWebClient authenticationWebClient;
    @Autowired
    private final UserWebClient userWebClient;
    @Autowired
    private AccountWebClient accountWebClient;
    @Autowired
    private SettingWebClient settingWebClient;
    @Autowired
    private RoleWebClient roleWebClient;

    @Value("${authzmanager-id}")
    private UUID authzManagerId;

    final String ROLES = "roles";

    public AuthenticationCallout(WebClient.Builder webClientBuilder, RequestCache requestCache,
                                 LoginAttemptWebClient loginAttemptWebClient,
                                 OrganizationWebClient organizationWebClient,
                                 AuthenticationWebClient authenticationWebClient,
                                 UserWebClient userWebClient, AccountWebClient accountWebClient,
                                 SettingWebClient settingWebClient, RoleWebClient roleWebClient) {
        this.webClientBuilder = webClientBuilder;
        this.requestCache = requestCache;
        this.loginAttemptWebClient = loginAttemptWebClient;
        this.organizationWebClient = organizationWebClient;
        this.authenticationWebClient = authenticationWebClient;
        this.userWebClient = userWebClient;
        this.accountWebClient = accountWebClient;
        this.settingWebClient = settingWebClient;
        this.roleWebClient = roleWebClient;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        LOG.info("authenticate with username and password");

        // ip address
        Object details = authentication.getDetails();
        String ipAddress = "";
        if (details instanceof WebAuthenticationDetails) {
            WebAuthenticationDetails webDetails = (WebAuthenticationDetails) details;
            // Access webDetails properties
            LOG.info("details is webAuthenitcationDetails type");
            ipAddress = webDetails.getRemoteAddress();
            LOG.info("ip address is {}", ipAddress);
        }

        final String authenticationId = authentication.getName();
        final String password = authentication.getCredentials().toString();

        LOG.info("authenticationId {}, password: {}", authenticationId, password);

         String clientId = ClientIdUtil.getClientId(requestCache);
         LOG.info("clientId: {}", clientId);
         if (clientId == null || clientId.equals("")) {
             LOG.error("client id not found");
             throw new BadCredentialsException("Please go back to your main application that brought you here to this sign-in page");
         }


         return accountWebClient.isAccountLocked(authenticationId)
                 .flatMap(aBoolean -> {
                     if (aBoolean) {
                         LOG.info("account is locked for authenticationId {}", authenticationId);

                         return loginAttemptWebClient.checkLoginAttempt(authenticationId)
                                         .flatMap(s -> {
                                             if (s.equals("keep locked")) {
                                                 LOG.info("response from checkAttempt is to keep locked as time interval has not passed");
                                                 return Mono.error(new BadCredentialsException("account is locked"));
                                             }
                                             else {
                                                 LOG.info("response {}", s);
                                                 LOG.info("let user attempt go thru");
                                                 return accountWebClient.unLockAccountAfterTimedIntervalExpire(authenticationId)
                                                         .onErrorResume(throwable -> {
                                                             LOG.debug("exception occurred in unlockAccount time Expiration", throwable);
                                                             LOG.error("failed to unlock account after timed interval expire {}", throwable.getMessage());
                                                             return Mono.error(throwable);
                                                         }).thenReturn(s);
                                             }
                                         }).thenReturn(true);
                     }
                     else {
                         LOG.info("account is not locked");
                         return Mono.just(aBoolean);
                     }
                 })
                .flatMap(aBoolean -> {
                    LOG.info("authorities: {}, details: {}, credentials: {}", authentication.getAuthorities(),
                            authentication.getDetails(), authentication.getCredentials());
                    LOG.info("get registeredClient from clientId: {}", clientId);
                    RegisteredClient registeredClient = registeredClientRepository.findByClientId(clientId);
                    UUID clientUuidId = UUID.fromString(registeredClient.getId());
                    LOG.info("got clientUuid: {}", clientUuidId);
                    return checkUserAndClient(authentication, clientUuidId);
                })
                 .block();
    }

    private Mono<UsernamePasswordAuthenticationToken> checkUserAndClient(Authentication authentication, UUID clientId) {
        final String authenticationId = authentication.getName();
        LOG.info("check user and client relationship");

        return userWebClient.getUserId(authenticationId).onErrorResume(throwable -> {
            LOG.error("failed to make get user by authId call: {}", throwable.getMessage());
            if (throwable instanceof WebClientResponseException) {
                WebClientResponseException webClientResponseException = (WebClientResponseException) throwable;
                LOG.error("error body contains: {}", webClientResponseException.getResponseBodyAsString());

                // catching BadCredentials exception from getUserId call

            }
            String ipAddress = "";
            if (authentication.getDetails() instanceof WebAuthenticationDetails) {
                WebAuthenticationDetails webDetails = (WebAuthenticationDetails) authentication.getDetails();
                // Access webDetails properties
                LOG.info("details is webAuthenitcationDetails type");
                ipAddress = webDetails.getRemoteAddress();
                LOG.info("ip address is {}", ipAddress);
            }

            return loginAttemptWebClient.loginFailed(authenticationId, ipAddress)
                    .doOnNext(s -> LOG.error("user not found with authenticationId: {}", authenticationId, throwable))
                    .flatMap(s -> Mono.error(new BadCredentialsException("Bad credentials")));

        }
        ).flatMap(userId -> isClientAuthzManager(userId, clientId, authentication)
                .switchIfEmpty(checkClientInOrganization(authentication, userId, clientId))

                    .onErrorResume(throwable -> {
                        LOG.debug("exception occurred when going thru client organization check", throwable);
                        LOG.error("exception occurred when going thru client organization check: {}", throwable.getMessage());

                        if (throwable instanceof BadCredentialsException) {
                            String ipAddress = "";

                            if (authentication.getDetails() instanceof WebAuthenticationDetails) {
                                WebAuthenticationDetails webDetails = (WebAuthenticationDetails) authentication.getDetails();
                                // Access webDetails properties
                                LOG.info("details is webAuthenitcationDetails type");
                                ipAddress = webDetails.getRemoteAddress();
                                LOG.info("ip address is {}", ipAddress);
                            }

                            return loginAttemptWebClient.loginFailed(authenticationId, ipAddress)
                                    .doOnNext(s -> LOG.trace("authentication failed: {}", authenticationId, throwable))
                                    .flatMap(s -> {
                                        final String message = throwable.getMessage() + " " + s;
                                        return Mono.error(new BadCredentialsException(message));
                                       });
                        }
                        return Mono.error(throwable);
                    })
        );
    }

    /**
     * If the clientId is authzManager create a client-user row if doesn't exist
     * and return call to authenticationWebClient.getAuth()
     * @param userId UUID of user
     * @param clientId UUID of client id
     * @param authentication authentication object
     * @return if authentication is success or fail object
     */
    private Mono<UsernamePasswordAuthenticationToken> isClientAuthzManager(final UUID userId, final UUID clientId, final Authentication authentication) {
        LOG.info("checking if the clientId: {} is for authzManagerId: {}", clientId, authzManagerId);

        if (authzManagerId.equals(clientId)) {
            LOG.info("client is authzManager, authenticate user");

            return settingWebClient.getDefaultOrganization(null, userId)
                    .switchIfEmpty(Mono.error(new AuthorizationException("No default org found four userId " + userId)))
                    //.flatMap(uuid -> roleWebClient.setUserAsRoleNameForOrganization(null, "SuperAdmin", userId, uuid).thenReturn(uuid))
                            .flatMap(orgId -> roleWebClient.isSuperAdminInOrgId(null, userId, orgId).zipWith(Mono.just(orgId)))
                    .flatMap(objects -> {
                                        if (!objects.getT1()) {
                                            return Mono.error(new BadCredentialsException("User is not a superadmin in the default orgId: "+ objects.getT2()));
                                        }
                                        return Mono.just(objects.getT2()); //return orgId
                                    }).flatMap(orgId -> authenticationWebClient.getAuth(authentication,  Map.of(
                                            "authenticationId", authentication.getPrincipal().toString(),
                                    "password", authentication.getCredentials().toString(),
                                    "clientId", clientId)));
        }
        else {
            LOG.info("clientId is not AuthzManager");
            return Mono.empty();
        }
    }

    private Mono<UsernamePasswordAuthenticationToken> checkClientInOrganization(Authentication authentication, UUID userId, UUID clientId) {
        return Mono.defer(() -> {
            LOG.info("checkClientInOrganization() - checking client exists in clientOrganization");
            Optional<ClientOrganization> optionalClientOrganization = clientOrganizationRepository.findByClientId(clientId);

            if (optionalClientOrganization.isEmpty()) {
                LOG.info("client-id {} not associated with organization, not found in clientOrganization", clientId);
                //return Mono.empty(); // return empty if there is no client organization association for this client-id
                return Mono.error(new BadCredentialsException("client is not associated to organization"));
            } else {
                LOG.info("client-id is associated to a organization, so user must also be associated to a organization for logging-in");
            }

            ClientOrganization clientOrganization = optionalClientOrganization.get();

            // client-id is associated to organization-id, check user-id is also in organization-id
            return organizationWebClient.userExistInOrganization(userId, clientOrganization.getOrganizationId())
                    .filter(aBoolean -> aBoolean)
                    .switchIfEmpty(Mono.error(new BadCredentialsException("user does not exists in organization")))
                    .flatMap(aBoolean -> authenticationWebClient.getAuth(authentication,
                            Map.of("authenticationId", authentication.getPrincipal().toString(),
                                    "password", authentication.getCredentials().toString(),
                                    "clientId", clientId,
                                    "organizationId", clientOrganization.getOrganizationId())));
        });
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }
}