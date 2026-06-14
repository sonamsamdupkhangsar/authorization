package me.sonam.auth.mfa.passkey;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class TenantAwarePublicKeyCredentialUserEntityRepository implements PublicKeyCredentialUserEntityRepository {
    private final CurrentTenantJdbcTemplateResolver jdbcTemplateResolver;
    private final Map<JdbcTemplate, JdbcPublicKeyCredentialUserEntityRepository> repositories = new ConcurrentHashMap<>();

    public TenantAwarePublicKeyCredentialUserEntityRepository(CurrentTenantJdbcTemplateResolver jdbcTemplateResolver) {
        this.jdbcTemplateResolver = jdbcTemplateResolver;
    }

    @Override
    public PublicKeyCredentialUserEntity findById(Bytes id) {
        return delegate().findById(id);
    }

    @Override
    public PublicKeyCredentialUserEntity findByUsername(String username) {
        return delegate().findByUsername(username);
    }

    @Override
    public void save(PublicKeyCredentialUserEntity userEntity) {
        delegate().save(userEntity);
    }

    @Override
    public void delete(Bytes id) {
        delegate().delete(id);
    }

    private JdbcPublicKeyCredentialUserEntityRepository delegate() {
        JdbcTemplate jdbcTemplate = jdbcTemplateResolver.resolve();
        return repositories.computeIfAbsent(jdbcTemplate, JdbcPublicKeyCredentialUserEntityRepository::new);
    }
}
