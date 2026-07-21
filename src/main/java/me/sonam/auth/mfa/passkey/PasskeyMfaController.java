package me.sonam.auth.mfa.passkey;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.webauthn.api.AuthenticatorAssertionResponse;
import org.springframework.security.web.webauthn.api.AuthenticatorAttachment;
import org.springframework.security.web.webauthn.api.AuthenticatorTransport;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutableAuthenticationExtensionsClientOutputs;
import org.springframework.security.web.webauthn.api.PublicKeyCredential;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialDescriptor;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.ImmutablePublicKeyCredentialRequestOptionsRequest;
import org.springframework.security.web.webauthn.management.RelyingPartyAuthenticationRequest;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

@Controller
public class PasskeyMfaController {
    private static final Logger LOG = LoggerFactory.getLogger(PasskeyMfaController.class);

    private final WebAuthnRelyingPartyOperations relyingPartyOperations;
    private final PasskeyMfaService passkeyMfaService;

    public PasskeyMfaController(WebAuthnRelyingPartyOperations relyingPartyOperations,
                                PasskeyMfaService passkeyMfaService) {
        this.relyingPartyOperations = relyingPartyOperations;
        this.passkeyMfaService = passkeyMfaService;
    }

    @GetMapping("/mfa/passkeys/challenge")
    public String challenge(HttpSession session, Model model) {
        Authentication pendingAuthentication = pendingAuthentication(session);
        if (pendingAuthentication == null) {
            model.addAttribute("error", "Passkey verification session expired. Please sign in again.");
        }
        return "mfa/passkey-challenge";
    }

    @PostMapping("/mfa/passkeys/authenticate/options")
    public ResponseEntity<?> options(HttpSession session) {
        Authentication pendingAuthentication = pendingAuthentication(session);
        if (pendingAuthentication == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Passkey verification session expired"));
        }

        PublicKeyCredentialRequestOptions options = relyingPartyOperations.createCredentialRequestOptions(
                new ImmutablePublicKeyCredentialRequestOptionsRequest(pendingAuthentication));
        session.setAttribute(PasskeyMfaSession.REQUEST_OPTIONS, options);
        return ResponseEntity.ok(toBrowserOptions(options));
    }

    @PostMapping("/mfa/passkeys/authenticate")
    public ResponseEntity<?> authenticate(@RequestBody AssertionRequest assertionRequest,
                                          HttpServletRequest request,
                                          HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        Authentication pendingAuthentication = pendingAuthentication(session);
        PublicKeyCredentialRequestOptions requestOptions = requestOptions(session);
        if (pendingAuthentication == null || requestOptions == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Passkey verification session expired"));
        }

        PublicKeyCredentialUserEntity userEntity;
        try {
            userEntity = relyingPartyOperations.authenticate(
                    new RelyingPartyAuthenticationRequest(requestOptions, assertionRequest.toPublicKeyCredential()));
            if (!pendingAuthentication.getName().equals(userEntity.getName())) {
                LOG.warn("passkey user {} did not match pending user {}", userEntity.getName(), pendingAuthentication.getName());
                throw new BadCredentialsException("Passkey does not match signed-in user");
            }
        }
        catch (RuntimeException exception) {
            LOG.debug("passkey MFA failed", exception);
            LOG.warn("passkey MFA failed for {}: {}", pendingAuthentication.getName(), exception.getMessage());
            return ResponseEntity.status(401).body(Map.of("error", "Passkey verification failed"));
        }

        LOG.info("passkey MFA succeeded for {}", pendingAuthentication.getName());
        session.removeAttribute(PasskeyMfaSession.PENDING_AUTHENTICATION);
        session.removeAttribute(PasskeyMfaSession.REQUEST_OPTIONS);
        String redirectUrl = passkeyMfaService.completeAuthentication(request, response, pendingAuthentication);
        return ResponseEntity.ok(Map.of("redirectUrl", redirectUrl));
    }

    private Authentication pendingAuthentication(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object authentication = session.getAttribute(PasskeyMfaSession.PENDING_AUTHENTICATION);
        return authentication instanceof Authentication ? (Authentication) authentication : null;
    }

    private PublicKeyCredentialRequestOptions requestOptions(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object options = session.getAttribute(PasskeyMfaSession.REQUEST_OPTIONS);
        return options instanceof PublicKeyCredentialRequestOptions
                ? (PublicKeyCredentialRequestOptions) options : null;
    }

    static Map<String, Object> toBrowserOptions(PublicKeyCredentialRequestOptions options) {
        return Map.of(
                "challenge", options.getChallenge().toBase64UrlString(),
                "timeout", options.getTimeout().toMillis(),
                "rpId", options.getRpId(),
                "allowCredentials", toBrowserCredentials(options.getAllowCredentials()),
                "userVerification", options.getUserVerification().getValue()
        );
    }

    private static List<Map<String, Object>> toBrowserCredentials(List<PublicKeyCredentialDescriptor> credentials) {
        if (credentials == null) {
            return List.of();
        }
        return credentials.stream()
                .map(credential -> Map.<String, Object>of(
                        "type", credential.getType().getValue(),
                        "id", credential.getId().toBase64UrlString(),
                        "transports", transports(credential)))
                .toList();
    }

    private static List<String> transports(PublicKeyCredentialDescriptor credential) {
        if (credential.getTransports() == null) {
            return List.of();
        }
        return credential.getTransports().stream()
                .map(AuthenticatorTransport::getValue)
                .toList();
    }

    public record AssertionRequest(String id, String rawId, AssertionResponse response, String type,
                                   String authenticatorAttachment) {
        PublicKeyCredential<AuthenticatorAssertionResponse> toPublicKeyCredential() {
            AuthenticatorAssertionResponse assertionResponse = AuthenticatorAssertionResponse.builder()
                    .authenticatorData(Bytes.fromBase64(response.authenticatorData()))
                    .clientDataJSON(Bytes.fromBase64(response.clientDataJSON()))
                    .signature(Bytes.fromBase64(response.signature()))
                    .userHandle(response.userHandle() == null ? null : Bytes.fromBase64(response.userHandle()))
                    .build();

            return PublicKeyCredential.<AuthenticatorAssertionResponse>builder()
                    .id(id)
                    .rawId(Bytes.fromBase64(rawId))
                    .type(PublicKeyCredentialType.valueOf(type))
                    .response(assertionResponse)
                    .clientExtensionResults(new ImmutableAuthenticationExtensionsClientOutputs())
                    .authenticatorAttachment(authenticatorAttachment == null ? null
                            : AuthenticatorAttachment.valueOf(authenticatorAttachment))
                    .build();
        }
    }

    public record AssertionResponse(String authenticatorData, String clientDataJSON, String signature,
                                    String userHandle) {
    }
}
