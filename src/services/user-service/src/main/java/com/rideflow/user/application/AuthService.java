package com.rideflow.user.application;

import com.rideflow.user.application.port.out.PasswordHasher;
import com.rideflow.user.application.port.out.TokenIssuer;
import com.rideflow.user.application.port.out.UserRepository;
import com.rideflow.user.domain.model.Role;
import com.rideflow.user.domain.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    public static class DuplicateEmailException extends RuntimeException {
        public DuplicateEmailException() {
            super("Email already registered");
        }
    }

    /** Cùng một thông điệp cho sai email và sai mật khẩu để không lộ email nào tồn tại. */
    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("Invalid email or password");
        }
    }

    public record LoginResult(String token, User user) {
    }

    private final UserRepository repository;
    private final PasswordHasher hasher;
    private final TokenIssuer tokens;

    public AuthService(UserRepository repository, PasswordHasher hasher, TokenIssuer tokens) {
        this.repository = repository;
        this.hasher = hasher;
        this.tokens = tokens;
    }

    /** shortcut: check-then-insert, hai đăng ký song song cùng email có thể 500 do unique index; chấp nhận cho demo. */
    @Transactional
    public User register(String email, String password, String fullName, Role role) {
        if (role == Role.ADMIN) {
            throw new IllegalArgumentException("ADMIN cannot self-register");
        }
        String normalized = email.trim().toLowerCase();
        if (repository.existsByEmail(normalized)) {
            throw new DuplicateEmailException();
        }
        var user = new User(UUID.randomUUID(), normalized, fullName, role, null, Instant.now());
        return repository.save(user, hasher.hash(password));
    }

    @Transactional(readOnly = true)
    public LoginResult login(String email, String password) {
        var credential = repository.findCredentialByEmail(email.trim().toLowerCase())
                .filter(c -> hasher.matches(password, c.passwordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        return new LoginResult(tokens.issue(credential.user()), credential.user());
    }
}
