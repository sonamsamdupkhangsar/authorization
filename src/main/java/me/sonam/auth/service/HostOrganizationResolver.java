package me.sonam.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Optional;

@Component
public class HostOrganizationResolver {
    private static final Logger LOG = LoggerFactory.getLogger(HostOrganizationResolver.class);

    @Value("${authzmanager-admin-label:admin}")
    private String adminHostLabel;

    @Value("#{'${authorization-server.multitenancy.default-hosts:localhost,127.0.0.1,authorization-server}'.split(',')}")
    private List<String> defaultHosts;

    /*
     * Reads the current servlet request host and normalizes it to the organization host used
     * for tenant lookup.
     *
     * Example:
     *   request.getServerName() = business2.admin.openissuer.test
     *   currentHost()           = Optional[business2.openissuer.test]
     */
    public Optional<String> currentHost() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return Optional.empty();
        }

        HttpServletRequest request = attributes.getRequest();
        if (request == null) {
            return Optional.empty();
        }
        String requestHost = forwardedHost(request).orElse(request.getServerName());
        if (defaultHosts != null && defaultHosts.stream().map(String::trim).anyMatch(requestHost::equals)) {
            LOG.info("request serverName '{}' is a default host, skipping host-bound organization resolution", requestHost);
            return Optional.empty();
        }
        String organizationHost = toOrganizationHost(requestHost);
        LOG.info("resolved organization host '{}' from request serverName '{}'", organizationHost, requestHost);
        return Optional.ofNullable(organizationHost);
    }

    /**
     * A Gateway may leave the servlet request server name set to the internal
     * service name. Prefer the original public host when it is supplied by the
     * proxy so tenant self-service links stay on the tenant hostname.
     */
    private Optional<String> forwardedHost(HttpServletRequest request) {
        String forwardedHost = request.getHeader("X-Forwarded-Host");
        if (forwardedHost == null || forwardedHost.isBlank()) {
            String forwarded = request.getHeader("Forwarded");
            if (forwarded != null) {
                for (String element : forwarded.split(";")) {
                    String trimmed = element.trim();
                    if (trimmed.regionMatches(true, 0, "host=", 0, 5)) {
                        forwardedHost = trimmed.substring(5).trim();
                        break;
                    }
                }
            }
        }
        if (forwardedHost == null || forwardedHost.isBlank()) {
            return Optional.empty();
        }
        String host = forwardedHost.split(",", 2)[0].trim();
        if (host.startsWith("\"") && host.endsWith("\"")) {
            host = host.substring(1, host.length() - 1);
        }
        return Optional.of(host);
    }

    /*
     * Converts authzmanager admin hosts back to the organization issuer host.
     *
     * Examples with authzmanager-admin-label=admin:
     *   business1.admin.openissuer.test -> business1.openissuer.test
     *   business2.admin.openissuer.test -> business2.openissuer.test
     *   free.admin.openissuer.test      -> free.openissuer.test
     *   platform.admin.openissuer.test  -> platform.openissuer.test
     *   business1.openissuer.test       -> business1.openissuer.test
     */
    private String toOrganizationHost(String host) {
        if (host == null || host.isBlank()) {
            return host;
        }

        String adminSegment = "." + adminHostLabel + ".";
        if (!host.contains(adminSegment)) {
            return host;
        }

        return host.replace(adminSegment, ".");
    }
}
