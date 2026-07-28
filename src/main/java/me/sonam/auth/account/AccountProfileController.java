package me.sonam.auth.account;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.sonam.auth.rest.signup.User;
import me.sonam.auth.webclient.UserWebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;

@Controller
@RequestMapping("/account")
public class AccountProfileController {
    private static final Logger LOG = LoggerFactory.getLogger(AccountProfileController.class);
    private static final String PROFILE_TEMPLATE = "account/profile";

    private final UserWebClient userWebClient;
    private final ObjectMapper objectMapper;

    public AccountProfileController(UserWebClient userWebClient, ObjectMapper objectMapper) {
        this.userWebClient = userWebClient;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public String account() {
        return "redirect:/account/profile";
    }

    @GetMapping("/profile")
    public Mono<String> profile(Authentication authentication,
                                @RequestParam(required = false) String updated,
                                Model model) {
        return loadAuthenticatedUser(authentication)
                .doOnNext(user -> {
                    model.addAttribute("user", user);
                    model.addAttribute("profilePhotoUrl", profilePhotoUrl(user.getProfilePhoto()));
                    model.addAttribute("profile", toForm(user));
                    model.addAttribute("updated", updated != null);
                })
                .thenReturn(PROFILE_TEMPLATE);
    }

    @PostMapping(path = "/profile/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<String> updateProfilePhoto(Authentication authentication,
                                           @RequestPart("file") MultipartFile file) {
        if (file.isEmpty() || file.getContentType() == null) {
            return Mono.error(new IllegalArgumentException("Select an image to upload"));
        }
        return userWebClient.updateProfilePhoto(authentication.getName(), file.getOriginalFilename(),
                        MediaType.parseMediaType(file.getContentType()), bytes(file))
                .thenReturn("redirect:/account/profile?photoUpdated")
                .onErrorResume(throwable -> {
                    LOG.error("Profile photo upload failed: {}", throwable.getMessage());
                    return Mono.just("redirect:/account/profile?photoError");
                });
    }

    @PostMapping("/profile")
    public Mono<String> updateProfile(Authentication authentication,
                                      @ModelAttribute("profile") AccountProfileForm profile) {
        return loadAuthenticatedUser(authentication)
                .flatMap(user -> {
                    user.setFirstName(trim(profile.getFirstName()));
                    user.setLastName(trim(profile.getLastName()));
                    user.setSearchable(profile.isSearchable());
                    LOG.info("update profile for authenticated user");
                    return userWebClient.updateProfile(user);
                })
                .thenReturn("redirect:/account/profile?updated");
    }

    private Mono<User> loadAuthenticatedUser(Authentication authentication) {
        return userWebClient.getUserId(authentication.getName())
                .flatMap(userWebClient::getUserById);
    }

    private AccountProfileForm toForm(User user) {
        AccountProfileForm form = new AccountProfileForm();
        form.setFirstName(user.getFirstName());
        form.setLastName(user.getLastName());
        form.setSearchable(Boolean.TRUE.equals(user.getSearchable()));
        return form;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("Could not read the selected image", exception);
        }
    }

    private String profilePhotoUrl(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(metadata);
            if (node.isTextual()) {
                node = objectMapper.readTree(node.asText());
            }
            return node.path("thumbnailUrl").asText("");
        } catch (Exception exception) {
            LOG.warn("Could not read profile photo metadata");
            return "";
        }
    }
}
