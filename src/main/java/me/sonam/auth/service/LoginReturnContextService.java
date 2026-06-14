package me.sonam.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;

import java.net.URI;

@Service
public class LoginReturnContextService {
    private static final Logger LOG = LoggerFactory.getLogger(LoginReturnContextService.class);
    public static final String RETURN_TO_SESSION_ATTRIBUTE = "OAUTH2_RETURN_TO";
    public static final String RETURN_TO_MODEL_ATTRIBUTE = "returnTo";

    private final RequestCache requestCache;

    public LoginReturnContextService(RequestCache requestCache) {
        this.requestCache = requestCache;
    }

    public void addReturnContext(Model model, HttpServletRequest request, HttpServletResponse response) {
        String returnTo = resolveReturnTo(request, response);
        if (!StringUtils.hasText(returnTo)) {
            return;
        }

        model.addAttribute(RETURN_TO_MODEL_ATTRIBUTE, returnTo);
        request.getSession().setAttribute(RETURN_TO_SESSION_ATTRIBUTE, returnTo);
    }

    private String resolveReturnTo(HttpServletRequest request, HttpServletResponse response) {
        String requestReturnTo = request.getParameter(RETURN_TO_MODEL_ATTRIBUTE);
        if (StringUtils.hasText(requestReturnTo) && isSafeReturnTo(requestReturnTo, request)) {
            return requestReturnTo;
        }

        SavedRequest savedRequest = requestCache.getRequest(request, response);
        if (savedRequest != null && StringUtils.hasText(savedRequest.getRedirectUrl())) {
            return savedRequest.getRedirectUrl();
        }

        Object sessionReturnTo = request.getSession().getAttribute(RETURN_TO_SESSION_ATTRIBUTE);
        if (sessionReturnTo != null
                && StringUtils.hasText(sessionReturnTo.toString())
                && isSafeReturnTo(sessionReturnTo.toString(), request)) {
            return sessionReturnTo.toString();
        }

        return null;
    }

    private boolean isSafeReturnTo(String returnTo, HttpServletRequest request) {
        try {
            URI uri = URI.create(returnTo);
            if (!uri.isAbsolute()) {
                return returnTo.startsWith("/") && !returnTo.startsWith("//");
            }
            return request.getServerName().equalsIgnoreCase(uri.getHost());
        }
        catch (IllegalArgumentException e) {
            LOG.warn("ignoring invalid returnTo value: {}", returnTo);
            return false;
        }
    }
}
