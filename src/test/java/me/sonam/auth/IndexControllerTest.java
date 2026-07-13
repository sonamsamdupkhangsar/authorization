package me.sonam.auth;

import jakarta.servlet.http.HttpServletResponse;
import me.sonam.auth.rest.IndexController;
import me.sonam.auth.service.ClientIdUtil;
import me.sonam.auth.service.LoginReturnContextService;
import me.sonam.auth.service.SignupPolicyService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.ui.ExtendedModelMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IndexControllerTest {

    @Test
    void indexStoresSavedClientIdInSession() {
        RequestCache requestCache = mock(RequestCache.class);
        SavedRequest savedRequest = mock(SavedRequest.class);
        when(requestCache.getRequest(any(), any(HttpServletResponse.class))).thenReturn(savedRequest);
        when(savedRequest.getParameterValues("client_id")).thenReturn(new String[]{"authzmanager"});

        IndexController indexController = indexController(requestCache);
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletRequest request = new MockHttpServletRequest();

        String view = indexController.index(model, request.getSession(), request, new MockHttpServletResponse());

        assertThat(view).isEqualTo("index");
        assertThat(model.get("clientId")).isEqualTo("authzmanager");
        assertThat(request.getSession().getAttribute(ClientIdUtil.CLIENT_ID_SESSION_ATTRIBUTE)).isEqualTo("authzmanager");
    }

    @Test
    void indexUsesSessionClientIdWhenSavedRequestIsGone() {
        RequestCache requestCache = mock(RequestCache.class);
        when(requestCache.getRequest(any(), any(HttpServletResponse.class))).thenReturn(null);

        IndexController indexController = indexController(requestCache);
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("error", "");
        request.getSession().setAttribute(ClientIdUtil.CLIENT_ID_SESSION_ATTRIBUTE, "authzmanager");

        String view = indexController.index(model, request.getSession(), request, new MockHttpServletResponse());

        assertThat(view).isEqualTo("index");
        assertThat(model.get("clientId")).isEqualTo("authzmanager");
    }

    @Test
    void indexDoesNotUseSessionClientIdOnFreshLoginPage() {
        RequestCache requestCache = mock(RequestCache.class);
        when(requestCache.getRequest(any(), any(HttpServletResponse.class))).thenReturn(null);

        IndexController indexController = indexController(requestCache);
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(ClientIdUtil.CLIENT_ID_SESSION_ATTRIBUTE, "authzmanager");

        String view = indexController.index(model, request.getSession(), request, new MockHttpServletResponse());

        assertThat(view).isEqualTo("index");
        assertThat(model.get("clientId")).isNull();
    }

    private IndexController indexController(RequestCache requestCache) {
        SignupPolicyService signupPolicyService = mock(SignupPolicyService.class);
        when(signupPolicyService.isAccountSelfServiceAllowedForCurrentHost()).thenReturn(true);
        return new IndexController(requestCache, new LoginReturnContextService(requestCache), signupPolicyService);
    }
}
