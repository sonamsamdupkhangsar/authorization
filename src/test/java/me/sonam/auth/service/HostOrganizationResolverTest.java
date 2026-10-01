package me.sonam.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HostOrganizationResolverTest {
    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void resolvesTenantHostFromForwardedAdminHost() {
        MockHttpServletRequest request = request("authorization-server.pr-test-4.svc.cluster.local");
        request.addHeader("X-Forwarded-Host", "pr-test-4.admin.openissuer.com");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(newResolver().currentHost()).contains("pr-test-4.openissuer.com");
    }

    @Test
    void resolvesTenantHostFromForwardedHeaderWhenProxyUsesStandardHeader() {
        MockHttpServletRequest request = request("authorization-server.pr-test-4.svc.cluster.local");
        request.addHeader("Forwarded", "for=192.0.2.1;host=pr-test-4.openissuer.com;proto=https");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(newResolver().currentHost()).contains("pr-test-4.openissuer.com");
    }

    private HostOrganizationResolver newResolver() {
        HostOrganizationResolver resolver = new HostOrganizationResolver();
        org.springframework.test.util.ReflectionTestUtils.setField(resolver, "adminHostLabel", "admin");
        org.springframework.test.util.ReflectionTestUtils.setField(resolver, "defaultHosts",
                List.of("localhost", "127.0.0.1", "authorization-server"));
        return resolver;
    }

    private MockHttpServletRequest request(String serverName) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName(serverName);
        return request;
    }
}
