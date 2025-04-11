package me.sonam.auth.config;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * With the RequestContextFilter registered in {@link FilterConfig}, the HttpServletRequest can be accessed using RequestContextHolder.
 */

//@Component
public class RequestContextAccessor {
    private static final Logger LOG = LoggerFactory.getLogger(RequestContextAccessor.class);

    public HttpServletRequest getCurrentRequest() {
        LOG.info("access servletRequest");

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        LOG.info("attributes: {}", attributes);
        return attributes != null ? attributes.getRequest() : null;
    }
}