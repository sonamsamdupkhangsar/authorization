package me.sonam.auth.config;

import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;

public final class MapBackedAuthorizationServerContext implements AuthorizationServerContext {
    private final String issuer;

    private MapBackedAuthorizationServerContext(String issuer) {
        this.issuer = issuer;
    }

    public static void withIssuer(String issuer) {
        AuthorizationServerContextHolder.setContext(new MapBackedAuthorizationServerContext(issuer));
    }

    public static void reset() {
        AuthorizationServerContextHolder.resetContext();
    }

    @Override
    public String getIssuer() {
        return issuer;
    }

    @Override
    public org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings getAuthorizationServerSettings() {
        return null;
    }
}
