package me.sonam.auth;

import me.sonam.auth.mfa.passkey.PasskeyMfaService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PasskeyMfaServiceTest {

    private final PublicKeyCredentialUserEntityRepository userEntityRepository =
            mock(PublicKeyCredentialUserEntityRepository.class);
    private final UserCredentialRepository userCredentialRepository = mock(UserCredentialRepository.class);
    private final RequestCache requestCache = mock(RequestCache.class);
    private final PasskeyMfaService passkeyMfaService =
            new PasskeyMfaService(userEntityRepository, userCredentialRepository, requestCache);

    @Test
    void requiresPasskeyMfaWhenUserHasRegisteredCredentials() {
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated("user@example.com", "password", List.of());
        PublicKeyCredentialUserEntity userEntity = mock(PublicKeyCredentialUserEntity.class);

        when(userEntityRepository.findByUsername("user@example.com")).thenReturn(userEntity);
        when(userEntity.getId()).thenReturn(new Bytes(new byte[] { 1 }));
        when(userCredentialRepository.findByUserId(userEntity.getId())).thenReturn(List.of(mock(CredentialRecord.class)));

        assertThat(passkeyMfaService.requiresPasskeyMfa(authentication)).isTrue();
    }

    @Test
    void doesNotRequirePasskeyMfaWhenUserHasNoPasskeyUserEntity() {
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated("user@example.com", "password", List.of());

        when(userEntityRepository.findByUsername("user@example.com")).thenReturn(null);

        assertThat(passkeyMfaService.requiresPasskeyMfa(authentication)).isFalse();
    }

    @Test
    void withPasskeyFactorAddsWebauthnAuthority() {
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                "user@example.com",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_USER"),
                        FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY)));

        UsernamePasswordAuthenticationToken result =
                (UsernamePasswordAuthenticationToken) passkeyMfaService.withPasskeyFactor(authentication);

        assertThat(result.getAuthorities())
                .extracting("authority")
                .contains("ROLE_USER",
                        FactorGrantedAuthority.PASSWORD_AUTHORITY,
                        FactorGrantedAuthority.WEBAUTHN_AUTHORITY);
    }
}
