package me.sonam.auth.mfa.passkey;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class TenantAwareUserCredentialRepository implements UserCredentialRepository {
    private final CurrentTenantJdbcTemplateResolver jdbcTemplateResolver;
    private final Map<JdbcTemplate, JdbcUserCredentialRepository> repositories = new ConcurrentHashMap<>();

    public TenantAwareUserCredentialRepository(CurrentTenantJdbcTemplateResolver jdbcTemplateResolver) {
        this.jdbcTemplateResolver = jdbcTemplateResolver;
    }

    @Override
    public void delete(Bytes credentialId) {
        delegate().delete(credentialId);
    }

    @Override
    public void save(CredentialRecord credentialRecord) {
        delegate().save(credentialRecord);
    }

    @Override
    public CredentialRecord findByCredentialId(Bytes credentialId) {
        return delegate().findByCredentialId(credentialId);
    }

    @Override
    public List<CredentialRecord> findByUserId(Bytes userId) {
        return delegate().findByUserId(userId);
    }

    private JdbcUserCredentialRepository delegate() {
        JdbcTemplate jdbcTemplate = jdbcTemplateResolver.resolve();
        return repositories.computeIfAbsent(jdbcTemplate, JdbcUserCredentialRepository::new);
    }
}
