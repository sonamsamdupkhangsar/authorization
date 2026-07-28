package me.sonam.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.sonam.auth.account.AccountProfileController;
import me.sonam.auth.account.AccountProfileForm;
import me.sonam.auth.rest.signup.User;
import me.sonam.auth.webclient.UserWebClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.ui.ConcurrentModel;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.AdditionalMatchers.aryEq;

@ExtendWith(MockitoExtension.class)
class AccountProfileControllerTest {
    private final UUID userId = UUID.randomUUID();

    @Mock
    private UserWebClient userWebClient;

    private AccountProfileController controller;
    private UsernamePasswordAuthenticationToken authentication;
    private User user;

    @BeforeEach
    void setUp() {
        controller = new AccountProfileController(userWebClient, new ObjectMapper());
        authentication = new UsernamePasswordAuthenticationToken("user1", "");
        user = new User();
        user.setId(userId);
        user.setAuthenticationId("user1");
        user.setEmail("user1@example.com");
        user.setFirstName("Old");
        user.setLastName("Name");
        user.setSearchable(false);
    }

    @Test
    void profileLoadsAuthenticatedUser() {
        when(userWebClient.getUserId("user1")).thenReturn(Mono.just(userId));
        when(userWebClient.getUserById(userId)).thenReturn(Mono.just(user));
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.profile(authentication, null, model).block();

        assertThat(view).isEqualTo("account/profile");
        assertThat(model.getAttribute("user")).isSameAs(user);
        assertThat(model.getAttribute("profile")).isInstanceOf(AccountProfileForm.class);
    }

    @Test
    void updateTargetsAuthenticatedUserAndPreservesReadOnlyIdentity() {
        when(userWebClient.getUserId("user1")).thenReturn(Mono.just(userId));
        when(userWebClient.getUserById(userId)).thenReturn(Mono.just(user));
        when(userWebClient.updateProfile(user)).thenReturn(Mono.just("updated"));
        AccountProfileForm form = new AccountProfileForm();
        form.setFirstName(" New ");
        form.setLastName(" Person ");
        form.setSearchable(true);

        String view = controller.updateProfile(authentication, form).block();

        assertThat(view).isEqualTo("redirect:/account/profile?updated");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userWebClient).updateProfile(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(userId);
        assertThat(captor.getValue().getAuthenticationId()).isEqualTo("user1");
        assertThat(captor.getValue().getEmail()).isEqualTo("user1@example.com");
        assertThat(captor.getValue().getFirstName()).isEqualTo("New");
        assertThat(captor.getValue().getLastName()).isEqualTo("Person");
        assertThat(captor.getValue().getSearchable()).isTrue();
    }

    @Test
    void photoUploadUsesAuthenticatedUsername() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "profile.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1, 2, 3});
        when(userWebClient.updateProfilePhoto(
                org.mockito.ArgumentMatchers.eq("user1"),
                org.mockito.ArgumentMatchers.eq("profile.png"),
                org.mockito.ArgumentMatchers.eq(MediaType.IMAGE_PNG),
                aryEq(new byte[]{1, 2, 3})))
                .thenReturn(Mono.just(Map.of("thumbnailUrl", "https://example.com/profile.png")));

        String view = controller.updateProfilePhoto(authentication, file).block();

        assertThat(view).isEqualTo("redirect:/account/profile?photoUpdated");
        verify(userWebClient).updateProfilePhoto(
                org.mockito.ArgumentMatchers.eq("user1"),
                org.mockito.ArgumentMatchers.eq("profile.png"),
                org.mockito.ArgumentMatchers.eq(MediaType.IMAGE_PNG),
                aryEq(new byte[]{1, 2, 3}));
    }
}
