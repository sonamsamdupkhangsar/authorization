package me.sonam.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;

public class RegisteredClientUtil {
    private static final Logger LOG = LoggerFactory.getLogger(RegisteredClientUtil.class);
    private final ObjectMapper objectMapper;

    public RegisteredClientUtil() {
        this.objectMapper = JsonMapper.builder().findAndAddModules().build();
    }
    public Map<String, Object> getMapObject(RegisteredClient registeredClient) {
        List<String> clientAuthenticationMethods = new ArrayList<>(registeredClient.getClientAuthenticationMethods().size());
        registeredClient.getClientAuthenticationMethods().forEach(clientAuthenticationMethod ->
                clientAuthenticationMethods.add(clientAuthenticationMethod.getValue()));

        List<String> authorizationGrantTypes = new ArrayList<>(registeredClient.getAuthorizationGrantTypes().size());
        registeredClient.getAuthorizationGrantTypes().forEach(authorizationGrantType ->
                authorizationGrantTypes.add(authorizationGrantType.getValue()));

        Map<String, Object> map = Map.of("id", registeredClient.getId(),
                "clientId", registeredClient.getClientId(), "clientSecret", registeredClient.getClientSecret(),
                "clientName", registeredClient.getClientName(),
                "clientAuthenticationMethods", StringUtils.collectionToCommaDelimitedString(clientAuthenticationMethods),
                "authorizationGrantTypes", StringUtils.collectionToCommaDelimitedString(authorizationGrantTypes),
                "redirectUris", StringUtils.collectionToCommaDelimitedString(registeredClient.getRedirectUris()),
                "scopes", StringUtils.collectionToCommaDelimitedString(registeredClient.getScopes()),
                "clientSettings",writeMap(registeredClient.getClientSettings().getSettings()),
                "tokenSettings", writeMap(registeredClient.getTokenSettings().getSettings()));

        LOG.info("map contains: {}", map);
        return map;
    }

    public Map<String, Object> getMapObjectNotWorking(RegisteredClient registeredClient) {
        List<String> clientAuthenticationMethods = new ArrayList<>(registeredClient.getClientAuthenticationMethods().size());
        registeredClient.getClientAuthenticationMethods().forEach(clientAuthenticationMethod ->
                clientAuthenticationMethods.add(clientAuthenticationMethod.getValue()));

        List<String> authorizationGrantTypes = new ArrayList<>(registeredClient.getAuthorizationGrantTypes().size());
        registeredClient.getAuthorizationGrantTypes().forEach(authorizationGrantType ->
                authorizationGrantTypes.add(authorizationGrantType.getValue()));

        Map<String, Object> map = new HashMap<>();
        map.put("id", registeredClient.getId());
        map.put("clientId", registeredClient.getClientId());
        map.put("clientSecret", registeredClient.getClientSecret());
        map.put("clientName", registeredClient.getClientName());
        map.put("clientAuthenticationMethods", StringUtils.collectionToCommaDelimitedString(clientAuthenticationMethods));
        map.put("authorizationGrantTypes", StringUtils.collectionToCommaDelimitedString(authorizationGrantTypes));
        map.put("redirectUris", StringUtils.collectionToCommaDelimitedString(registeredClient.getRedirectUris()));
        map.put("scopes", StringUtils.collectionToCommaDelimitedString(registeredClient.getScopes()));
        map.put("clientSettings",writeMap(registeredClient.getClientSettings().getSettings()));
        map.put("tokenSettings", writeMap(registeredClient.getTokenSettings().getSettings()));

        LOG.info("map contains: {}", map);
        return map;
    }

    public Map<String, Object> parseMap(String data) {
        try {
            return this.objectMapper.readValue(data, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    private String writeMap(Map<String, Object> data) {
        try {
            String string = this.objectMapper.writeValueAsString(data);
            LOG.info("string: {}", string);
            return string;
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    public RegisteredClient build(Map<String, Object> map) {
        String id = null;
        if (map.get("id") != null) {
            id = (String) map.get("id");
            LOG.info("access id from map: {}", id);
        }
        else {
            id = UUID.randomUUID().toString();
            LOG.info("generate id for RegisteredClient: {}", id);
        }

        Set<String> clientAuthenticationMethods = StringUtils.commaDelimitedListToSet(
                map.get("clientAuthenticationMethods").toString());
        Set<String> authorizationGrantTypes = StringUtils.commaDelimitedListToSet(
                map.get("authorizationGrantTypes").toString());
        Set<String> redirectUris = StringUtils.commaDelimitedListToSet(
                map.get("redirectUris").toString());
        Set<String> clientScopes = StringUtils.commaDelimitedListToSet(
                map.get("scopes").toString());

        return RegisteredClient.withId(id)
                .clientId((String)map.get("clientId"))
                .clientSecret((String)map.get("clientSecret"))
                .clientName((String)map.get("clientName"))
                .clientAuthenticationMethods(authenticationMethods ->
                        clientAuthenticationMethods.forEach(authenticationMethod -> {
                            ClientAuthenticationMethod cam = resolveClientAuthenticationMethod(authenticationMethod);
                            LOG.trace("cam: {}, cam.value: {}", cam, cam.getValue());
                            authenticationMethods.add(cam);
                        }))
                .authorizationGrantTypes(grantTypes ->
                        authorizationGrantTypes.forEach(authorizationGrantType -> {
                            AuthorizationGrantType agt = resolveAuthorizationGrantType(authorizationGrantType);
                            LOG.trace("agt: {}, agt.value: {}", agt, agt.getValue());
                            grantTypes.add(agt);
                        })
                )
                .redirectUris(uris ->
                        redirectUris.forEach(redirectUri -> {
                            uris.add(redirectUri);
                        })
                )
                .scopes(scopes ->
                        clientScopes.forEach(scope -> {
                            scopes.add(scope);
                            LOG.trace("add scope: {}", scope);
                        })
                )

                .clientSettings(ClientSettings.withSettings(parseMap(map.get("clientSettings").toString())).build()).build();
    }

    private static AuthorizationGrantType resolveAuthorizationGrantType(String authorizationGrantType) {
        if (AuthorizationGrantType.AUTHORIZATION_CODE.getValue().equals(authorizationGrantType)) {
            return AuthorizationGrantType.AUTHORIZATION_CODE;
        } else if (AuthorizationGrantType.CLIENT_CREDENTIALS.getValue().equals(authorizationGrantType)) {
            return AuthorizationGrantType.CLIENT_CREDENTIALS;
        } else if (AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(authorizationGrantType)) {
            return AuthorizationGrantType.REFRESH_TOKEN;
        }
        return new AuthorizationGrantType(authorizationGrantType);              // Custom authorization grant type
    }

    private static ClientAuthenticationMethod resolveClientAuthenticationMethod(String clientAuthenticationMethod) {
        if (ClientAuthenticationMethod.CLIENT_SECRET_BASIC.getValue().equals(clientAuthenticationMethod)) {
            return ClientAuthenticationMethod.CLIENT_SECRET_BASIC;
        } else if (ClientAuthenticationMethod.CLIENT_SECRET_POST.getValue().equals(clientAuthenticationMethod)) {
            return ClientAuthenticationMethod.CLIENT_SECRET_POST;
        } else if (ClientAuthenticationMethod.NONE.getValue().equals(clientAuthenticationMethod)) {
            return ClientAuthenticationMethod.NONE;
        }
        return new ClientAuthenticationMethod(clientAuthenticationMethod);      // Custom client authentication method
    }

}
