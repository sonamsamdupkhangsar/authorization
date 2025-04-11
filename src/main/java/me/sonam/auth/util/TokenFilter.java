package me.sonam.auth.util;

import me.sonam.auth.config.RequestContextAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * this token filter will be invoked automatically by the webclient for intercepting request
 * to add a access-token by making a client-credential flow http call.
 * Don't add it manually to a webclient to avoid getting calling twice.
 */
@Service
public class TokenFilter {
    private static final Logger LOG = LoggerFactory.getLogger(TokenFilter.class);

    static final Class<ServerHttpRequest> CONTEXT_KEY = ServerHttpRequest.class;

    @Value("${auth-server.root}${auth-server.context-path}${auth-server.oauth2token.path}")
    private String oauth2TokenEndpoint;

    @Value("${auth-server.oauth2token.grantType}")
    private String grantType;

    @Value("${user-rest-service.root}${user-rest-service.userByAuthId}")
    private String userByAuthIdEp;

    @Autowired
    private TokenRequestFilter tokenRequestFilter;

    private final WebClient.Builder webClientBuilder;

    private RequestCache requestCache;
    @Value("${server.servlet.context-path}${auth-server.oauth2token.path:}")
    private String accessTokenPath;

    @Value("${server.servlet.context-path}")
    private String servletContextPath;

    @Value("${tokenExpireSeconds}")
    private int tokenExpireSeconds;

    public TokenFilter(WebClient.Builder webClientBuilder, RequestCache requestCache) {
        this.webClientBuilder = webClientBuilder;
        this.requestCache = requestCache;
    }

    public static Mono<ServerHttpRequest> getRequest() {
        return Mono.deferContextual(Mono::just)// returns .Mono<ContextView>
                .map(ctx -> ctx.get(CONTEXT_KEY)).map(serverHttpRequest -> {
                    return serverHttpRequest;
                });
    }

    public ExchangeFilterFunction renewTokenFilter() {
        return (request, next) -> {
            LOG.info("outbound request path: {}", request.url().getPath());
            LOG.info("accessTokenPath: {}", accessTokenPath);

            String requestPathWithServeletContextPath = request.url().getPath();
            if (!request.url().getPath().startsWith(servletContextPath)) {
                requestPathWithServeletContextPath = servletContextPath + requestPathWithServeletContextPath;
            }

            if (requestPathWithServeletContextPath.equals(accessTokenPath)) {
                LOG.debug("no need to request access token when going to that path: {}", request.url().getPath());
                ClientRequest clientRequest = ClientRequest.from(request).build();
                return next.exchange(clientRequest);
            }
            else {
                return processTokenFilter(request, next);
            }
        };
    }

    private Mono<ClientResponse> processTokenFilter(ClientRequest request, ExchangeFunction next) {
        LOG.debug("going thru request filters") ;
        int index = 0;

        for (TokenRequestFilter.RequestFilter requestFilter : tokenRequestFilter.getRequestFilters()) {
            LOG.info("checking requestFilter[{}]  {}", index++, requestFilter);

            if (!requestFilter.getOutHttpMethods().isEmpty()) {

                LOG.info("outHttpMethods: {} provided, actual outbound httpMethod: {}", requestFilter.getOutHttpMethodSet(),
                        request.method().name());

                if (requestFilter.getOutHttpMethodSet().contains(request.method().name().toLowerCase())) {

                    boolean matchOutPath = requestFilter.getOutSet().stream().anyMatch(w -> {
                        boolean value = request.url().getPath().trim().matches(w);
                        LOG.debug("request path {}, regex expression '{}' matches? : {}", request.url().getPath().trim(), w, value);
                        return value;
                    });
                    if (matchOutPath) {
                        LOG.info("outbound path matched");
                        try {
                            return passAccessToken(request, next, requestFilter.getAccessToken());
                        }
                        catch (Exception e) {
                            LOG.error("Exception occurred, clear out access token", e);
                            requestFilter.getAccessToken().setAccessToken(null);
                            return Mono.error(e);
                        }
                    } else {
                        LOG.info("no match found for outbound path {} ", request.url().getPath());
                    }
                }
            }
            else if (requestFilter.getOutHttpMethods().isEmpty() && requestFilter.getOut().isEmpty()) {
                LOG.info("user request filter to apply a general filter when out http method and out path is empty");
                try {
                    return passAccessToken(request, next, requestFilter.getAccessToken());
                }
                catch (Exception e) {
                    LOG.error("Exception occurred, clear out access token", e);
                    requestFilter.getAccessToken().setAccessToken(null);
                    return Mono.error(e);
                }
            }
            else {
                LOG.error("either outbound request method and out path must be specified or leave them empty");
            }
        }
        LOG.info("no out match found");
        ClientRequest filtered = ClientRequest.from(request).build();
        return next.exchange(filtered);
    }

    private Mono<ClientResponse> passAccessToken(ClientRequest request, ExchangeFunction next,
                                                                      TokenRequestFilter.RequestFilter.AccessToken accessToken) {
        LOG.info("pass inbound token, request or do nothing");

        if (accessToken.getOption().equals(TokenRequestFilter.RequestFilter.AccessToken.JwtOption.forward)) {
            LOG.info("option is forward token");

            return ReactiveSecurityContextHolder.getContext().
                    map(securityContext -> securityContext.getAuthentication().getPrincipal())
                    .cast(Jwt.class).flatMap(jwt -> {
                        LOG.info("got accessToken inbound jwt.getTokenValue: {}, jwt: {}", jwt.getTokenValue(), jwt);
                        ClientRequest clientRequest = getClientRequest(jwt.getTokenValue(), request, next);

                        return next.exchange(clientRequest);
                    }).switchIfEmpty(requestToken(request, next, accessToken));//if not jwt found, then switch to empty method
        }
        else if (accessToken.getOption().equals(TokenRequestFilter.RequestFilter.AccessToken.JwtOption.request)) {

            if (accessToken.getAccessToken() != null && !isExpired(accessToken.getAccessTokenCreationTime())) {
                LOG.info("accessToken object contains a accessToken that is not expired");
                return getClientRequestWithHeader(accessToken.getAccessToken(), request, next);
            }
            else {
                LOG.info("accessToken does not contain a un-expired token");
                return getAccessToken(oauth2TokenEndpoint, grantType, accessToken.getScopes(), accessToken.getBase64EncodedClientIdSecret())
                        .flatMap(jwtAccessToken -> {
                            LOG.info("set token in access-token");
                            accessToken.setAccessToken(jwtAccessToken);

                            ClientRequest clientRequest = getClientRequest(accessToken.getAccessToken(), request, next);
                            return Mono.just(clientRequest);
                        }).flatMap(next::exchange);
            }
        } // there is no need to forward as there is no inbound token coming in, just requests going out
        else {
            LOG.info("do nothing");
            ClientRequest filtered = ClientRequest.from(request).build();
            return next.exchange(filtered);
        }
    }

    private Mono<ClientResponse> requestToken(ClientRequest request, ExchangeFunction next, TokenRequestFilter.RequestFilter.AccessToken accessToken) {
        LOG.info("request token");
        return  getAccessToken(oauth2TokenEndpoint, grantType, accessToken.getScopes(), accessToken.getBase64EncodedClientIdSecret())
                .flatMap(jwtAccessToken -> {
                    LOG.info("set token in access-token");
                    accessToken.setAccessToken(jwtAccessToken);

                    ClientRequest clientRequest = getClientRequest(accessToken.getAccessToken(), request, next);
                    return Mono.just(clientRequest);
                }).flatMap(next::exchange);
    }

    private Mono<ClientResponse> getClientRequestWithHeader(String accessToken, ClientRequest request, ExchangeFunction next) {
        ClientRequest clientRequest = getClientRequest(accessToken, request, next);
        return next.exchange(clientRequest);
    }

    private ClientRequest getClientRequest(String accessToken, ClientRequest request, ExchangeFunction next) {
        return ClientRequest.from(request)
                .headers(headers -> {
                    headers.set(HttpHeaders.ORIGIN, request.headers().getFirst(HttpHeaders.ORIGIN));
                    if (accessToken != null) {
                        headers.setBearerAuth(accessToken);
                        LOG.info("set authorization header with {}", accessToken);
                    }
                }).build();
    }

    private boolean isExpired(LocalDateTime tokenTime) {
        LocalDateTime tokenExpiredTime = LocalDateTime.now().minus(Duration.ofSeconds(tokenExpireSeconds));

        return tokenTime.isBefore(tokenExpiredTime);
    }

    private Mono<String> getAccessToken(final String oauthEndpoint, String grantType, String scopes, final String base64EncodeClientIdSecret) {
        LOG.info("making a access-token request to endpoint: {}",oauthEndpoint);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("grant_type", grantType);

        if (scopes != null && !scopes.isEmpty()) {
            body.add("scope", scopes);
            LOG.info("added scope to body: {}", scopes);
        }
        else {
            LOG.info("scope is null, not adding to body");
        }

        LOG.info("add body payload for grant type and scopes: {}", body);

        WebClient.ResponseSpec responseSpec = webClientBuilder.build().post().uri(oauthEndpoint)
                .bodyValue(body)
                .headers(httpHeaders -> httpHeaders.setBasicAuth(base64EncodeClientIdSecret))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve();

        return responseSpec.bodyToMono(Map.class).map(map -> {
            LOG.debug("response for '{}' is in map: {}", oauthEndpoint, map);
            if (map.get("access_token") != null) {
                return map.get("access_token").toString();
            }
            else {
                LOG.error("nothing to return");
                return "nothing";
            }
        }).onErrorResume(throwable -> {
            LOG.error("client credentials access token rest call failed: {}", throwable.getMessage());
            String errorMessage = throwable.getMessage();

            if (throwable instanceof WebClientResponseException) {
                WebClientResponseException webClientResponseException = (WebClientResponseException) throwable;
                LOG.error("error body contains: {}", webClientResponseException.getResponseBodyAsString());
                errorMessage = webClientResponseException.getResponseBodyAsString();
            }
            return Mono.error(new RuntimeException(errorMessage));
        });
    }
}
