package me.sonam.auth.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.net.URI;
import java.util.Optional;

@Component
public class AdminReturnUrlValidator {
    private final boolean allowLocalUi;

    public AdminReturnUrlValidator() {
        this(false);
    }

    @Autowired
    public AdminReturnUrlValidator(@Value("${auth.admin-return-url.allow-local-ui:false}") boolean allowLocalUi) {
        this.allowLocalUi = allowLocalUi;
    }

    public Optional<String> validate(String returnUrl, HttpServletRequest request) {
        if (returnUrl == null || returnUrl.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = URI.create(returnUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null
                    || (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme))) {
                return Optional.empty();
            }
            String issuerHost = request.getServerName();
            int firstDot = issuerHost.indexOf('.');
            if (firstDot < 1) {
                return Optional.empty();
            }
            String expectedAdminHost = issuerHost.substring(0, firstDot) + ".admin" + issuerHost.substring(firstDot);
            boolean expectedAdmin = expectedAdminHost.equalsIgnoreCase(host);
            boolean localNextUi = allowLocalUi
                    && ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host))
                    && (uri.getPort() == 3000 || uri.getPort() == -1);
            return expectedAdmin || localNextUi ? Optional.of(returnUrl) : Optional.empty();
        }
        catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
