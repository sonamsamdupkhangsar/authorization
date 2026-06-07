package me.sonam.auth;

import com.nimbusds.jose.jwk.JWKSet;
import me.sonam.auth.multitenancy.PersistentJwkSetStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class PersistentJwkSetStoreIntegTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PersistentJwkSetStore persistentJwkSetStore;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DROP TABLE oauth2_jwk_set IF EXISTS");
        persistentJwkSetStore.initializeSchema(jdbcTemplate);
    }

    @Test
    void loadOrCreateReturnsPersistedKeySet() {
        JWKSet first = persistentJwkSetStore.loadOrCreate(jdbcTemplate);
        JWKSet second = persistentJwkSetStore.loadOrCreate(jdbcTemplate);

        assertThat(first.getKeys()).hasSize(1);
        assertThat(second.getKeys()).hasSize(1);
        assertThat(first.getKeys().get(0).getKeyID()).isEqualTo(second.getKeys().get(0).getKeyID());
    }
}
