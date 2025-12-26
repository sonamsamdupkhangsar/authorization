package me.sonam.auth;


import me.sonam.auth.jpa.entity.ClientUser;
import me.sonam.auth.jpa.entity.ClientUserId;
import me.sonam.auth.jpa.repo.HClientUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
@EnableAutoConfiguration
@ExtendWith(SpringExtension.class)
@SpringBootTest( webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class ClientUserRepoTest {
    private static final Logger LOG = LoggerFactory.getLogger(ClientUserRepoTest.class);

    @Autowired
    private HClientUserRepository clientUserRepository;


    @Test
    public void save() {
        LOG.info("create a clientUser and save it");
        UUID userId = UUID.randomUUID();
        //final String clientClientId = "nextjs-client";
        final UUID clientId = UUID.randomUUID();

        ClientUser clientUser = new ClientUser(clientId, userId);
        ClientUser clientUser1 = clientUserRepository.save(clientUser);
        LOG.info("saved clientUser1: {}", clientUser1);

        LOG.info("delete the clientUser");
        clientUserRepository.deleteById(new ClientUserId(clientId, userId));
        assertThat(clientUserRepository.findByClientIdAndUserId(clientId, userId)).isEmpty();
    }

    @Test
    @Transactional
    public void deleteByClientId() {
        LOG.info("create a clientUser and save it");
        UUID userId = UUID.randomUUID();
        final String clientClientId = "nextjs-client";
        final UUID clientId = UUID.randomUUID();

        ClientUser clientUser = new ClientUser(clientId, userId);
        ClientUser clientUser1 = clientUserRepository.save(clientUser);
        LOG.info("saved clientUser1: {}", clientUser1);

        LOG.info("delete tby clientId");
        clientUserRepository.deleteByClientId(clientId);
        assertThat(clientUserRepository.findByClientIdAndUserId(clientId, userId)).isEmpty();
    }

    @Test
    public void saveMultiple() {
        LOG.info("create multiple clientUser with different clientNames");
        UUID userId = UUID.randomUUID();
        //final String clientId = "nextjs-client";
        final UUID clientId = UUID.randomUUID();

        ClientUser clientUser = new ClientUser(clientId, userId);
        ClientUser clientUser1 = clientUserRepository.save(clientUser);
        LOG.info("saved clientUser1: {}", clientUser1);

        LOG.info("try saving the same clientUser");
        clientUserRepository.save(clientUser);
        LOG.info("saved multiple clients for a user");
    }

    @Test
    public void getByUserId() {
        UUID userId = UUID.randomUUID();
      //  String clientId = "nextjs-client";
         UUID clientId1 = UUID.randomUUID();

        ClientUser clientUser = new ClientUser(clientId1, userId);
        ClientUser clientUser1 = clientUserRepository.save(clientUser);
        LOG.info("saved clientUser1: {}", clientUser1);


        UUID clientId2 = UUID.randomUUID(); //"another-client";

        clientUser = new ClientUser(clientId2, userId);
        clientUserRepository.save(clientUser);


        LOG.info("get clientUser list by userId: {}", userId);
        List<ClientUser> list = clientUserRepository.findByUserId(userId, PageRequest.of(0, 100));
        LOG.info("found {} clientUsers for userId", list.size());
        assertThat(list.contains(new ClientUser(clientId1, userId))).isTrue();
        assertThat(list.contains(new ClientUser(clientId2, userId))).isTrue();
    }

    //this should fail for same composite ids
    @Test
    public void trySaving2IdenticalClientUser() {
        UUID userId = UUID.randomUUID();
        //String clientId = "nextjs-client";
        UUID clientId = UUID.randomUUID();

        LOG.info("save once");
        ClientUser clientUser1 = clientUserRepository.save(new ClientUser(clientId, userId));
        LOG.info("saved clientUser1: {}", clientUser1);

        LOG.info("attempt another create with same clientId and userId");
        clientUserRepository.save(new ClientUser(clientId, userId));


        LOG.info("get clientUser list by userId: {}", userId);
        LOG.info("should only have 1 entry when attempting to insert two");
        List<ClientUser> list = clientUserRepository.findByUserId(userId, PageRequest.of(0, 100));
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.contains(new ClientUser(clientId, userId))).isTrue();

    }


}
