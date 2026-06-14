package me.sonam.auth.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import me.sonam.auth.service.ClientIdUtil;
import me.sonam.auth.service.LoginReturnContextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * When user is redirected from their application to the Authorization server via their OAuth Client
 * application this controller will return the index thymeleaf page.
 */
@Controller
public class IndexController {
    private static final Logger LOG = LoggerFactory.getLogger(IndexController.class);

    @Value("${authzmanager}")
    private String authzManagerUrl;

    private final RequestCache requestCache;
    private final LoginReturnContextService loginReturnContextService;

    public IndexController(RequestCache requestCache, LoginReturnContextService loginReturnContextService) {
        this.requestCache = requestCache;
        this.loginReturnContextService = loginReturnContextService;
    }


    @GetMapping("/")
    public String index(Model model, HttpSession httpSession, HttpServletRequest request, HttpServletResponse response) {
        LOG.info("returning index");

        model.addAttribute("authzmanager", authzManagerUrl);
        addSavedClientId(model, request, response);
        loginReturnContextService.addReturnContext(model, request, response);

        return "index";
    }

    private void addSavedClientId(Model model, HttpServletRequest request, HttpServletResponse response) {
        SavedRequest savedRequest = requestCache.getRequest(request, response);
        if (savedRequest == null) {
            LOG.debug("no saved OAuth request found for login page");
            Object sessionClientId = request.getSession().getAttribute(ClientIdUtil.CLIENT_ID_SESSION_ATTRIBUTE);
            if (request.getParameter("error") != null
                    && sessionClientId != null
                    && StringUtils.hasText(sessionClientId.toString())) {
                model.addAttribute("clientId", sessionClientId.toString());
                LOG.debug("added session OAuth client_id to login form");
            }
            return;
        }

        String[] clientIds = savedRequest.getParameterValues(OAuth2ParameterNames.CLIENT_ID);
        if (clientIds == null || clientIds.length == 0 || !StringUtils.hasText(clientIds[0])) {
            LOG.debug("saved OAuth request did not contain client_id");
            return;
        }

        model.addAttribute("clientId", clientIds[0]);
        request.getSession().setAttribute(ClientIdUtil.CLIENT_ID_SESSION_ATTRIBUTE, clientIds[0]);
        LOG.debug("added saved OAuth client_id to login form");
    }
}
