package me.sonam.auth.mfa.passkey;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.management.ImmutablePublicKeyCredentialRequestOptionsRequest;
import org.springframework.security.web.webauthn.management.RelyingPartyAuthenticationRequest;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@Controller
public class PasskeyLoginController {
    private static final Logger LOG = LoggerFactory.getLogger(PasskeyLoginController.class);
    private static final String REQUEST_OPTIONS = PasskeyLoginController.class.getName() + ".REQUEST_OPTIONS";

    private final WebAuthnRelyingPartyOperations relyingPartyOperations;
    private final PasskeyLoginService passkeyLoginService;
    private final PasskeyMfaService passkeyMfaService;

    public PasskeyLoginController(WebAuthnRelyingPartyOperations relyingPartyOperations,
                                  PasskeyLoginService passkeyLoginService,
                                  PasskeyMfaService passkeyMfaService) {
        this.relyingPartyOperations = relyingPartyOperations;
        this.passkeyLoginService = passkeyLoginService;
        this.passkeyMfaService = passkeyMfaService;
    }

    @PostMapping("/passkeys/login/options")
    public ResponseEntity<?> options(HttpSession session) {
        PublicKeyCredentialRequestOptions options = relyingPartyOperations.createCredentialRequestOptions(
                new ImmutablePublicKeyCredentialRequestOptionsRequest(null));
        session.setAttribute(REQUEST_OPTIONS, options);
        return ResponseEntity.ok(PasskeyMfaController.toBrowserOptions(options));
    }

    @PostMapping("/passkeys/login")
    public ResponseEntity<?> login(@RequestBody PasskeyMfaController.AssertionRequest assertionRequest,
                                   HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        PublicKeyCredentialRequestOptions options = requestOptions(session);
        if (options == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Passkey sign-in session expired"));
        }
        session.removeAttribute(REQUEST_OPTIONS);

        try {
            String username = relyingPartyOperations.authenticate(
                    new RelyingPartyAuthenticationRequest(options, assertionRequest.toPublicKeyCredential())).getName();
            Authentication authentication = passkeyLoginService.authorize(username, request);
            String redirectUrl = passkeyMfaService.completeAuthentication(request, response, authentication);
            LOG.info("passkey login succeeded for {}", username);
            return ResponseEntity.ok(Map.of("redirectUrl", redirectUrl));
        }
        catch (RuntimeException exception) {
            LOG.debug("passkey login failed", exception);
            return ResponseEntity.status(401).body(Map.of("error", "Passkey sign-in failed"));
        }
    }

    private PublicKeyCredentialRequestOptions requestOptions(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object options = session.getAttribute(REQUEST_OPTIONS);
        return options instanceof PublicKeyCredentialRequestOptions requestOptions ? requestOptions : null;
    }
}
