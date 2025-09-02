package me.sonam.auth.rest;


import jakarta.ws.rs.BadRequestException;
import me.sonam.auth.jpa.entity.Client;
import me.sonam.auth.jpa.entity.ClientOrganization;
import me.sonam.auth.jpa.entity.ClientOwner;
import me.sonam.auth.jpa.entity.ClientUser;
import me.sonam.auth.jpa.repo.*;
import me.sonam.auth.rest.util.MyPair;
import me.sonam.auth.service.JpaRegisteredClientRepository;
import me.sonam.auth.service.exception.MaxCountException;
import me.sonam.auth.util.TokenRequestFilter;
import me.sonam.auth.webclient.RoleWebClient;
import me.sonam.auth.webclient.SettingWebClient;
import org.apache.tomcat.websocket.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
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

    private final ClientRepository clientRepository;
    private final JpaRegisteredClientRepository jpaRegisteredClientRepository;

    @Autowired
    private HClientUserRepository clientUserRepository;

    @Autowired
    private ClientOrganizationRepository clientOrganizationRepository;

    @Autowired
    private ClientOwnerRepository clientOwnerRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TokenRequestFilter tokenRequestFilter;

    @Autowired
    private final RoleWebClient roleWebClient;

    @Autowired
    private SettingWebClient settingWebClient;

    @Value("${maxClients}")
    private int maxClients;

    public ClientRestService(JpaRegisteredClientRepository jpaRegisteredClientRepository,
                             ClientRepository clientRepository, PasswordEncoder passwordEncoder, RoleWebClient roleWebClient,
                             SettingWebClient settingWebClient) {
        this.jpaRegisteredClientRepository = jpaRegisteredClientRepository;
        this.clientRepository = clientRepository;
        this.passwordEncoder = passwordEncoder;
        this.roleWebClient = roleWebClient;
        this.settingWebClient = settingWebClient;
        LOG.info("initialized clientRestService");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Map<String, Object>> createNew(@RequestBody Map<String, Object> map) {
        LOG.info("create new client with map: {}", map);

        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String userIdString = jwt.getClaim("userId");

        LOG.info("userId: {}", userIdString);

        String accessToken = jwt.getTokenValue();

        UUID userId = UUID.fromString(userIdString);

        LOG.info("userId {}, accessToken: {}", userId, accessToken);


       return settingWebClient.getDefaultOrganization(accessToken)
               .switchIfEmpty(Mono.error(new AuthenticationException("User does not have default organization-id")))
               .flatMap(orgId -> roleWebClient.getSuperAdminOrgIds(accessToken, 0, 1)
                       .switchIfEmpty(Mono.error(new AuthenticationException("No super Admin org id found")))
                       .zipWith(Mono.just(orgId)))

               .flatMap(objects -> {
                   UUID organizationId = objects.getT2();

                   LOG.info("max number of clients count: {}", maxClients);
                   long clientCount = clientOrganizationRepository.countByOrganizationId(organizationId);
                   LOG.info("clientCount: {}", clientCount);
                   if (clientCount >= maxClients) {
                       LOG.info("client row count max exceeded by organizationId: {}", clientCount);
                       return Mono.error(new MaxCountException("Max number of clients reached"));
                   }
                   return Mono.just(objects);
               })
               .flatMap(objects -> {
                    LOG.error("orgId: {}", objects.getT2());

                   if (objects.getT1().isEmpty()) {
                    return Mono.error(new AuthenticationException("no superadmin org id found"));
                   }
                   UUID superAdminOrgId = objects.getT1().getFirst(); //just get first only as we store 1 superadmin only
                   UUID organizationId = objects.getT2();
                   if (superAdminOrgId.equals(organizationId)) {
                       LOG.info("superAdminOrgId and orgId matches: {}, {}", superAdminOrgId, objects.getT2());
                       return save(map, userId, objects.getT2());
                   }
                   else {
                       LOG.info("throwing exception when superAdminOrgId and defaultOrgId does not match");
                       return Mono.error(new BadRequestException("superAdminOrgId and defaultOrgId does not match"));
                   }
                });
    }

    private Mono<Map<String, Object>> save(Map<String, Object> map, UUID userId, UUID organizationId) {
        if (jpaRegisteredClientRepository.findByClientId(map.get("clientId").toString()) != null) {
            LOG.error("clientId already exists, do an update");
            RegisteredClient registeredClient = jpaRegisteredClientRepository.findByClientId(map.get("clientId").toString());
            if (registeredClient != null) {
                return Mono.just(jpaRegisteredClientRepository.getMapObject(registeredClient));
            }
            throw new BadRequestException("clientId already exists but not able to pull from repository");
        }

        String encodedPassword = passwordEncoder.encode((String)map.get("clientSecret"));

        LOG.info("saving bcrypt encodedPassword {} as the clientSecret", encodedPassword);
        map.put("clientSecret", encodedPassword);

        RegisteredClient registeredClient = jpaRegisteredClientRepository.build(map);

        LOG.debug("built registeredClient from map: {}", registeredClient);

        jpaRegisteredClientRepository.save(registeredClient);
        RegisteredClient savedRedisteredClient = jpaRegisteredClientRepository.findById(registeredClient.getId());
        LOG.info("saved registeredClient: {}", savedRedisteredClient);

        LOG.info("saved registeredClient.id: {}", registeredClient.getId());
        UUID clientId = UUID.fromString(registeredClient.getId());

        LOG.info("save clientUser relationship, userId: {}", map.get("userId"));
        clientUserRepository.save(new ClientUser(UUID.fromString(registeredClient.getId()),
                UUID.fromString(map.get("userId").toString())));

        Map<String, Object> mapToReturn = jpaRegisteredClientRepository.getMapObject(registeredClient);

        LOG.info("clientId: {}", clientId);

        clientOwnerRepository.save(new ClientOwner(clientId, userId));

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
        String accessToken = jwt.getTokenValue();

        return settingWebClient.getDefaultOrganization(accessToken)
                .flatMap(orgId -> {
                    RegisteredClient registeredClient = jpaRegisteredClientRepository.findByClientId(clientId);
                    if (registeredClient != null) {
                        UUID uuidClientId = UUID.fromString(registeredClient.getId());

                        Optional<Boolean> optionalBoolean = clientOrganizationRepository.
                                existsByClientIdAndOrganizationId(uuidClientId, orgId);
                        if (optionalBoolean.isPresent() && optionalBoolean.get()) {

                            return Mono.just(jpaRegisteredClientRepository.getMapObject(registeredClient));
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
        String accessToken = jwt.getTokenValue();

        return settingWebClient.getDefaultOrganization(accessToken)
                .flatMap(orgId -> {
                    LOG.info("orgId: '{}'", id);
                    UUID uuid = UUID.fromString(id);
                    Optional<Boolean> optionalBoolean = clientOrganizationRepository.existsByClientIdAndOrganizationId(uuid, orgId);
                    if (optionalBoolean.isPresent() && optionalBoolean.get()) {
                        RegisteredClient registeredClient = jpaRegisteredClientRepository.findById(id);
                        if (registeredClient != null) {
                            return Mono.just(jpaRegisteredClientRepository.getMapObject(registeredClient));
                        }
                    }
                    return Mono.just(Map.of("error", "registeredClient not found with id:"+ id));
                });
    }

    /**
     * Get user's default organization (whether a subdomain like org1.authzger.com or a free tier one) or a id from settings
     * then show organization level clients.  Users can't login, only superadmins can.
     * use the tokenized userId
     * @param pageable
     * @return
     */
    @GetMapping("/users")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Page<Pair<String, String>>> getClientsOwnedByUserId(Pageable pageable) {
        LOG.info("get clientIds for userId");

        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String userIdString = jwt.getClaim("userId");

        String accessToken = jwt.getTokenValue();
        List<Pair<String, String>> list = new ArrayList<>();

        return settingWebClient.getDefaultOrganization(accessToken)
                .flatMap(orgId -> {
                    LOG.info("transfer to organizationId for userId: {}", userIdString);
                    UUID userId = UUID.fromString(userIdString);

                    List<ClientUser> clientUserList = clientUserRepository.findByUserId(userId, pageable);
                    clientUserList.forEach(clientUser -> {
                        LOG.info("transfer client.id {} to organization: {}", clientUser.getClientId(), orgId);
                        Optional<Boolean> optionalBoolean = clientOrganizationRepository.existsByClientIdAndOrganizationId(clientUser.getClientId(), orgId);
                        if (optionalBoolean.isPresent() && optionalBoolean.get() == false) {
                            clientOrganizationRepository.save(new ClientOrganization(clientUser.getClientId(), orgId));
                            LOG.info("transfer client.id to organization", clientUser.getClientId());
                        }
                    });


                    List<ClientOrganization> clientOrganizationList = clientOrganizationRepository.findByOrganizationId(orgId, pageable);

                LOG.info("defaultOrgId: {}, clientOrganizationList: {}", orgId, clientOrganizationList.size());
                clientOrganizationList.forEach(clientOrganization -> {
                    Optional<Client> clientOptional = clientRepository.findById(clientOrganization.getClientId().toString());
                    clientOptional.ifPresent(client -> list.add(Pair.of(client.getId(), client.getClientId())));
                });
                LOG.info("list.size {}, list {}", list.size(), list);


            return Mono.just(new PageImpl<>(list, pageable, clientOrganizationRepository.countByOrganizationId(orgId)));
        }).switchIfEmpty(Mono.just(new PageImpl<>(list, pageable, 0))).flatMap(Mono::just);
    }

    @PutMapping
    @ResponseStatus(HttpStatus.OK)
    public Mono<Map<String, Object>> update(@RequestBody Map<String, Object> map) {
        LOG.info("update client using map: {}", map);
        LOG.info("clientIdIssuedAt: {}", map.get("clientIdIssuedAt"));

        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        String accessToken = jwt.getTokenValue();
        LOG.info("accessToken: {}", accessToken);

        if (map.get("id") == null) {
            LOG.error("map does not contain client id");
            return Mono.error(new BadRequestException("No client id"));
        }
        RegisteredClient fromDb = jpaRegisteredClientRepository.findById(map.get("id").toString());
        if (fromDb == null) {
            LOG.error("There is no RegisteredClient found with id: {}", map.get("id"));
            return Mono.error(new BadRequestException("Registered client not found with id: "+map.get("id")));
        }

        map.put("id", fromDb.getId());
        final String newClientSecret = (String) map.get("newClientSecret");
        LOG.info("using newClientSecret as clientSecret: {}", newClientSecret);

        if (newClientSecret != null && !newClientSecret.isEmpty()) {

            LOG.info("using new client secret to overwrite clientSecret: {}", map.get("newClientSecret"));
            final String encodedPassword = passwordEncoder.encode(newClientSecret);
            map.put("clientSecret", encodedPassword);
            LOG.info("adding encodePassword as clientSecret: {}", encodedPassword);
        }

        LOG.info("fromDb: {}, fromDb.ts.authCodeTimeToLive seconds: {}",
                fromDb, fromDb.getTokenSettings().getAuthorizationCodeTimeToLive().getSeconds());

        try {
            RegisteredClient registeredClient = jpaRegisteredClientRepository.build(map);

            LOG.info("built registeredClient from map, authorizationCodeTimeToLive in seconds: {}, registeredClient {}",
                    registeredClient.getTokenSettings().getAuthorizationCodeTimeToLive().getSeconds(), registeredClient);

            jpaRegisteredClientRepository.save(registeredClient);

            LOG.info("saved registeredClient entity");
            UUID clientId = UUID.fromString(registeredClient.getId());
            RegisteredClient registeredClient1 = jpaRegisteredClientRepository.findByClientId(registeredClient.getClientId());
            return Mono.just(jpaRegisteredClientRepository.getMapObject(registeredClient1));
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

        String accessToken = jwt.getTokenValue();
        LOG.info("userId: {}, accessToken: {}", jwt.getClaim("userId"), accessToken);

        RegisteredClient registeredClient = jpaRegisteredClientRepository.findById(id);

        if (registeredClient == null) {
            LOG.error("client not found with id: {}", id);
            return Mono.empty();
        }
        else {
            LOG.info("deleting by id: {}", registeredClient.getId());
            clientRepository.deleteById(registeredClient.getId());

            long rows = clientUserRepository.deleteByClientId(UUID.fromString(registeredClient.getId()));
            LOG.info("delete clientUser by clientId: {} affected rows: {}", registeredClient.getClientId(), rows);
            clientOrganizationRepository.deleteByClientId(UUID.fromString(registeredClient.getId()));

        }
        return Mono.empty();
    }


    /**
     * delete clients, clientorganization, clientowner, clientuser part of delete my info
     * @return
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public Mono<Map<String, String>> delete() {
        LOG.info("delete my clients");

        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        String userIdString = jwt.getClaim("userId");
        LOG.info("delete user data for userId: {}", userIdString);

        UUID userId = UUID.fromString(userIdString);

        String accessToken = jwt.getTokenValue();
        LOG.info("userId: {}, accessToken: {}", jwt.getClaim("userId"), accessToken);

        clientOwnerRepository.findByUserId(userId).forEach(clientOwner -> {
            clientUserRepository.deleteByClientId(clientOwner.getClientId());
            clientRepository.deleteById(clientOwner.getClientId().toString());
            clientOrganizationRepository.deleteByClientId(clientOwner.getClientId());
        });
        clientOwnerRepository.deleteByUserId(userId);
        return Mono.just(Map.of("message", "deleted user client data"));
    }
}
