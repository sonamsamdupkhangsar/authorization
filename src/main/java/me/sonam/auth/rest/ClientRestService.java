package me.sonam.auth.rest;


import jakarta.ws.rs.BadRequestException;
import me.sonam.auth.config.ClientLimitProperties;
import me.sonam.auth.jpa.entity.ClientOrganization;
import me.sonam.auth.jpa.repo.ClientOrganizationRepository;
import me.sonam.auth.multitenancy.IssuerAwareAuthorizationServerOperations;
import me.sonam.auth.service.RegisteredClientMapConverter;
import me.sonam.auth.service.exception.MaxCountException;
import me.sonam.auth.util.CustomPair;
import me.sonam.auth.util.CustomRestPage;
import me.sonam.auth.util.TokenRequestFilter;
import me.sonam.auth.util.UserIdUtil;
import me.sonam.auth.webclient.OrganizationWebClient;
import me.sonam.auth.webclient.RoleWebClient;
import org.apache.tomcat.websocket.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * Clients are associated to a Organization and are searched by organization.
 * They can be associated to a user who may create it but all objects fall
 * under Organization.
 */
@RestController
@RequestMapping("/clients")
public class ClientRestService {
    private static final Logger LOG = LoggerFactory.getLogger(ClientRestService.class);

    private final RegisteredClientMapConverter registeredClientMapConverter;
    private final IssuerAwareAuthorizationServerOperations issuerAwareAuthorizationServerOperations;

/*    @Autowired
    private HClientUserRepository clientUserRepository;*/

    @Autowired
    private ClientOrganizationRepository clientOrganizationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TokenRequestFilter tokenRequestFilter;

    @Autowired
    private final RoleWebClient roleWebClient;

    @Autowired
    private final OrganizationWebClient organizationWebClient;

    private final ClientLimitProperties clientLimitProperties;

    private final TransactionTemplate transactionTemplate;

    public ClientRestService(RegisteredClientMapConverter registeredClientMapConverter,
                             IssuerAwareAuthorizationServerOperations issuerAwareAuthorizationServerOperations,
                             PasswordEncoder passwordEncoder, RoleWebClient roleWebClient,
                             OrganizationWebClient organizationWebClient,
                             TransactionTemplate transactionTemplate,
                             ClientLimitProperties clientLimitProperties) {
        this.registeredClientMapConverter = registeredClientMapConverter;
        this.issuerAwareAuthorizationServerOperations = issuerAwareAuthorizationServerOperations;
        this.passwordEncoder = passwordEncoder;
        this.roleWebClient = roleWebClient;
        this.organizationWebClient = organizationWebClient;
        this.transactionTemplate = transactionTemplate;
        this.clientLimitProperties = clientLimitProperties;
        LOG.info("initialized clientRestService");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Map<String, Object>> createNew(@RequestBody Map<String, Object> map) {
        if (map == null) {
            return Mono.error(new BadRequestException("client request body is required"));
        }
        if (map.get("clientId") == null) {
            return Mono.error(new BadRequestException("clientId is required"));
        }

        LOG.info("create new client with clientId: {}", map.get("clientId"));

        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        final String issuer = issuerFromJwt(jwt);
        String userIdString = jwt.getClaim("userId");

        LOG.info("userId: {}", userIdString);

        String accessToken = jwt.getTokenValue();

        UUID userId = UUID.fromString(userIdString);

        LOG.info("create client requested by userId {}", userId);


       return organizationWebClient.getDefaultOrganizationIdForUser(userId)
               .switchIfEmpty(Mono.error(new AuthenticationException("User does not have default organization-id")))
               .flatMap(orgId -> roleWebClient.isOrgAdminInOrgId(accessToken, userId, orgId).zipWith(Mono.just(orgId)))
               .flatMap(objects -> {
                   if (objects.getT1() == false) {
                       return Mono.error(new AuthenticationException("user is not an OrgAdmin in organizationId : "+ objects.getT2()));
                   }
                   else {
                    return Mono.just(objects.getT2());
                   }
               })
               .flatMap(orgId -> {
                   int maxClients = clientLimitProperties.maxClientsForIssuer(issuer);
                   LOG.info("max number of clients count for issuer {}: {}", issuer, maxClients);
                    long clientCount = clientOrganizationRepository.countByOrganizationId(orgId);
                    LOG.info("clientCount: {}", clientCount);
                   if (clientCount >= maxClients) {
                        LOG.info("client row count max exceeded by organizationId: {}", clientCount);
                        return Mono.error(new MaxCountException("Max number of clients reached"));
                    }
                   return save(issuer, map, userId, orgId);
                });

    }

    private Mono<Map<String, Object>> save(String issuer, Map<String, Object> map, UUID userId, UUID organizationId) {
        if (issuerAwareAuthorizationServerOperations.findByClientId(issuer, map.get("clientId").toString()) != null) {
            LOG.error("clientId already exists, do an update");
            RegisteredClient registeredClient = issuerAwareAuthorizationServerOperations.findByClientId(issuer, map.get("clientId").toString());
            if (registeredClient != null) {
                return Mono.just(registeredClientMapConverter.getMapObject(registeredClient));
            }
            throw new BadRequestException("clientId already exists but not able to pull from repository");
        }

        String encodedPassword = passwordEncoder.encode((String)map.get("clientSecret"));

        LOG.info("encoding client secret before storage");
        map.put("clientSecret", encodedPassword);

        RegisteredClient registeredClient = registeredClientMapConverter.build(map);

        LOG.debug("built registered client with id {}", registeredClient.getId());

        issuerAwareAuthorizationServerOperations.save(issuer, registeredClient);
        RegisteredClient savedRedisteredClient = issuerAwareAuthorizationServerOperations.findById(issuer, registeredClient.getId());
        LOG.info("saved registered client with id {}", savedRedisteredClient.getId());

        LOG.info("saved registeredClient.id: {}", registeredClient.getId());
        UUID clientId = UUID.fromString(registeredClient.getId());

        Map<String, Object> mapToReturn = registeredClientMapConverter.getMapObject(registeredClient);

        LOG.info("clientId: {}", clientId);

        clientOrganizationRepository.save(new ClientOrganization(clientId, organizationId));
        LOG.info("saved clientOrganization clientId: {}, orgId: {}", clientId, organizationId);
        mapToReturn.put("orgId", organizationId);

        return Mono.just(mapToReturn);
    }

    @RequestMapping(value = "/client-id/{clientId}",  method=RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public Mono<Map<String, Object>> getByClientId(@PathVariable("clientId") String clientId) {
        LOG.info("get by clientId: {}", clientId);

        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        final String issuer = issuerFromJwt(jwt);
        String accessToken = jwt.getTokenValue();
        String userIdString = jwt.getClaim("userId");

        LOG.info("userId: {}", userIdString);

        UUID userId = UUID.fromString(userIdString);

        return organizationWebClient.getDefaultOrganizationIdForUser(userId)
                .flatMap(orgId -> {
                    RegisteredClient registeredClient = issuerAwareAuthorizationServerOperations.findByClientId(issuer, clientId);
                    if (registeredClient != null) {
                        UUID uuidClientId = UUID.fromString(registeredClient.getId());

                        Optional<Boolean> optionalBoolean = clientOrganizationRepository.
                                existsByClientIdAndOrganizationId(uuidClientId, orgId);
                        if (optionalBoolean.isPresent() && optionalBoolean.get()) {

                            return Mono.just(registeredClientMapConverter.getMapObject(registeredClient));
                        }
                        else {
                            LOG.info("clientId and organizationId not associated");
                        }
                    }
                    LOG.error("return error");
                    return Mono.just(Map.of("error", "registeredClient not found with client-id:"+ clientId));
                });
    }

    @GetMapping(value = "{id}")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Map<String, Object>> getClientById(@PathVariable("id") String id) {
        LOG.info("get client by id: {}", id);

        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        final String issuer = issuerFromJwt(jwt);
        String accessToken = jwt.getTokenValue();
        String userIdString = jwt.getClaim("userId");

        LOG.info("userId: {}", userIdString);
        UUID userId = UUID.fromString(userIdString);

        return organizationWebClient.getDefaultOrganizationIdForUser(userId)
                .flatMap(orgId -> {
                    LOG.info("orgId: '{}'", id);
                    UUID uuid = UUID.fromString(id);
                    Optional<Boolean> optionalBoolean = clientOrganizationRepository.existsByClientIdAndOrganizationId(uuid, orgId);
                    if (optionalBoolean.isPresent() && optionalBoolean.get()) {
                        RegisteredClient registeredClient = issuerAwareAuthorizationServerOperations.findById(issuer, id);
                        if (registeredClient != null) {
                            return Mono.just(registeredClientMapConverter.getMapObject(registeredClient));
                        }
                    }
                    return Mono.just(Map.of("error", "registeredClient not found with id:"+ id));
                });
    }


    // get count of clients associated with the logged-in user's default organization
    @GetMapping("/count/users")
    public Mono<Long> getClientCount() {
        LOG.info("get count of clients for logged-in user's default organization");
        return getDefaultOrganizationClientCount();
    }

    @GetMapping("/count/organizations/default")
    public Mono<Long> getDefaultOrganizationClientCount() {
        LOG.info("get count of clients for default organization");
        Pair<UUID, String> uuidTokenPair = UserIdUtil.getLoggedInUserId();
        UUID userId = uuidTokenPair.getFirst();

        return organizationWebClient.getDefaultOrganizationIdForUser(userId)
                .flatMap(orgId -> Mono.just(clientOrganizationRepository.countByOrganizationId(orgId)))
                .doOnNext(aLong -> LOG.info("found {} clients for organization", aLong));
    }
    /**
     * Get user's default organization (whether a subdomain like org1.authzger.com or a free tier one) or a id from settings
     * then show organization level clients.  Users can't login, only OrgAdmins can.
     * use the tokenized userId
     * @param pageable
     * @return
     */
    @GetMapping("/organizations")
    @ResponseStatus(HttpStatus.OK)
    public Mono<CustomRestPage<CustomPair<String, String>>> getClientsForLoggedInUserByTheirOrgId(Pageable pageable) {
        LOG.info("get clientIds for userId");

        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        final String issuer = issuerFromJwt(jwt);
        String userIdString = jwt.getClaim("userId");
        LOG.info("userId: {}", userIdString);
        UUID userId = UUID.fromString(userIdString);

        String accessToken = jwt.getTokenValue();
        List<CustomPair<String, String>> list = new ArrayList<>();

        return organizationWebClient.getDefaultOrganizationIdForUser(userId)
                .switchIfEmpty(Mono.error(new AuthenticationException("User does not have default organization-id")))
                .flatMap(orgId -> roleWebClient.isOrgAdminInOrgId(accessToken, userId, orgId).zipWith(Mono.just(orgId)))
                .flatMap(objects -> {
                    if (!objects.getT1()) {
                        return Mono.error(new AuthenticationException("user is not an OrgAdmin in organizationId : " + objects.getT2()));
                    } else {
                        return Mono.just(objects.getT2());
                    }
                }).flatMap(orgId -> {
                    List<ClientOrganization> clientOrganizationList = clientOrganizationRepository.findByOrganizationId(orgId, pageable);

                    LOG.info("defaultOrgId: {}, clientOrganizationList: {}", orgId, clientOrganizationList.size());
                    clientOrganizationList.forEach(clientOrganization -> {
                        RegisteredClient registeredClient = issuerAwareAuthorizationServerOperations.findById(issuer, clientOrganization.getClientId().toString());
                        if (registeredClient != null) {
                            list.add(CustomPair.of(registeredClient.getId(), registeredClient.getClientId()));
                        }
                    });
                    LOG.info("list.size {}, list {}", list.size(), list);
                    LOG.info("pageable: {}", pageable);

                    return Mono.just(new CustomRestPage<>(list, pageable.getPageNumber(), pageable.getPageSize(), clientOrganizationRepository.countByOrganizationId(orgId)));
                }).switchIfEmpty(Mono.just(new CustomRestPage<>(list, pageable.getPageNumber(), pageable.getPageSize(), 0))).flatMap(Mono::just);
    }

    @PutMapping
    @ResponseStatus(HttpStatus.OK)
    public Mono<Map<String, Object>> update(@RequestBody Map<String, Object> map) {
        if (map == null) {
            return Mono.error(new BadRequestException("client request body is required"));
        }
        if (map.get("id") == null) {
            LOG.error("map does not contain client id");
            return Mono.error(new BadRequestException("No client id"));
        }

        LOG.info("update client with id: {}", map.get("id"));
        LOG.info("clientIdIssuedAt: {}", map.get("clientIdIssuedAt"));

        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        final String issuer = issuerFromJwt(jwt);

        String accessToken = jwt.getTokenValue();
        LOG.info("authenticated client update request received");

        RegisteredClient fromDb = issuerAwareAuthorizationServerOperations.findById(issuer, map.get("id").toString());
        if (fromDb == null) {
            LOG.error("There is no RegisteredClient found with id: {}", map.get("id"));
            return Mono.error(new BadRequestException("Registered client not found with id: "+map.get("id")));
        }

        map.put("id", fromDb.getId());
        final String newClientSecret = (String) map.get("newClientSecret");
        LOG.info("new client secret supplied: {}", newClientSecret != null && !newClientSecret.isEmpty());

        if (newClientSecret != null && !newClientSecret.isEmpty()) {

            LOG.info("replacing stored client secret");
            final String encodedPassword = passwordEncoder.encode(newClientSecret);
            map.put("clientSecret", encodedPassword);
            LOG.info("encoded replacement client secret before storage");
        }

        LOG.info("loaded registered client id {}, authorizationCodeTimeToLive seconds: {}",
                fromDb.getId(), fromDb.getTokenSettings().getAuthorizationCodeTimeToLive().getSeconds());

        try {
            RegisteredClient registeredClient = registeredClientMapConverter.build(map);

            LOG.info("built registered client id {}, authorizationCodeTimeToLive in seconds: {}",
                    registeredClient.getId(), registeredClient.getTokenSettings().getAuthorizationCodeTimeToLive().getSeconds());

            issuerAwareAuthorizationServerOperations.save(issuer, registeredClient);

            LOG.info("saved registeredClient entity");
            RegisteredClient registeredClient1 = issuerAwareAuthorizationServerOperations.findById(issuer, registeredClient.getId());
            return Mono.just(registeredClientMapConverter.getMapObject(registeredClient1));
        }
        catch (Exception e) {
            LOG.error("exception can occur if user does not fill right data {}", e.getMessage(), e);
            return Mono.error(new BadRequestException("update failed: "+ e.getMessage()));
        }
    }

    @DeleteMapping("{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public Mono<Void> delete(@PathVariable("id") String id) {
        LOG.info("delete client with id: {}", id);

        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        final String issuer = issuerFromJwt(jwt);

        String accessToken = jwt.getTokenValue();
        LOG.info("delete client requested by userId {}", (Object) jwt.getClaim("userId"));

        RegisteredClient registeredClient = issuerAwareAuthorizationServerOperations.findById(issuer, id);

        if (registeredClient == null) {
            LOG.error("client not found with id: {}", id);
            return Mono.empty();
        }
        else {
            LOG.info("deleting by id: {}", registeredClient.getId());
            issuerAwareAuthorizationServerOperations.deleteById(issuer, registeredClient.getId());

            LOG.info("delete client associated with organization {}", registeredClient.getClientId());
            clientOrganizationRepository.deleteByClientId(UUID.fromString(registeredClient.getId()));

        }
        return Mono.empty();
    }


    /**
     * delete clients, clientorganization, clientuser part of delete my info
     * delete client-user relationship for logged-in userId
     * @return
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public Mono<Map<String, String>> delete() {
        LOG.info("delete client-user relationship for logged-in user-id");

        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        final String issuer = issuerFromJwt(jwt);
        String userIdString = jwt.getClaim("userId");
        LOG.info("delete client-user for userIdString: {}", userIdString);

        UUID userId = UUID.fromString(userIdString);
        String accessToken = jwt.getTokenValue();

        return organizationWebClient.getDefaultOrganizationIdForUser(userId)
                .switchIfEmpty(Mono.error(new AuthenticationException("User does not have default organization-id")))
                .flatMap(orgId -> roleWebClient.isOrgAdminInOrgId(accessToken, userId, orgId).zipWith(Mono.just(orgId)))
                .flatMap(objects -> {
                    if (!objects.getT1()) {
                        return Mono.error(new AuthenticationException("user is not an OrgAdmin in organizationId : " + objects.getT2()));
                    }
                    UUID orgId = objects.getT2();
                    //check if there are rows where there are roles set by this orgId -- indicating there are users with roles for some client
                    //basically we don't want to delete any client if there are roles assigned to this org
                    return roleWebClient.getCountOfUsersWithUserClientOrganizationRoleByOrgId(accessToken, orgId).zipWith(Mono.just(orgId));
                })
                .flatMap(objects -> {
                    UUID orgId = objects.getT2();
                    LOG.info("count of users with user-client-org-role by orgId: {}", objects.getT1());
                    int count = objects.getT1();
                    if (count <= 0) {
                        //there are no roles setup for any client, org -- this should delete all rows with this orgId
                        transactionTemplate.executeWithoutResult(status -> {
                            deleteCli(issuer, orgId);
                        });

                        return Mono.just(Map.of("message", "delete client"));
                    } else {
                        return Mono.just(Map.of("error", "there are currently roles associated to organization"));
                    }
                });
   }

    private void deleteCli(String issuer, UUID orgId) {
        List<ClientOrganization> clientOrganizationList = clientOrganizationRepository.findByOrganizationId(orgId);

        LOG.info("deleting clientOrganization by organizationId");
        clientOrganizationRepository.deleteByOrganizationId(orgId);

        LOG.info("iterate {}", clientOrganizationList.size());
        //then delete all clients associated to this org
        clientOrganizationList.forEach(clientOrganization ->
        {
            LOG.info("delete client: {}", clientOrganization.getClientId());
            issuerAwareAuthorizationServerOperations.deleteById(issuer, clientOrganization.getClientId().toString());
        });
        LOG.info("done");
    }

    private String issuerFromJwt(Jwt jwt) {
        Object issuer = jwt.getClaims().get("iss");
        if (issuer != null && !issuer.toString().isBlank()) {
            LOG.info("using jwt issuer '{}' for client repository lookup", issuer);
            return issuer.toString();
        }
        String requestIssuer = issuerAwareAuthorizationServerOperations.currentIssuer();
        LOG.warn("jwt issuer missing, falling back to request issuer '{}'", requestIssuer);
        return requestIssuer;
    }
}
