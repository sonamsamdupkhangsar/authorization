package me.sonam.auth.util;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.context.ApplicationListener;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.session.SessionDestroyedEvent;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SessionExpired implements ApplicationListener<SessionDestroyedEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(SessionExpired.class);

    @Override
    public void onApplicationEvent(SessionDestroyedEvent event)
    {
        List<SecurityContext> lstSecurityContext = event.getSecurityContexts();
        LOG.info("event session expired: {}, event: {}", event.getId(), event);
    }

}
