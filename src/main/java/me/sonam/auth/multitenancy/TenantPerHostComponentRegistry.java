package me.sonam.auth.multitenancy;

import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.util.Assert;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class TenantPerHostComponentRegistry {
    private final ConcurrentMap<String, Map<Class<?>, Object>> registry = new ConcurrentHashMap<>();
    private final Map<Class<?>, Object> defaultComponents = new ConcurrentHashMap<>();
    private final Set<String> defaultHosts = ConcurrentHashMap.newKeySet();

    public void registerDefaultHost(String host) {
        Assert.hasText(host, "host cannot be empty");
        this.defaultHosts.add(host);
    }

    public <T> void registerDefault(Class<T> componentClass, T component) {
        Assert.notNull(componentClass, "componentClass cannot be null");
        Assert.notNull(component, "component cannot be null");
        this.defaultComponents.put(componentClass, component);
    }

    public <T> void register(String host, Class<T> componentClass, T component) {
        Assert.hasText(host, "host cannot be empty");
        Assert.notNull(componentClass, "componentClass cannot be null");
        Assert.notNull(component, "component cannot be null");
        Map<Class<?>, Object> components = this.registry.computeIfAbsent(host, key -> new ConcurrentHashMap<>());
        components.put(componentClass, component);
    }

    @Nullable
    public <T> T get(String host, Class<T> componentClass) {
        Assert.hasText(host, "host cannot be empty");
        Assert.notNull(componentClass, "componentClass cannot be null");
        if (this.defaultHosts.contains(host)) {
            return componentClass.cast(this.defaultComponents.get(componentClass));
        }
        Map<Class<?>, Object> components = this.registry.get(host);
        if (components == null) {
            return null;
        }
        return componentClass.cast(components.get(componentClass));
    }

    @Nullable
    public <T> T get(Class<T> componentClass) {
        String host = resolveCurrentHost();
        if (host == null) {
            return componentClass.cast(this.defaultComponents.get(componentClass));
        }
        if (this.defaultHosts.contains(host)) {
            return componentClass.cast(this.defaultComponents.get(componentClass));
        }
        Map<Class<?>, Object> components = this.registry.get(host);
        if (components == null) {
            return null;
        }
        return componentClass.cast(components.get(componentClass));
    }

    @Nullable
    private String resolveCurrentHost() {
        AuthorizationServerContext context = AuthorizationServerContextHolder.getContext();
        if (context == null || context.getIssuer() == null) {
            return null;
        }
        return URI.create(context.getIssuer()).getHost();
    }
}
