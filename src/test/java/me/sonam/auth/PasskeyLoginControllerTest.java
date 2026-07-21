package me.sonam.auth;

import me.sonam.auth.mfa.passkey.PasskeyLoginController;
import me.sonam.auth.mfa.passkey.PasskeyLoginService;
import me.sonam.auth.mfa.passkey.PasskeyMfaController;
import me.sonam.auth.mfa.passkey.PasskeyMfaService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.UserVerificationRequirement;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasskeyLoginControllerTest {

    @Test
    void loginCompletesAuthenticationAndConsumesChallenge() {
        WebAuthnRelyingPartyOperations operations = mock(WebAuthnRelyingPartyOperations.class);
        PasskeyLoginService loginService = mock(PasskeyLoginService.class);
        PasskeyMfaService mfaService = mock(PasskeyMfaService.class);
        PasskeyLoginController controller = new PasskeyLoginController(operations, loginService, mfaService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        PublicKeyCredentialRequestOptions options = options();
        when(operations.createCredentialRequestOptions(any())).thenReturn(options);

        controller.options(request.getSession());

        PublicKeyCredentialUserEntity user = mock(PublicKeyCredentialUserEntity.class);
        when(user.getName()).thenReturn("demo-user");
        when(operations.authenticate(any())).thenReturn(user);
        var authentication = UsernamePasswordAuthenticationToken.authenticated("demo-user", "", List.of());
        when(loginService.authorize("demo-user", request)).thenReturn(authentication);
        when(mfaService.completeAuthentication(request, response, authentication)).thenReturn("/oauth2/authorize");

        var result = controller.login(assertion(), request, response);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isEqualTo(java.util.Map.of("redirectUrl", "/oauth2/authorize"));
        verify(loginService).authorize("demo-user", request);
        assertThat(controller.login(assertion(), request, response).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void loginRejectsMissingChallenge() {
        PasskeyLoginController controller = new PasskeyLoginController(
                mock(WebAuthnRelyingPartyOperations.class), mock(PasskeyLoginService.class),
                mock(PasskeyMfaService.class));

        var result = controller.login(assertion(), new MockHttpServletRequest(), new MockHttpServletResponse());

        assertThat(result.getStatusCode().value()).isEqualTo(401);
    }

    private PublicKeyCredentialRequestOptions options() {
        return PublicKeyCredentialRequestOptions.builder()
                .challenge(new Bytes(new byte[]{1}))
                .timeout(Duration.ofMinutes(2))
                .rpId("free.openissuer.test")
                .allowCredentials(List.of())
                .userVerification(UserVerificationRequirement.REQUIRED)
                .build();
    }

    private PasskeyMfaController.AssertionRequest assertion() {
        return new PasskeyMfaController.AssertionRequest("AQ", "AQ",
                new PasskeyMfaController.AssertionResponse("AQ", "AQ", "AQ", "AQ"),
                "public-key", null);
    }
}
