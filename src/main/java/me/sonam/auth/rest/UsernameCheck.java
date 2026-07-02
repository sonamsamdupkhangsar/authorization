package me.sonam.auth.rest;


import jakarta.servlet.http.HttpServletRequest;
import me.sonam.auth.webclient.AuthenticationWebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/users/")
public class UsernameCheck {
    private static final Logger LOG = LoggerFactory.getLogger(UsernameCheck.class);

    private final AuthenticationWebClient authenticationWebClient;

    public UsernameCheck(AuthenticationWebClient authenticationWebClient) {
        this.authenticationWebClient = authenticationWebClient;
    }

    @PutMapping("/username")
    public Mono<String> checkUsername(HttpServletRequest request, @RequestBody  String username) {
        LOG.info("username availability check requested");
        if (username == null) {
            LOG.error("no username set");
        }

        String ipAddress = request.getRemoteAddr();
        LOG.debug("username check includes remote-address metadata");

        return authenticationWebClient.checkUsername(ipAddress, username).flatMap(s -> {
            LOG.info("add message attribute for response {}",s);
            return Mono.just(s);
        }).onErrorResume(throwable -> Mono.just(throwable.getMessage()));
    }
}
