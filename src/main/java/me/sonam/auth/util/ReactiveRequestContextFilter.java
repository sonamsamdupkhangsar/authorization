package me.sonam.auth.util;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.io.IOException;

import static me.sonam.auth.util.TokenRequestFilter.RequestFilter.AccessToken.JwtOption.request;

//@Configuration
//@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
//@Component
public class ReactiveRequestContextFilter  {
    private static final Logger LOG = LoggerFactory.getLogger(ReactiveRequestContextFilter.class);


  // @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        LOG.error("reactive request: {}", request.getPath());

        return chain.filter(exchange).contextWrite(context -> context.put(TokenFilter.CONTEXT_KEY, request));
    }

}