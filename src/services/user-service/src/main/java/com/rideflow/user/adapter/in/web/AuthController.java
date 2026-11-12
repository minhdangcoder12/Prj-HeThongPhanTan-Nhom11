package com.rideflow.user.adapter.in.web;

import com.rideflow.user.application.AuthService;
import com.rideflow.user.domain.model.Role;
import com.rideflow.user.domain.model.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    // max 72: BCrypt chỉ dùng 72 byte đầu
    record RegisterRequest(@NotBlank @Email String email, @NotNull @Size(min = 8, max = 72) String password,
                           @NotBlank String fullName, @NotNull Role role) {}

    record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    record LoginResponse(String token, UUID userId, Role role, UUID driverId) {}

    private final AuthService auth;

    AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    User register(@Valid @RequestBody RegisterRequest req) {
        try {
            return auth.register(req.email(), req.password(), req.fullName(), req.role());
        } catch (AuthService.DuplicateEmailException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest req) {
        try {
            var r = auth.login(req.email(), req.password());
            return new LoginResponse(r.token(), r.user().id(), r.user().role(), r.user().driverId());
        } catch (AuthService.InvalidCredentialsException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }
}
