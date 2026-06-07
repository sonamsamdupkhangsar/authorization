package me.sonam.auth.multitenancy;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.UUID;

@Component
public class PersistentJwkSetStore {
    private static final Logger LOG = LoggerFactory.getLogger(PersistentJwkSetStore.class);
    private static final String JWK_ROW_ID = "authorization-server";

    public void initializeSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE oauth2_jwk_set (
                    id VARCHAR(100) PRIMARY KEY,
                    jwk_set VARCHAR(8192) NOT NULL
                )
                """);
    }

    public JWKSet loadOrCreate(JdbcTemplate jdbcTemplate) {
        JWKSet jwkSet = load(jdbcTemplate);
        if (jwkSet != null) {
            return jwkSet;
        }

        JWKSet generatedJwkSet = new JWKSet(generateRsaJwk());
        String jwkSetJson = generatedJwkSet.toString(false);
        try {
            jdbcTemplate.update("INSERT INTO oauth2_jwk_set (id, jwk_set) VALUES (?, ?)", JWK_ROW_ID, jwkSetJson);
            LOG.info("persisted new JWK set");
            return generatedJwkSet;
        } catch (DuplicateKeyException ex) {
            LOG.info("JWK set already created by another initializer, loading stored value");
            JWKSet storedJwkSet = load(jdbcTemplate);
            if (storedJwkSet != null) {
                return storedJwkSet;
            }
            throw ex;
        }
    }

    private JWKSet load(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.query(
                "SELECT jwk_set FROM oauth2_jwk_set WHERE id = ?",
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    String jwkSetJson = rs.getString("jwk_set");
                    try {
                        return JWKSet.parse(jwkSetJson);
                    } catch (ParseException ex) {
                        throw new IllegalStateException("Failed to parse stored JWK set", ex);
                    }
                },
                JWK_ROW_ID
        );
    }

    private static RSAKey generateRsaJwk() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
            RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
