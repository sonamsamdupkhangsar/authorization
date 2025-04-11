package me.sonam.auth.service;

import me.sonam.auth.jpa.entity.ClientOrganization;
import me.sonam.auth.jpa.entity.ClientUser;
import me.sonam.auth.jpa.repo.ClientOrganizationRepository;
import me.sonam.auth.jpa.repo.HClientUserRepository;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
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

    @Value("${authzmanager-id}")
    private UUID authzManagerId;

    final String ROLES = "roles";

    public AuthenticationCallout(WebClient.Builder webClientBuilder, RequestCache requestCache,
                                 LoginAttemptWebClient loginAttemptWebClient,
                                 OrganizationWebClient organizationWebClient,
                                 AuthenticationWebClient authenticationWebClient,
                                 UserWebClient userWebClient, AccountWebClient accountWebClient) {
        this.webClientBuilder = webClientBuilder;
        this.requestCache = requestCache;
        this.loginAttemptWebClient = loginAttemptWebClient;
        this.organizationWebClient = organizationWebClient;
        this.authenticationWebClient = authenticationWebClient;
        this.userWebClient = userWebClient;
        this.accountWebClient = accountWebClient;
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
             throw new BadCredentialsException("clientId not found in request cache");
         }


         return accountWebClient.isAccountLocked(authenticationId)
                 .flatMap(aBoolean -> {
                     if (aBoolean) {
                         LOG.info("account is locked for authenticationId {}", authenticationId);
                         return Mono.error(new BadCredentialsException("account is locked"));
                     }
                     return Mono.just(aBoolean);
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
                    .flatMap(s -> Mono.error(new BadCredentialsException("user not found with authenticationId: "+
                            authenticationId + ", "+s)));

        }
        ).flatMap(userId ->
            checkClientInOrganization(authentication, userId, clientId)
                    .switchIfEmpty(checkClientUserRelationship(userId, clientId, authentication))

                    .onErrorResume(throwable -> {
                        LOG.debug("exception occurred after going thru checking if client-id exists in client-user relationship", throwable);
                        LOG.error("exception occurred after going thru client-user relationship check: {}", throwable.getMessage());

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
                                    .flatMap(s -> Mono.error(new BadCredentialsException(s)));
                        }
                        return Mono.error(throwable);
                    })
        );
    }

    private Mono<UsernamePasswordAuthenticationToken> checkClientUserRelationship(final UUID userId, final UUID clientId, final Authentication authentication) {
        LOG.info("checkClientUserRelationship() - checking userId {} and clientId {} in ClientUser relationship", userId, clientId);

        Optional<ClientUser> clientUserOptional = clientUserRepository.findByClientIdAndUserId(clientId, userId);

        if (clientUserOptional.isPresent()) {
            LOG.info("user has clientId relationship");
            return authenticationWebClient.getAuth(authentication, Map.of("authenticationId", authentication.getPrincipal().toString(),
                    "password", authentication.getCredentials().toString(),
                    "clientId", clientId));
        }
        else {
            LOG.info("authzManagerId: {}, clientId: {}", authzManagerId, clientId);
            if (authzManagerId.equals(clientId)) {
                LOG.info("clientId is for authzmanager, create user-authzmanager client relationship");
                clientUserRepository.save(new ClientUser(clientId, userId));

                return authenticationWebClient.getAuth(authentication,  Map.of("authenticationId", authentication.getPrincipal().toString(),
                                                    "password", authentication.getCredentials().toString(),
                                                "clientId", clientId)
                        );
            }
            else {

                LOG.error("the user trying to log-in with user-id is not associated with this client-id");
                return Mono.error(new BadCredentialsException("there is no client-id association with this user-id"));
            }
        }
    }

    private Mono<UsernamePasswordAuthenticationToken> checkClientInOrganization(Authentication authentication, UUID userId, UUID clientId) {
        LOG.info("checkClientInOrganization() - checking client exists in clientOrganization");

        Optional<ClientOrganization> optionalClientOrganization = clientOrganizationRepository.findByClientId(clientId);
        optionalClientOrganization.ifPresent(clientOrganization -> LOG.info("clientOrganization exists with clientId: {}", clientOrganization));

        if (optionalClientOrganization.isEmpty()) {
            LOG.error("client-id {} not found in clientOrganization", clientId);
            return Mono.empty(); // return empty if there is no client organization association for this client-id
           //return Mono.error(new BadCredentialsException("no clientId " + clientId + " found in ClientOrganization"));
        }
        else {
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
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }
}