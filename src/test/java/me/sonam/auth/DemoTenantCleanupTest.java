package me.sonam.auth;

import me.sonam.auth.config.DemoCleanupProperties;
import me.sonam.auth.init.DemoTenantCleanup;
import me.sonam.auth.jpa.entity.Client;
import me.sonam.auth.jpa.repo.AuthorizationConsentRepository;
import me.sonam.auth.jpa.repo.AuthorizationRepository;
import me.sonam.auth.jpa.repo.ClientOrganizationRepository;
import me.sonam.auth.jpa.repo.ClientRepository;
import me.sonam.auth.jpa.repo.HClientUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DemoTenantCleanupTest {

    @Test
    void cleanupDeletesDemoClientsButPreservesConfiguredClientIds() {
        String tenantHost = "demo.openissuer.com";
        UUID clientToDeleteId = UUID.randomUUID();
        UUID preservedClientId = UUID.randomUUID();

        Client clientToDelete = client(clientToDeleteId, tenantHost, "temporary-demo-client");
        Client preservedClient = client(preservedClientId, tenantHost, "nextauth-demo");

        DemoCleanupProperties properties = new DemoCleanupProperties();
        properties.setTenantHost(tenantHost);
        properties.setPreservedClientIds("nextauth-demo");

        ClientRepository clientRepository = mock(ClientRepository.class);
        AuthorizationRepository authorizationRepository = mock(AuthorizationRepository.class);
        AuthorizationConsentRepository authorizationConsentRepository = mock(AuthorizationConsentRepository.class);
        ClientOrganizationRepository clientOrganizationRepository = mock(ClientOrganizationRepository.class);
        HClientUserRepository clientUserRepository = mock(HClientUserRepository.class);

        when(clientRepository.findByTenantId(tenantHost)).thenReturn(List.of(clientToDelete, preservedClient));
        when(authorizationRepository.deleteByTenantIdAndRegisteredClientId(tenantHost, clientToDeleteId.toString()))
                .thenReturn(2L);
        when(authorizationConsentRepository.deleteByTenantIdAndRegisteredClientId(tenantHost, clientToDeleteId.toString()))
                .thenReturn(1L);
        when(clientUserRepository.deleteByClientId(clientToDeleteId)).thenReturn(3L);
        when(clientOrganizationRepository.deleteByClientId(clientToDeleteId)).thenReturn(Optional.of(1L));

        DemoTenantCleanup demoTenantCleanup = new DemoTenantCleanup(
                properties,
                clientRepository,
                authorizationRepository,
                authorizationConsentRepository,
                clientOrganizationRepository,
                clientUserRepository,
                mock(TaskScheduler.class));

        demoTenantCleanup.cleanupDemoTenantClients();

        verify(clientRepository).deleteById(clientToDeleteId.toString());
        verify(clientRepository, never()).deleteById(preservedClientId.toString());
        verify(authorizationRepository).deleteByTenantIdAndRegisteredClientId(tenantHost, clientToDeleteId.toString());
        verify(authorizationRepository, never()).deleteByTenantIdAndRegisteredClientId(tenantHost, preservedClientId.toString());
        verify(clientUserRepository).deleteByClientId(clientToDeleteId);
        verify(clientOrganizationRepository).deleteByClientId(clientToDeleteId);
    }

    private Client client(UUID id, String tenantHost, String clientId) {
        Client client = new Client();
        client.setId(id.toString());
        client.setTenantId(tenantHost);
        client.setClientId(clientId);
        return client;
    }
}
