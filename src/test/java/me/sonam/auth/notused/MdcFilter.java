package me.sonam.auth.notused;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class MdcFilter implements Filter {
    private static final Logger LOG = LoggerFactory.getLogger(MdcFilter.class);

    private static final String REQUEST_ID = "requestId";
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        LOG.trace("checking MDC filter for request");
        HttpServletResponse httpServletResponse = (HttpServletResponse) response;
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;

        if (httpServletRequest.getHeader(REQUEST_ID) != null) {
            String requestId = httpServletRequest.getHeader(REQUEST_ID);
            LOG.trace("add requestId to MDC: {}", requestId);
            MDC.put(REQUEST_ID, requestId);
        }

        chain.doFilter(request, response);
        LOG.trace("remove MDC {}", REQUEST_ID);
        MDC.remove(REQUEST_ID);
    }
}
