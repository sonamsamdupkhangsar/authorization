package me.sonam.auth.mfa.passkey;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

@Service
public class PasskeyMfaService {
    private static final Logger LOG = LoggerFactory.getLogger(PasskeyMfaService.class);

    private final PublicKeyCredentialUserEntityRepository userEntityRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final RequestCache requestCache;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public PasskeyMfaService(PublicKeyCredentialUserEntityRepository userEntityRepository,
                             UserCredentialRepository userCredentialRepository,
                             RequestCache requestCache) {
        this.userEntityRepository = userEntityRepository;
        this.userCredentialRepository = userCredentialRepository;
        this.requestCache = requestCache;
    }

    public boolean requiresPasskeyMfa(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        PublicKeyCredentialUserEntity userEntity = userEntityRepository.findByUsername(authentication.getName());
        if (userEntity == null) {
            LOG.info("no passkey user entity found for {}", authentication.getName());
            return false;
        }
        List<CredentialRecord> credentials = userCredentialRepository.findByUserId(userEntity.getId());
        boolean requiresMfa = credentials != null && !credentials.isEmpty();
        LOG.info("passkey MFA required for {}? {}", authentication.getName(), requiresMfa);
        return requiresMfa;
    }

    public Authentication withPasskeyFactor(Authentication authentication) {
        List<GrantedAuthority> authorities = new ArrayList<>(authentication.getAuthorities());
        boolean hasPasskeyFactor = authorities.stream()
                .anyMatch(authority -> FactorGrantedAuthority.WEBAUTHN_AUTHORITY.equals(authority.getAuthority()));
        if (!hasPasskeyFactor) {
            authorities.add(FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.WEBAUTHN_AUTHORITY));
        }

        UsernamePasswordAuthenticationToken authenticated =
                new UsernamePasswordAuthenticationToken(authentication.getPrincipal(),
                        authentication.getCredentials(), authorities);
        authenticated.setDetails(authentication.getDetails());
        return authenticated;
    }

    public String completeAuthentication(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) {
        Authentication mfaAuthentication = withPasskeyFactor(authentication);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(mfaAuthentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        SavedRequest savedRequest = requestCache.getRequest(request, response);
        if (savedRequest != null) {
            requestCache.removeRequest(request, response);
            return normalizeSavedRedirectUrl(savedRequest.getRedirectUrl());
        }
        String contextPath = request.getContextPath();
        return contextPath == null || contextPath.isBlank() ? "/" : contextPath + "/";
    }

    public void clearAuthentication(HttpServletRequest request, HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        SecurityContext emptyContext = SecurityContextHolder.createEmptyContext();
        securityContextRepository.saveContext(emptyContext, request, response);
    }

    private String normalizeSavedRedirectUrl(String redirectUrl) {
        if (redirectUrl == null || redirectUrl.isBlank()) {
            return "/";
        }
        try {
            URI uri = new URI(redirectUrl);
            if (!"/error".equals(uri.getPath()) || uri.getQuery() == null
                    || !uri.getQuery().contains("response_type=code")
                    || !uri.getQuery().contains("client_id=")) {
                return redirectUrl;
            }
            URI authorizeUri = new URI(uri.getScheme(), uri.getAuthority(), "/oauth2/authorize",
                    uri.getQuery(), uri.getFragment());
            LOG.warn("rewriting saved error redirect to authorization endpoint: {}", authorizeUri);
            return authorizeUri.toString();
        }
        catch (URISyntaxException exception) {
            LOG.warn("saved redirect URL was not a valid URI: {}", redirectUrl);
            return redirectUrl;
        }
    }
}
