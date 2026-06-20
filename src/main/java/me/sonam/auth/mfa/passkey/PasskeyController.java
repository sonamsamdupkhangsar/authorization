package me.sonam.auth.mfa.passkey;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@Controller
public class PasskeyController {
    private final PublicKeyCredentialUserEntityRepository userEntityRepository;
    private final UserCredentialRepository userCredentialRepository;

    public PasskeyController(PublicKeyCredentialUserEntityRepository userEntityRepository,
                             UserCredentialRepository userCredentialRepository) {
        this.userEntityRepository = userEntityRepository;
        this.userCredentialRepository = userCredentialRepository;
    }

    @GetMapping("/mfa/passkeys")
    public String passkeys(Authentication authentication, Model model,
                           @RequestParam(name = "return_url", required = false) String returnUrl,
                           HttpServletRequest request) {
        List<CredentialRecord> credentials = List.of();
        if (authentication != null) {
            PublicKeyCredentialUserEntity userEntity = userEntityRepository.findByUsername(authentication.getName());
            if (userEntity != null) {
                credentials = userCredentialRepository.findByUserId(userEntity.getId());
            }
        }
        model.addAttribute("credentials", credentials);
        validReturnUrl(returnUrl, request).ifPresent(url -> model.addAttribute("returnUrl", url));
        return "mfa/passkeys";
    }

    private Optional<String> validReturnUrl(String returnUrl, HttpServletRequest request) {
        if (returnUrl == null || returnUrl.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = URI.create(returnUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null
                    || (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme))) {
                return Optional.empty();
            }
            String issuerHost = request.getServerName();
            int firstDot = issuerHost.indexOf('.');
            if (firstDot < 1) {
                return Optional.empty();
            }
            String expectedAdminHost = issuerHost.substring(0, firstDot) + ".admin" + issuerHost.substring(firstDot);
            return expectedAdminHost.equalsIgnoreCase(host) ? Optional.of(returnUrl) : Optional.empty();
        }
        catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
