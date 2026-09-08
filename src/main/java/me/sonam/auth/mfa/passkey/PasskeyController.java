package me.sonam.auth.mfa.passkey;

import jakarta.servlet.http.HttpServletRequest;
import me.sonam.auth.util.AdminReturnUrlValidator;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class PasskeyController {
    private final PublicKeyCredentialUserEntityRepository userEntityRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final AdminReturnUrlValidator adminReturnUrlValidator;

    public PasskeyController(PublicKeyCredentialUserEntityRepository userEntityRepository,
                             UserCredentialRepository userCredentialRepository,
                             AdminReturnUrlValidator adminReturnUrlValidator) {
        this.userEntityRepository = userEntityRepository;
        this.userCredentialRepository = userCredentialRepository;
        this.adminReturnUrlValidator = adminReturnUrlValidator;
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
        adminReturnUrlValidator.validate(returnUrl, request)
                .ifPresent(url -> model.addAttribute("returnUrl", url));
        return "mfa/passkeys";
    }
}
