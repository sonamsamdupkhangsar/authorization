package me.sonam.auth.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Optional;

@Component
public class AdminReturnUrlValidator {
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
            return expectedAdminHost.equalsIgnoreCase(host) ? Optional.of(returnUrl) : Optional.empty();
        }
        catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
