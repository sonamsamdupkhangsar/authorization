package me.sonam.auth.mfa.passkey;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class PasskeyMfaAuthenticationSuccessHandler implements AuthenticationSuccessHandler {
    private static final Logger LOG = LoggerFactory.getLogger(PasskeyMfaAuthenticationSuccessHandler.class);

    private final PasskeyMfaService passkeyMfaService;
    private final SavedRequestAwareAuthenticationSuccessHandler delegate =
            new SavedRequestAwareAuthenticationSuccessHandler();

    public PasskeyMfaAuthenticationSuccessHandler(PasskeyMfaService passkeyMfaService) {
        this.passkeyMfaService = passkeyMfaService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (!passkeyMfaService.requiresPasskeyMfa(authentication)) {
            delegate.onAuthenticationSuccess(request, response, authentication);
            return;
        }

        LOG.info("password authentication succeeded; redirecting to passkey MFA");
        request.getSession().setAttribute(PasskeyMfaSession.PENDING_AUTHENTICATION, authentication);
        passkeyMfaService.clearAuthentication(request, response);
        response.sendRedirect(request.getContextPath() + "/mfa/passkeys/challenge");
    }
}
