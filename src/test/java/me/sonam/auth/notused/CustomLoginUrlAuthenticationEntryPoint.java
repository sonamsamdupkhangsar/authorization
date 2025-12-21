package me.sonam.auth.notused;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.UrlUtils;


public class CustomLoginUrlAuthenticationEntryPoint extends LoginUrlAuthenticationEntryPoint {

    private static final Logger LOG = LoggerFactory.getLogger(CustomLoginUrlAuthenticationEntryPoint.class);

    public CustomLoginUrlAuthenticationEntryPoint(String loginFormUrl) {
        super(loginFormUrl);
    }

    @Override
    protected String determineUrlToUseForThisRequest(HttpServletRequest request, HttpServletResponse response,
                                                     AuthenticationException exception) {

        LOG.info("adding a query param to login entry point");
        String continueParamValue = UrlUtils.buildRequestUrl(request);
        String redirect = super.determineUrlToUseForThisRequest(request, response, exception);
        //return UriComponentsBuilder.fromPath(redirect).queryParam("client_id", "hello").toUriString();

        String url = super.determineUrlToUseForThisRequest(request, response, exception) + "?jack=sonam";
        LOG.info("url: {}", url);
        return url;

    }
}
