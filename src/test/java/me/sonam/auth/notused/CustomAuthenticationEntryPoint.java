package me.sonam.auth.notused;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private static final Logger LOG = LoggerFactory.getLogger(CustomAuthenticationEntryPoint.class);

    private final RequestCache requestCache = new HttpSessionRequestCache();
    private final String loginPageUrl;

    public CustomAuthenticationEntryPoint(String loginPageUrl) {
        this.loginPageUrl = loginPageUrl;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {

        // Save the original request, including the clientId parameter
        requestCache.saveRequest(request, response);

        String clientId = request.getParameter("client_id");

        String redirectUrl =  UriComponentsBuilder.fromUriString(loginPageUrl)
                .queryParam("clientId", clientId)
                .build().toUriString();

        String removeFirstSlash = redirectUrl.replaceFirst("/", "");
        String newRedirectUrl = "/issuer" + removeFirstSlash;
        response.sendRedirect(newRedirectUrl);
        LOG.info("newRedirecting to url: {}", newRedirectUrl);
    }
}