package me.sonam.auth;

import me.sonam.auth.service.ClientIdUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ClientIdUtilTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void getClientIdFallsBackToSessionWhenSavedRequestIsGone() {
        RequestCache requestCache = mock(RequestCache.class);
        when(requestCache.getRequest(any(), any())).thenReturn(null);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(ClientIdUtil.CLIENT_ID_SESSION_ATTRIBUTE, "authzmanager");
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request, new MockHttpServletResponse()));

        assertThat(ClientIdUtil.getClientId(requestCache)).isEqualTo("authzmanager");
    }

    @Test
    void isSavedRequestForMatchesRedirectPath() {
        RequestCache requestCache = mock(RequestCache.class);
        SavedRequest savedRequest = mock(SavedRequest.class);
        when(requestCache.getRequest(any(), any())).thenReturn(savedRequest);
        when(savedRequest.getRedirectUrl()).thenReturn("http://free.openissuer.test:9001/mfa/passkeys");

        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request, new MockHttpServletResponse()));

        assertThat(ClientIdUtil.isSavedRequestFor(requestCache, "/mfa/passkeys")).isTrue();
        assertThat(ClientIdUtil.isSavedRequestFor(requestCache, "/admin")).isFalse();
    }
}
