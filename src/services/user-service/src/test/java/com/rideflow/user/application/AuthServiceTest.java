package com.rideflow.user.application;

import com.rideflow.user.application.port.out.PasswordHasher;
import com.rideflow.user.application.port.out.TokenIssuer;
import com.rideflow.user.application.port.out.UserRepository;
import com.rideflow.user.domain.model.Role;
import com.rideflow.user.domain.model.User;
import com.rideflow.user.domain.model.UserCredential;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final UserRepository repository = mock(UserRepository.class);
    private final PasswordHasher hasher = mock(PasswordHasher.class);
    private final TokenIssuer tokens = mock(TokenIssuer.class);
    private final AuthService service = new AuthService(repository, hasher, tokens);

    private final UUID driverId = UUID.randomUUID();
    private final User user = new User(UUID.randomUUID(), "a@x.com", "A", Role.DRIVER, driverId, Instant.now());

    @Test
    void registerStoresHashNotPlainPassword() {
        when(repository.existsByEmail("a@x.com")).thenReturn(false);
        when(hasher.hash("secret123")).thenReturn("HASH");
        when(repository.save(any(), anyString())).thenAnswer(i -> i.getArgument(0));

        User saved = service.register("A@x.com", "secret123", "A", Role.PASSENGER);

        assertThat(saved.email()).isEqualTo("a@x.com");
        verify(repository).save(saved, "HASH");
    }

    @Test
    void registerDuplicateEmailFails() {
        when(repository.existsByEmail("a@x.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register("a@x.com", "secret123", "A", Role.PASSENGER))
                .isInstanceOf(AuthService.DuplicateEmailException.class);
        verify(repository, never()).save(any(), anyString());
    }

    @Test
    void adminCannotSelfRegister() {
        assertThatThrownBy(() -> service.register("a@x.com", "secret123", "A", Role.ADMIN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loginReturnsTokenAndDriverId() {
        when(repository.findCredentialByEmail("a@x.com")).thenReturn(Optional.of(new UserCredential(user, "HASH")));
        when(hasher.matches("secret123", "HASH")).thenReturn(true);
        when(tokens.issue(user)).thenReturn("jwt");

        var result = service.login("a@x.com", "secret123");

        assertThat(result.token()).isEqualTo("jwt");
        assertThat(result.user().driverId()).isEqualTo(driverId);
    }

    @Test
    void wrongPasswordAndUnknownEmailFailTheSameWay() {
        when(repository.findCredentialByEmail("a@x.com")).thenReturn(Optional.of(new UserCredential(user, "HASH")));
        when(hasher.matches("bad", "HASH")).thenReturn(false);
        when(repository.findCredentialByEmail("none@x.com")).thenReturn(Optional.empty());

        var wrongPw = catchAuth(() -> service.login("a@x.com", "bad"));
        var noUser = catchAuth(() -> service.login("none@x.com", "whatever"));

        assertThat(wrongPw).isInstanceOf(AuthService.InvalidCredentialsException.class);
        assertThat(wrongPw.getMessage()).isEqualTo(noUser.getMessage());
    }

    private static Throwable catchAuth(Runnable r) {
        try {
            r.run();
            return null;
        } catch (RuntimeException e) {
            return e;
        }
    }
}
