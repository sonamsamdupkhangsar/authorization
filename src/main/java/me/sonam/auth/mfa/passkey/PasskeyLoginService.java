package me.sonam.auth.mfa.passkey;

import jakarta.servlet.http.HttpServletRequest;
import me.sonam.auth.service.AuthenticationCallout;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class PasskeyLoginService {
    private final AuthenticationCallout authenticationCallout;

    public PasskeyLoginService(AuthenticationCallout authenticationCallout) {
        this.authenticationCallout = authenticationCallout;
    }

    public Authentication authorize(String username, HttpServletRequest request) {
        return authenticationCallout.authenticatePasskey(username, request);
    }
}
