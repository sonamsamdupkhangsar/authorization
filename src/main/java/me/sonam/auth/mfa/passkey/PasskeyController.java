package me.sonam.auth.mfa.passkey;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

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
    public String passkeys(Authentication authentication, Model model) {
        List<CredentialRecord> credentials = List.of();
        if (authentication != null) {
            PublicKeyCredentialUserEntity userEntity = userEntityRepository.findByUsername(authentication.getName());
            if (userEntity != null) {
                credentials = userCredentialRepository.findByUserId(userEntity.getId());
            }
        }
        model.addAttribute("credentials", credentials);
        return "mfa/passkeys";
    }
}
