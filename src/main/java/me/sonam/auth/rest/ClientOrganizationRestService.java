package me.sonam.auth.rest;

import jakarta.transaction.Transactional;


import jakarta.ws.rs.Produces;
import me.sonam.auth.jpa.entity.ClientOrganization;
import me.sonam.auth.jpa.repo.ClientOrganizationRepository;
import me.sonam.auth.rest.util.MyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/clients")
public class ClientOrganizationRestService {
    private static final Logger LOG = LoggerFactory.getLogger(ClientOrganizationRestService.class);

    private final ClientOrganizationRepository clientOrganizationRepository;

    public ClientOrganizationRestService(ClientOrganizationRepository clientOrganizationRepository) {
        this.clientOrganizationRepository = clientOrganizationRepository;
    }

    @Transactional
    @PostMapping("/organizations")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<String> addClientToOrganization(@RequestBody ClientOrganization clientOrganization) {
        LOG.info("add client {} to organization: {}", clientOrganization.getClientId(), clientOrganization.getOrganizationId());
        LOG.info("user is {}", SecurityContextHolder.getContext().getAuthentication().getName());
        String response;

        clientOrganizationRepository.deleteByClientId(clientOrganization.getClientId()).ifPresent(aLong -> {
            LOG.info("deleted {} rows matching clientId in clientOrganizationRepository before adding", aLong);
        });

        Optional<Boolean> optionalBoolean = clientOrganizationRepository.existsByClientIdAndOrganizationId(
                clientOrganization.getClientId(), clientOrganization.getOrganizationId());
        if (optionalBoolean.isPresent()) {
            if (!optionalBoolean.get()) {
                clientOrganizationRepository.save(new ClientOrganization(clientOrganization.getClientId(), clientOrganization.getOrganizationId()));
                response = "clientOrganization saved";
                LOG.info(response);
                return Mono.just(response);
            }
            else {
                response = "there is already a row in ClientOrganization with clientId and organizationId";
                LOG.info(response);
                return Mono.just(response);
            }
        }
        else {
            response = "there is no record of clientId and organizationId in clientOrganization";
            LOG.error("there is no record of clientId {} and organizationId {} in ClientOrganization",
                    clientOrganization.getClientId(), clientOrganization.getOrganizationId());
            return Mono.just(response);
        }
    }

    @PutMapping("/organizations")
    @Produces(MediaType.APPLICATION_JSON_VALUE)
    public Mono<ClientOrganization> findRowWithClientIdAndOrganizationId(@RequestBody MyPair<UUID, List<UUID>> myPair) {
        LOG.info("find row with clientId and organizationId");

        UUID clientsId = myPair.getKey();
        List<UUID> organizationIds = myPair.getValue();

        Optional<ClientOrganization> clientOrganizationOptional =
                clientOrganizationRepository.findByClientIdAndOrganizationIdIn(clientsId, organizationIds);
        LOG.info("found clientOrganization?: {}", clientOrganizationOptional.isPresent());

        return clientOrganizationOptional.map(Mono::just).orElseGet(Mono::empty);
    }

    @Transactional
    @DeleteMapping("/{id}/organizations/{organizationId}")
    public Mono<String> deleteClientOrganizationRow(@PathVariable("id")UUID clientId,
                                                    @PathVariable("organizationId")UUID organizationId) {
        LOG.info("delete clientId {} and organizationId {} row", clientId, organizationId);

        clientOrganizationRepository.deleteByClientIdAndOrganizationId(
                clientId, organizationId).ifPresent(aLong -> LOG.info("delete rows: {}", aLong));
        return Mono.just("deleted clientId OrganizationId row");
    }




    @GetMapping("/{id}/organizations/id")
    @ResponseStatus(HttpStatus.OK)
    public Mono<UUID> getOrganizationIdForClientId(@PathVariable("id")UUID id) {
        LOG.info("get organization id associated for this client.id: {}", id);

        Optional<ClientOrganization> clientOrganizationOptional = clientOrganizationRepository.findByClientId(id);
        return clientOrganizationOptional.map(clientOrganization -> Mono.just(clientOrganization.getOrganizationId())).orElseGet(Mono::empty);
    }
}
