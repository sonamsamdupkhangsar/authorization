package me.sonam.auth.rest;


import jakarta.ws.rs.BadRequestException;
import me.sonam.auth.jpa.entity.Client;
import me.sonam.auth.jpa.entity.ClientOwner;
import me.sonam.auth.jpa.entity.ClientUser;
import me.sonam.auth.jpa.repo.*;
import me.sonam.auth.rest.util.MyPair;
import me.sonam.auth.service.JpaRegisteredClientRepository;
import me.sonam.auth.util.TokenRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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

    public ClientRestService(JpaRegisteredClientRepository jpaRegisteredClientRepository,
                             ClientRepository clientRepository, PasswordEncoder passwordEncoder) {
        this.jpaRegisteredClientRepository = jpaRegisteredClientRepository;
        this.clientRepository = clientRepository;
        this.passwordEncoder = passwordEncoder;
        LOG.info("initialized clientRestService");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createNew(@RequestBody Map<String, Object> map) {
        LOG.info("create new client with map: {}", map);

        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String userIdString = jwt.getClaim("userId");

        String accessToken = jwt.getTokenValue();

        UUID userId = UUID.fromString(userIdString);

        LOG.info("userId {}, accessToken: {}", userId, accessToken);

        if (jpaRegisteredClientRepository.findByClientId(map.get("clientId").toString()) != null) {
            LOG.error("clientId already exists, do an update");
            RegisteredClient registeredClient = jpaRegisteredClientRepository.findByClientId(map.get("clientId").toString());
            if (registeredClient != null) {
                return jpaRegisteredClientRepository.getMapObject(registeredClient);
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

        return mapToReturn;
    }

    @RequestMapping(value = "/client-id/{clientId}",  method=RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public Map<String, Object> getByClientId(@PathVariable("clientId") String clientId) {
        LOG.info("get by clientId: {}", clientId);

        RegisteredClient registeredClient = jpaRegisteredClientRepository.findByClientId(clientId);
        return jpaRegisteredClientRepository.getMapObject(registeredClient);
    }

    @GetMapping(value = "{id}")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, Object> getClientById(@PathVariable("id") String id) {
        LOG.info("get client by id: {}", id);

        RegisteredClient registeredClient = jpaRegisteredClientRepository.findById(id);
        if (registeredClient != null) {
            return jpaRegisteredClientRepository.getMapObject(registeredClient);
        }
        return Map.of("error", "registeredClient not found with id:"+ id);
    }


    @GetMapping("/users/{id}")
    @ResponseStatus(HttpStatus.OK)
    public Page<MyPair<String, String>> getClientsOwnedByUserId(@PathVariable("id") UUID userId, Pageable pageable) {
        LOG.info("get clientIds for userId: {}", userId);

        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String userIdString = jwt.getClaim("userId");

        LOG.info("userIdString: {}, and userId: {}",userIdString, userId);
        LOG.info("jwt.getTokenValue: {}", jwt.getTokenValue());

        List<MyPair<String, String>> list = new ArrayList<>();

        clientOwnerRepository.findByUserId(userId, pageable).forEach(clientOwner -> {
                    Optional<Client> optionalClient = clientRepository.findById(clientOwner.getClientId().toString());
                    if (optionalClient.isEmpty()) {
                        LOG.error("client not found ClientRepository by clientId: '{}'", clientOwner.getClientId());
                    }
                    optionalClient.ifPresent(client ->
                            list.add(new MyPair<>(client.getId(), client.getClientId())));
                }
        );

        LOG.info("list of clientId pairs: {}", list);
        return new PageImpl<>(list, pageable, clientOwnerRepository.countByUserId(userId));
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

        verifyUserOwnsClientId(UUID.fromString(fromDb.getId()), UUID.fromString(map.get("userId").toString()));

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

    @DeleteMapping("{id}/user-id/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public Mono<Void> delete(@PathVariable("id") String id, @PathVariable("userId") UUID userId) {
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
            verifyUserOwnsClientId(UUID.fromString(id), userId);

            LOG.info("deleting by id: {}", registeredClient.getId());
            clientRepository.deleteById(registeredClient.getId());

            long rows = clientUserRepository.deleteByClientId(UUID.fromString(registeredClient.getId()));
            LOG.info("delete clientUser by clientId: {} affected rows: {}", registeredClient.getClientId(), rows);
            clientOrganizationRepository.deleteByClientId(UUID.fromString(registeredClient.getId()));

            clientUserRepository.deleteByClientId(UUID.fromString(registeredClient.getId()));
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



    private void checkClientIdAndLoggedInUser(String clientId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        final String authId = authentication.getName();
        LOG.info("checking logged-in user: {} and clientId match: {}", authId.toString(), clientId);


        if(authId.equals(clientId)) {
            LOG.info("logged-in principal name and clientId matches");
        }
        else {
            LOG.error("logged-in principal and clientId does not match");
            throw new BadRequestException("Logged-in user and clientId must match");
        }
    }

    private void verifyUserOwnsClientId(UUID clientId, UUID userId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        Optional<Boolean> booleanOptional = clientUserRepository.existsByClientIdAndUserId(clientId, userId);
        if(booleanOptional.isEmpty()) {
            LOG.error("logged-in user is not associated with clientId");
            throw new BadRequestException("Logged-in user and clientId must match");
        }

        booleanOptional.ifPresent(aBoolean -> {
            if (!aBoolean) {
                LOG.error("logged-in userId {} is not associated with clientId: {}", userId, clientId);
                throw new BadRequestException("Logged-in user and clientId must match");
            }
            else {
                LOG.info("there is a relationship between the clientId and userId: {}", aBoolean);
            }
        });
    }
}
