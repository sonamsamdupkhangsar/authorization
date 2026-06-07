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
        String requestHost = request.getServerName();
        if (defaultHosts != null && defaultHosts.stream().map(String::trim).anyMatch(requestHost::equals)) {
            LOG.info("request serverName '{}' is a default host, skipping host-bound organization resolution", requestHost);
            return Optional.empty();
        }
        String organizationHost = toOrganizationHost(requestHost);
        LOG.info("resolved organization host '{}' from request serverName '{}'", organizationHost, requestHost);
        return Optional.ofNullable(organizationHost);
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
