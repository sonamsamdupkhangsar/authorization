package me.sonam.auth.service;

import me.sonam.auth.jpa.entity.Client;
import me.sonam.auth.jpa.repo.ClientRepository;
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
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;

@Component
public class JpaRegisteredClientRepository {
    private static final Logger LOG = LoggerFactory.getLogger(JpaRegisteredClientRepository.class);
    private final ClientRepository clientRepository;
    private JsonMapper jsonMapper;

    public JpaRegisteredClientRepository(ClientRepository clientRepository) {
        Assert.notNull(clientRepository, "clientRepository cannot be null");
        this.clientRepository = clientRepository;

        ClassLoader classLoader = JpaRegisteredClientRepository.class.getClassLoader();
        jsonMapper = JsonMapper.builder()
                .addModules(SecurityJacksonModules.getModules(classLoader))
                .addModules(new OAuth2AuthorizationServerJacksonModule())
                .build();
    }

    public void save(RegisteredClient registeredClient) {
        save(registeredClient, "default");
    }

    public void save(RegisteredClient registeredClient, String tenantId) {
        Assert.notNull(registeredClient, "registeredClient cannot be null");
        this.clientRepository.save(toEntity(registeredClient, tenantId));
    }

    public RegisteredClient findById(String id) {
        LOG.info("findBy id: {}", id);
        Assert.hasText(id, "id cannot be empty");
        return this.clientRepository.findById(id).map(this::toObject).orElse(null);
    }

    public RegisteredClient findByClientId(String clientId) {
        LOG.info("findByClientId: {}", clientId);
        Assert.hasText(clientId, "clientId cannot be empty");
        return this.clientRepository.findByClientId(clientId)
                .map(this::toObject)
                .orElse(null);
    }

    private RegisteredClient toObject(Client client) {
        LOG.info("build RegisteredClient from client.id {}", client.getId());
        Set<String> clientAuthenticationMethods = StringUtils.commaDelimitedListToSet(
                client.getClientAuthenticationMethods());
        Set<String> authorizationGrantTypes = StringUtils.commaDelimitedListToSet(
                client.getAuthorizationGrantTypes());
        Set<String> redirectUris = StringUtils.commaDelimitedListToSet(
                client.getRedirectUris());
        Set<String> clientScopes = StringUtils.commaDelimitedListToSet(
                client.getScopes());

        RegisteredClient.Builder builder = RegisteredClient.withId(client.getId())
                .clientId(client.getClientId())
                .clientIdIssuedAt(client.getClientIdIssuedAt())
                .clientSecret(client.getClientSecret())
                .clientSecretExpiresAt(client.getClientSecretExpiresAt())
                .clientName(client.getClientName())
                .clientAuthenticationMethods(authenticationMethods ->
                        clientAuthenticationMethods.forEach(authenticationMethod ->
                                authenticationMethods.add(resolveClientAuthenticationMethod(authenticationMethod))))
                .authorizationGrantTypes((grantTypes) ->
                        authorizationGrantTypes.forEach(grantType ->
                                grantTypes.add(resolveAuthorizationGrantType(grantType))))
                .redirectUris((uris) -> uris.addAll(redirectUris))
                .scopes((scopes) -> scopes.addAll(clientScopes));

        Map<String, Object> clientSettingsMap = parseMap(client.getClientSettings());

        builder.clientSettings(ClientSettings.withSettings(clientSettingsMap).build());

        Map<String, Object> tokenSettingsMap = parseMap(client.getTokenSettings());
        builder.tokenSettings(TokenSettings.withSettings(tokenSettingsMap).build());

        LOG.info("returning registeredClient");
        return builder.build();
    }

    private Client toEntity(RegisteredClient registeredClient, String tenantId) {
        List<String> clientAuthenticationMethods = new ArrayList<>(registeredClient.getClientAuthenticationMethods().size());
        registeredClient.getClientAuthenticationMethods().forEach(clientAuthenticationMethod ->
                clientAuthenticationMethods.add(clientAuthenticationMethod.getValue()));

        List<String> authorizationGrantTypes = new ArrayList<>(registeredClient.getAuthorizationGrantTypes().size());
        registeredClient.getAuthorizationGrantTypes().forEach(authorizationGrantType ->
                authorizationGrantTypes.add(authorizationGrantType.getValue()));

        Client entity = new Client();
        entity.setId(registeredClient.getId());
        entity.setTenantId(tenantId);
        entity.setClientId(registeredClient.getClientId());
        entity.setClientIdIssuedAt(registeredClient.getClientIdIssuedAt());
        entity.setClientSecret(registeredClient.getClientSecret());
        entity.setClientSecretExpiresAt(registeredClient.getClientSecretExpiresAt());
        entity.setClientName(registeredClient.getClientName());
        entity.setClientAuthenticationMethods(StringUtils.collectionToCommaDelimitedString(clientAuthenticationMethods));
        entity.setAuthorizationGrantTypes(StringUtils.collectionToCommaDelimitedString(authorizationGrantTypes));
        entity.setRedirectUris(StringUtils.collectionToCommaDelimitedString(registeredClient.getRedirectUris()));
        entity.setScopes(StringUtils.collectionToCommaDelimitedString(registeredClient.getScopes()));
        entity.setClientSettings(writeMap(registeredClient.getClientSettings().getSettings()));
        entity.setTokenSettings(writeMap(registeredClient.getTokenSettings().getSettings()));

        return entity;
    }
    DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm");

    public RegisteredClient build(Map<String, Object> map) {
        String id;
        if (map.get("id") != null && !map.get("id").toString().trim().isEmpty()) {
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

        RegisteredClient.Builder registeredClientBuilder = RegisteredClient.withId(id)
                .clientId((String)map.get("clientId"))
                .clientSecret((String)map.get("clientSecret"))
                .clientName((String)map.get("clientName"))
                .clientAuthenticationMethods(authenticationMethods ->
                        clientAuthenticationMethods.forEach(authenticationMethod -> {
                            ClientAuthenticationMethod cam = resolveClientAuthenticationMethod(authenticationMethod);
                            LOG.info("cam: {}, cam.value: {}", cam, cam.getValue());
                            authenticationMethods.add(cam);
                        }))
                .authorizationGrantTypes(grantTypes ->
                        authorizationGrantTypes.forEach(authorizationGrantType -> {
                            AuthorizationGrantType agt = resolveAuthorizationGrantType(authorizationGrantType);
                            LOG.info("agt: {}, agt.value: {}", agt, agt.getValue());
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
                            LOG.info("add scope: {}", scope);
                        })
                )

                .clientSettings(ClientSettings.withSettings(parseMap(map.get("clientSettings").toString())).build())
                .tokenSettings(TokenSettings.withSettings(parseMap(map.get("tokenSettings").toString())).build());


        if (map.get("clientIdIssuedAt") != null) {
            registeredClientBuilder.clientIdIssuedAt(getInstant(map.get("clientIdIssuedAt").toString()));
        }
        if (map.get("clientSecretExpiresAt") != null) {
            registeredClientBuilder.clientSecretExpiresAt(getInstant(map.get("clientSecretExpiresAt").toString()));
        }

        return registeredClientBuilder.build();
    }

    private Instant getInstant(String secondsSinceEpochInExponential) {
        if (secondsSinceEpochInExponential == null || secondsSinceEpochInExponential.isEmpty()) {
            LOG.info("secondsSinceEpochInExponential is null/empty: {}", secondsSinceEpochInExponential);
            return null;
        }

        if (secondsSinceEpochInExponential.contains("-") || secondsSinceEpochInExponential.contains("T")) {
            Instant instant = Instant.parse(secondsSinceEpochInExponential);
            LOG.info("parsed iso instant: {}", instant);
            return instant;
        }

        long secondsSinceEpoch = Double.valueOf(secondsSinceEpochInExponential).longValue();
        Instant instant = Instant.ofEpochSecond(secondsSinceEpoch);

        LOG.info("secondsSinceEpoch {} to instant: {}", secondsSinceEpoch, instant);
        return instant;
    }

    public Map<String, String> getMap(RegisteredClient registeredClient) {
        List<String> clientAuthenticationMethods = new ArrayList<>(registeredClient.getClientAuthenticationMethods().size());
        registeredClient.getClientAuthenticationMethods().forEach(clientAuthenticationMethod ->
                clientAuthenticationMethods.add(clientAuthenticationMethod.getValue()));

        List<String> authorizationGrantTypes = new ArrayList<>(registeredClient.getAuthorizationGrantTypes().size());
        registeredClient.getAuthorizationGrantTypes().forEach(authorizationGrantType ->
                authorizationGrantTypes.add(authorizationGrantType.getValue()));

        Map<String, String> map = new HashMap<>();
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

    public Map<String, Object> getMapObject(RegisteredClient registeredClient) {
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
        map.put("clientIdIssuedAt", registeredClient.getClientIdIssuedAt());
        map.put("clientSecretExpiresAt", registeredClient.getClientSecretExpiresAt());
        map.put("clientName", registeredClient.getClientName());
        map.put("clientAuthenticationMethods", StringUtils.collectionToCommaDelimitedString(clientAuthenticationMethods));
        map.put("authorizationGrantTypes", StringUtils.collectionToCommaDelimitedString(authorizationGrantTypes));
        map.put("redirectUris", StringUtils.collectionToCommaDelimitedString(registeredClient.getRedirectUris()));
        map.put("scopes", StringUtils.collectionToCommaDelimitedString(registeredClient.getScopes()));
        map.put("clientSettings", writeMap(registeredClient.getClientSettings().getSettings()));
        map.put("tokenSettings", writeMap(registeredClient.getTokenSettings().getSettings()));

        LOG.info("map contains: {}", map);
        return map;
    }

    public Map<String, Object> parseMap(String data) {
        try {
            return this.jsonMapper.readValue(data, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    private String writeMap(Map<String, Object> data) {
        try {
            String  string =  this.jsonMapper.writeValueAsString(data);
            LOG.info("data: {}, string: {}", data, string);
            return string;
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    private static AuthorizationGrantType resolveAuthorizationGrantType(String authorizationGrantType) {
        if (AuthorizationGrantType.AUTHORIZATION_CODE.getValue().equals(authorizationGrantType)) {
            return AuthorizationGrantType.AUTHORIZATION_CODE;
        }
        else if (AuthorizationGrantType.CLIENT_CREDENTIALS.getValue().equals(authorizationGrantType)) {
            return AuthorizationGrantType.CLIENT_CREDENTIALS;
        }
        else if (AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(authorizationGrantType)) {
            return AuthorizationGrantType.REFRESH_TOKEN;
        }
        else if (AuthorizationGrantType.DEVICE_CODE.getValue().equals(authorizationGrantType)) {
            return AuthorizationGrantType.DEVICE_CODE;
        }
        else if (AuthorizationGrantType.TOKEN_EXCHANGE.getValue().equals(authorizationGrantType)) {
            return AuthorizationGrantType.TOKEN_EXCHANGE;
        }

        return new AuthorizationGrantType(authorizationGrantType);              // Custom authorization grant type
    }

    private static ClientAuthenticationMethod resolveClientAuthenticationMethod(String clientAuthenticationMethod) {
        if (ClientAuthenticationMethod.CLIENT_SECRET_BASIC.getValue().equals(clientAuthenticationMethod)) {
            return ClientAuthenticationMethod.CLIENT_SECRET_BASIC;
        }
        else if (ClientAuthenticationMethod.CLIENT_SECRET_POST.getValue().equals(clientAuthenticationMethod)) {
            return ClientAuthenticationMethod.CLIENT_SECRET_POST;
        }
        else if (ClientAuthenticationMethod.CLIENT_SECRET_JWT.getValue().equals(clientAuthenticationMethod)) {
            return ClientAuthenticationMethod.CLIENT_SECRET_JWT;
        }
        else if (ClientAuthenticationMethod.PRIVATE_KEY_JWT.getValue().equals(clientAuthenticationMethod)) {
            return ClientAuthenticationMethod.PRIVATE_KEY_JWT;
        }
        else if (ClientAuthenticationMethod.TLS_CLIENT_AUTH.getValue().equals(clientAuthenticationMethod)) {
            return ClientAuthenticationMethod.TLS_CLIENT_AUTH;
        }
        else if (ClientAuthenticationMethod.SELF_SIGNED_TLS_CLIENT_AUTH.getValue().equals(clientAuthenticationMethod)) {
            return ClientAuthenticationMethod.SELF_SIGNED_TLS_CLIENT_AUTH;
        }
        else if (ClientAuthenticationMethod.NONE.getValue().equals(clientAuthenticationMethod)) {
            return ClientAuthenticationMethod.NONE;
        }
        return new ClientAuthenticationMethod(clientAuthenticationMethod);      // Custom client authentication method
    }
}
