package me.sonam.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.jackson.SecurityJacksonModules;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.jackson.OAuth2AuthorizationServerJacksonModule;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.*;

@Component
public class RegisteredClientMapConverter {
    private static final Logger LOG = LoggerFactory.getLogger(RegisteredClientMapConverter.class);
    private final JsonMapper jsonMapper;

    public RegisteredClientMapConverter() {
        ClassLoader classLoader = RegisteredClientMapConverter.class.getClassLoader();
        this.jsonMapper = JsonMapper.builder()
                .addModules(SecurityJacksonModules.getModules(classLoader))
                .addModules(new OAuth2AuthorizationServerJacksonModule())
                .build();
    }

    public RegisteredClient build(Map<String, Object> map) {
        String id = map.get("id") != null && !map.get("id").toString().trim().isEmpty()
                ? map.get("id").toString()
                : UUID.randomUUID().toString();

        Set<String> clientAuthenticationMethods = StringUtils.commaDelimitedListToSet(map.get("clientAuthenticationMethods").toString());
        Set<String> authorizationGrantTypes = StringUtils.commaDelimitedListToSet(map.get("authorizationGrantTypes").toString());
        Set<String> redirectUris = StringUtils.commaDelimitedListToSet(map.get("redirectUris").toString());
        Set<String> clientScopes = StringUtils.commaDelimitedListToSet(map.get("scopes").toString());

        RegisteredClient.Builder builder = RegisteredClient.withId(id)
                .clientId(map.get("clientId").toString())
                .clientSecret(map.get("clientSecret").toString())
                .clientName(map.get("clientName").toString())
                .clientAuthenticationMethods(authenticationMethods ->
                        clientAuthenticationMethods.forEach(authenticationMethod ->
                                authenticationMethods.add(resolveClientAuthenticationMethod(authenticationMethod))))
                .authorizationGrantTypes(grantTypes ->
                        authorizationGrantTypes.forEach(grantType ->
                                grantTypes.add(resolveAuthorizationGrantType(grantType))))
                .redirectUris(uris -> uris.addAll(redirectUris))
                .scopes(scopes -> scopes.addAll(clientScopes))
                .clientSettings(ClientSettings.withSettings(parseMap(map.get("clientSettings").toString())).build())
                .tokenSettings(TokenSettings.withSettings(parseMap(map.get("tokenSettings").toString())).build());

        if (map.get("clientIdIssuedAt") != null) {
            builder.clientIdIssuedAt(getInstant(map.get("clientIdIssuedAt").toString()));
        }
        if (map.get("clientSecretExpiresAt") != null) {
            builder.clientSecretExpiresAt(getInstant(map.get("clientSecretExpiresAt").toString()));
        }
        return builder.build();
    }

    public Map<String, Object> getMapObject(RegisteredClient registeredClient) {
        List<String> clientAuthenticationMethods = new ArrayList<>(registeredClient.getClientAuthenticationMethods().size());
        registeredClient.getClientAuthenticationMethods().forEach(method -> clientAuthenticationMethods.add(method.getValue()));

        List<String> authorizationGrantTypes = new ArrayList<>(registeredClient.getAuthorizationGrantTypes().size());
        registeredClient.getAuthorizationGrantTypes().forEach(grantType -> authorizationGrantTypes.add(grantType.getValue()));

        Map<String, Object> map = new HashMap<>();
        map.put("id", registeredClient.getId());
        map.put("clientId", registeredClient.getClientId());
        map.put("clientSecret", registeredClient.getClientSecret());
        map.put("clientIdIssuedAt", registeredClient.getClientIdIssuedAt());
        map.put("clientSecretExpiresAt", registeredClient.getClientSecretExpiresAt());
        map.put("clientName", registeredClient.getClientName());
        map.put("clientAuthenticationMethods", StringUtils.collectionToCommaDelimitedString(clientAuthenticationMethods));
        map.put("authorizationGrantTypes", StringUtils.collectionToCommaDelimitedString(authorizationGrantTypes));
        map.put("redirectUris", StringUtils.collectionToCommaDelimitedString(registeredClient.getRedirectUris()));
        map.put("scopes", StringUtils.collectionToCommaDelimitedString(registeredClient.getScopes()));
        map.put("clientSettings", writeMap(registeredClient.getClientSettings().getSettings()));
        map.put("tokenSettings", writeMap(registeredClient.getTokenSettings().getSettings()));
        return map;
    }

    public Map<String, String> getMap(RegisteredClient registeredClient) {
        Map<String, Object> objectMap = getMapObject(registeredClient);
        Map<String, String> map = new HashMap<>();
        objectMap.forEach((key, value) -> map.put(key, value == null ? null : value.toString()));
        LOG.info("map contains: {}", map);
        return map;
    }

    public Map<String, Object> parseMap(String data) {
        try {
            return this.jsonMapper.readValue(data, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    private Instant getInstant(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        if (value.contains("-") || value.contains("T")) {
            return Instant.parse(value);
        }
        return Instant.ofEpochSecond(Double.valueOf(value).longValue());
    }

    private String writeMap(Map<String, Object> data) {
        try {
            return this.jsonMapper.writeValueAsString(data);
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    private static AuthorizationGrantType resolveAuthorizationGrantType(String authorizationGrantType) {
        if (AuthorizationGrantType.AUTHORIZATION_CODE.getValue().equals(authorizationGrantType)) {
            return AuthorizationGrantType.AUTHORIZATION_CODE;
        } else if (AuthorizationGrantType.CLIENT_CREDENTIALS.getValue().equals(authorizationGrantType)) {
            return AuthorizationGrantType.CLIENT_CREDENTIALS;
        } else if (AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(authorizationGrantType)) {
            return AuthorizationGrantType.REFRESH_TOKEN;
        } else if (AuthorizationGrantType.DEVICE_CODE.getValue().equals(authorizationGrantType)) {
            return AuthorizationGrantType.DEVICE_CODE;
        } else if (AuthorizationGrantType.TOKEN_EXCHANGE.getValue().equals(authorizationGrantType)) {
            return AuthorizationGrantType.TOKEN_EXCHANGE;
        }
        return new AuthorizationGrantType(authorizationGrantType);
    }

    private static ClientAuthenticationMethod resolveClientAuthenticationMethod(String clientAuthenticationMethod) {
        if (ClientAuthenticationMethod.CLIENT_SECRET_BASIC.getValue().equals(clientAuthenticationMethod)) {
            return ClientAuthenticationMethod.CLIENT_SECRET_BASIC;
        } else if (ClientAuthenticationMethod.CLIENT_SECRET_POST.getValue().equals(clientAuthenticationMethod)) {
            return ClientAuthenticationMethod.CLIENT_SECRET_POST;
        } else if (ClientAuthenticationMethod.CLIENT_SECRET_JWT.getValue().equals(clientAuthenticationMethod)) {
            return ClientAuthenticationMethod.CLIENT_SECRET_JWT;
        } else if (ClientAuthenticationMethod.PRIVATE_KEY_JWT.getValue().equals(clientAuthenticationMethod)) {
            return ClientAuthenticationMethod.PRIVATE_KEY_JWT;
        } else if (ClientAuthenticationMethod.TLS_CLIENT_AUTH.getValue().equals(clientAuthenticationMethod)) {
            return ClientAuthenticationMethod.TLS_CLIENT_AUTH;
        } else if (ClientAuthenticationMethod.SELF_SIGNED_TLS_CLIENT_AUTH.getValue().equals(clientAuthenticationMethod)) {
            return ClientAuthenticationMethod.SELF_SIGNED_TLS_CLIENT_AUTH;
        } else if (ClientAuthenticationMethod.NONE.getValue().equals(clientAuthenticationMethod)) {
            return ClientAuthenticationMethod.NONE;
        }
        return new ClientAuthenticationMethod(clientAuthenticationMethod);
    }
}
