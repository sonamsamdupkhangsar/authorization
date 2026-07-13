package me.sonam.auth;

import me.sonam.auth.config.ClientLimitProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClientLimitPropertiesTest {

    @Test
    void returnsHostSpecificLimitForIssuerUrl() {
        ClientLimitProperties properties = new ClientLimitProperties();
        properties.setDefaultMaxClients(5);
        properties.getHosts().put("demo.openissuer.com", 2);
        properties.getHosts().put("free.openissuer.com", 2);
        properties.getHosts().put("demo.openissuer.test", 2);
        properties.getHosts().put("free.openissuer.test", 2);

        assertThat(properties.maxClientsForIssuer("https://demo.openissuer.com")).isEqualTo(2);
        assertThat(properties.maxClientsForIssuer("https://free.openissuer.com/oauth2/token")).isEqualTo(2);
        assertThat(properties.maxClientsForIssuer("https://demo.openissuer.test")).isEqualTo(2);
        assertThat(properties.maxClientsForIssuer("https://free.openissuer.test/oauth2/token")).isEqualTo(2);
    }

    @Test
    void returnsDefaultLimitWhenIssuerHasNoOverride() {
        ClientLimitProperties properties = new ClientLimitProperties();
        properties.setDefaultMaxClients(5);
        properties.getHosts().put("demo.openissuer.com", 2);

        assertThat(properties.maxClientsForIssuer("https://business1.openissuer.com")).isEqualTo(5);
        assertThat(properties.maxClientsForIssuer("authorization-server.main.svc.cluster.local")).isEqualTo(5);
    }
}
