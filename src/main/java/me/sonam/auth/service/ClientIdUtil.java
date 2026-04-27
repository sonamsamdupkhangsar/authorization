package me.sonam.auth.service;

//import org.apache.hc.core5.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class ClientIdUtil {
    private static final Logger LOG = LoggerFactory.getLogger(ClientIdUtil.class);

    public static String getClientId(RequestCache requestCache) {
        var requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        var request = requestAttributes.getRequest();
        var response = requestAttributes.getResponse();
        String requestClientId = request.getParameter(OAuth2ParameterNames.CLIENT_ID);
        if (StringUtils.hasText(requestClientId)) {
            LOG.debug("using client_id from current login request");
            return requestClientId;
        }

        var savedRequest = requestCache.getRequest(request, response);
        return ClientIdUtil.getParameter(savedRequest, OAuth2ParameterNames.CLIENT_ID);
    }

    private static String getParameter(SavedRequest savedRequest, String parameterName) {
        if (savedRequest == null) {
            LOG.error("savedRequest is null");
            return "";
        }
        var parameterValues = savedRequest.getParameterValues(parameterName);
        if (parameterValues == null) {
            LOG.error("parameterValues is null");
            return "";
        }
        if (parameterValues.length != 1) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
        }
        return parameterValues[0];
    }


}
