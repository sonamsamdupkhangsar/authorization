package me.sonam.auth.mfa.passkey;

import jakarta.servlet.http.HttpServletRequest;
import me.sonam.auth.service.HostOrganizationResolver;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialCreationOptionsRequest;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialRequestOptionsRequest;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.RelyingPartyAuthenticationRequest;
import org.springframework.security.web.webauthn.management.RelyingPartyRegistrationRequest;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.security.web.webauthn.management.Webauthn4JRelyingPartyOperations;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TenantAwareWebAuthnRelyingPartyOperations implements WebAuthnRelyingPartyOperations {
    private final HostOrganizationResolver hostOrganizationResolver;
    private final PublicKeyCredentialUserEntityRepository userEntityRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final Map<String, Webauthn4JRelyingPartyOperations> delegates = new ConcurrentHashMap<>();

    public TenantAwareWebAuthnRelyingPartyOperations(HostOrganizationResolver hostOrganizationResolver,
                                                     PublicKeyCredentialUserEntityRepository userEntityRepository,
                                                     UserCredentialRepository userCredentialRepository) {
        this.hostOrganizationResolver = hostOrganizationResolver;
        this.userEntityRepository = userEntityRepository;
        this.userCredentialRepository = userCredentialRepository;
    }

    @Override
    public PublicKeyCredentialCreationOptions createPublicKeyCredentialCreationOptions(
            PublicKeyCredentialCreationOptionsRequest request) {
        return delegate().createPublicKeyCredentialCreationOptions(request);
    }

    @Override
    public CredentialRecord registerCredential(RelyingPartyRegistrationRequest request) {
        return delegate().registerCredential(request);
    }

    @Override
    public PublicKeyCredentialRequestOptions createCredentialRequestOptions(PublicKeyCredentialRequestOptionsRequest request) {
        return delegate().createCredentialRequestOptions(request);
    }

    @Override
    public PublicKeyCredentialUserEntity authenticate(RelyingPartyAuthenticationRequest request) {
        return delegate().authenticate(request);
    }

    private Webauthn4JRelyingPartyOperations delegate() {
        HttpServletRequest request = currentRequest();
        String rpId = hostOrganizationResolver.currentHost().orElse(request.getServerName());
        String origin = origin(request);
        return delegates.computeIfAbsent(rpId + "|" + origin, key -> {
            PublicKeyCredentialRpEntity rp = PublicKeyCredentialRpEntity.builder()
                    .id(rpId)
                    .name("OpenIssuer")
                    .build();
            return new Webauthn4JRelyingPartyOperations(userEntityRepository, userCredentialRepository, rp, Set.of(origin));
        });
    }

    private String origin(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);
        return defaultPort ? scheme + "://" + host : scheme + "://" + host + ":" + port;
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new IllegalStateException("No current servlet request available for WebAuthn");
        }
        return attributes.getRequest();
    }
}
