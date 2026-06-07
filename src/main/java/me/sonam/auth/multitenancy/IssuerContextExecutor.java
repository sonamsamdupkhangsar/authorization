package me.sonam.auth.multitenancy;

import me.sonam.auth.config.MapBackedAuthorizationServerContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.function.Supplier;

@Component
public class IssuerContextExecutor {
    public <T> T withCurrentRequestIssuer(Supplier<T> supplier) {
        return withIssuer(currentIssuer(), supplier);
    }

    public void withCurrentRequestIssuer(Runnable runnable) {
        withIssuer(currentIssuer(), () -> {
            runnable.run();
            return null;
        });
    }

    public String currentHost() {
        HttpServletRequest request = currentRequest();
        return request.getServerName();
    }

    public String currentIssuer() {
        HttpServletRequest request = currentRequest();
        String scheme = request.getScheme();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);
        return defaultPort ? scheme + "://" + request.getServerName() : scheme + "://" + request.getServerName() + ":" + port;
    }

    public <T> T withIssuer(String issuer, Supplier<T> supplier) {
        MapBackedAuthorizationServerContext.withIssuer(issuer);
        try {
            return supplier.get();
        } finally {
            MapBackedAuthorizationServerContext.reset();
        }
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new IllegalStateException("No current servlet request available");
        }
        return attributes.getRequest();
    }
}
