package me.sonam.auth;

import me.sonam.auth.mfa.passkey.PasskeyMfaAuthenticationSuccessHandler;
import me.sonam.auth.mfa.passkey.PasskeyMfaService;
import me.sonam.auth.mfa.passkey.PasskeyMfaSession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasskeyMfaAuthenticationSuccessHandlerTest {

    @Test
    void redirectsToChallengeWhenPasskeyMfaIsRequired() throws Exception {
        PasskeyMfaService passkeyMfaService = mock(PasskeyMfaService.class);
        PasskeyMfaAuthenticationSuccessHandler successHandler =
                new PasskeyMfaAuthenticationSuccessHandler(passkeyMfaService);
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated("user@example.com", "password", List.of());
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(passkeyMfaService.requiresPasskeyMfa(authentication)).thenReturn(true);

        successHandler.onAuthenticationSuccess(request, response, authentication);

        assertThat(request.getSession().getAttribute(PasskeyMfaSession.PENDING_AUTHENTICATION))
                .isSameAs(authentication);
        assertThat(response.getRedirectedUrl()).isEqualTo("/mfa/passkeys/challenge");
        verify(passkeyMfaService).clearAuthentication(request, response);
        SecurityContextHolder.clearContext();
    }
}
