package me.sonam.auth;

import jakarta.servlet.http.HttpServletResponse;
import me.sonam.auth.jpa.entity.ClientOrganization;
import me.sonam.auth.jpa.repo.ClientOrganizationRepository;
import me.sonam.auth.jpa.repo.HClientUserRepository;
import me.sonam.auth.service.AuthenticationCallout;
import me.sonam.auth.service.HostOrganizationResolver;
import me.sonam.auth.webclient.AccountWebClient;
import me.sonam.auth.webclient.AuthenticationWebClient;
import me.sonam.auth.webclient.LoginAttemptWebClient;
import me.sonam.auth.webclient.OrganizationWebClient;
import me.sonam.auth.webclient.RoleWebClient;
import me.sonam.auth.webclient.SettingWebClient;
import me.sonam.auth.webclient.UserWebClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AuthenticationCalloutHostAwareTest {
    private static final String CLIENT_ID = "messaging-client";
    private static final String FREE_HOST = "free.openissuer.test";
    private static final String BUSINESS1_HOST = "business1.openissuer.test";

    private final UUID clientUuid = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID organizationId = UUID.randomUUID();
    private final UUID authzManagerId = UUID.randomUUID();

    @Mock
    private RequestCache requestCache;
    @Mock
    private LoginAttemptWebClient loginAttemptWebClient;
    @Mock
    private OrganizationWebClient organizationWebClient;
    @Mock
    private AuthenticationWebClient authenticationWebClient;
    @Mock
    private UserWebClient userWebClient;
    @Mock
    private AccountWebClient accountWebClient;
    @Mock
    private SettingWebClient settingWebClient;
    @Mock
    private RoleWebClient roleWebClient;
    @Mock
    private ClientOrganizationRepository clientOrganizationRepository;
    @Mock
    private HClientUserRepository clientUserRepository;
    @Mock
    private RegisteredClientRepository registeredClientRepository;

    private AuthenticationCallout authenticationCallout;

    @BeforeEach
    void setUp() {
        authenticationCallout = new AuthenticationCallout(mock(WebClient.Builder.class), requestCache,
                loginAttemptWebClient, organizationWebClient, authenticationWebClient, userWebClient,
                accountWebClient, settingWebClient, roleWebClient);

        ReflectionTestUtils.setField(authenticationCallout, "clientOrganizationRepository", clientOrganizationRepository);
        ReflectionTestUtils.setField(authenticationCallout, "clientUserRepository", clientUserRepository);
        ReflectionTestUtils.setField(authenticationCallout, "registeredClientRepository", registeredClientRepository);
        ReflectionTestUtils.setField(authenticationCallout, "hostOrganizationResolver", new HostOrganizationResolver());
        ReflectionTestUtils.setField(authenticationCallout, "authzManagerId", authzManagerId);

        SavedRequest savedRequest = mock(SavedRequest.class);
        when(requestCache.getRequest(any(), any(HttpServletResponse.class))).thenReturn(savedRequest);
        when(savedRequest.getParameterValues("client_id")).thenReturn(new String[]{CLIENT_ID});

        RegisteredClient registeredClient = RegisteredClient.withId(clientUuid.toString())
                .clientId(CLIENT_ID)
                .clientSecret("{noop}secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://127.0.0.1/callback")
                .build();
        when(registeredClientRepository.findByClientId(CLIENT_ID)).thenReturn(registeredClient);

        when(accountWebClient.isAccountLocked("user1")).thenReturn(Mono.just(false));
        when(userWebClient.getUserId("user1")).thenReturn(Mono.just(userId));
        when(clientOrganizationRepository.findByClientId(clientUuid))
                .thenReturn(Optional.of(new ClientOrganization(clientUuid, organizationId)));
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void publicHostLoginFallsBackToClientOrganizationFlow() {
        bindRequestHost(FREE_HOST);
        when(organizationWebClient.getOrganizationIdBySubdomain(FREE_HOST)).thenReturn(Mono.empty());
        when(organizationWebClient.userExistInOrganization(userId, organizationId)).thenReturn(Mono.just(true));

        UsernamePasswordAuthenticationToken expected =
                new UsernamePasswordAuthenticationToken("principal", "password");
        when(authenticationWebClient.getAuth(any(), argThat(authBodyWithOrganization(organizationId))))
                .thenReturn(Mono.just(expected));

        UsernamePasswordAuthenticationToken request =
                new UsernamePasswordAuthenticationToken("user1", "password");

        Object result = authenticationCallout.authenticate(request);

        assertThat(result).isSameAs(expected);
        verify(organizationWebClient).getOrganizationIdBySubdomain(FREE_HOST);
        verify(organizationWebClient, times(1)).userExistInOrganization(userId, organizationId);
        verify(clientOrganizationRepository, times(1)).findByClientId(clientUuid);
        verify(authenticationWebClient).getAuth(any(), argThat(authBodyWithOrganization(organizationId)));
        verify(roleWebClient, never()).isSuperAdminInOrgId(any(), any(), any());
    }

    @Test
    void hostBoundLoginChecksHostOrganizationBeforeAuthenticating() {
        bindRequestHost(BUSINESS1_HOST);
        when(organizationWebClient.getOrganizationIdBySubdomain(BUSINESS1_HOST)).thenReturn(Mono.just(organizationId));
        when(organizationWebClient.userExistInOrganization(userId, organizationId)).thenReturn(Mono.just(true));

        UsernamePasswordAuthenticationToken expected =
                new UsernamePasswordAuthenticationToken("principal", "password");
        when(authenticationWebClient.getAuth(any(), argThat(authBodyWithOrganization(organizationId))))
                .thenReturn(Mono.just(expected));

        UsernamePasswordAuthenticationToken request =
                new UsernamePasswordAuthenticationToken("user1", "password");

        Object result = authenticationCallout.authenticate(request);

        assertThat(result).isSameAs(expected);
        verify(organizationWebClient).getOrganizationIdBySubdomain(BUSINESS1_HOST);
        verify(organizationWebClient, times(2)).userExistInOrganization(userId, organizationId);
        verify(clientOrganizationRepository, times(2)).findByClientId(clientUuid);
        verify(authenticationWebClient).getAuth(any(), argThat(authBodyWithOrganization(organizationId)));
        verify(roleWebClient, never()).isSuperAdminInOrgId(any(), any(), any());
    }

    private void bindRequestHost(String host) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName(host);
        request.setServerPort(9001);
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request, new MockHttpServletResponse()));
    }

    private ArgumentMatcher<Map<String, Object>> authBodyWithOrganization(UUID expectedOrganizationId) {
        return body -> body != null
                && clientUuid.equals(body.get("clientId"))
                && expectedOrganizationId.equals(body.get("organizationId"))
                && "user1".equals(body.get("authenticationId"))
                && "password".equals(body.get("password"));
    }
}
