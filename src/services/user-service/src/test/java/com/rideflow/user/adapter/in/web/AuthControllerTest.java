package com.rideflow.user.adapter.in.web;

import com.rideflow.user.application.AuthService;
import com.rideflow.user.domain.model.Role;
import com.rideflow.user.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private final AuthService auth = mock(AuthService.class);
    private final User user = new User(UUID.randomUUID(), "a@x.com", "A", Role.PASSENGER, null, Instant.now());
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(auth)).build();
    }

    private static String register(String password, String role) {
        return "{\"email\":\"a@x.com\",\"password\":\"" + password + "\",\"fullName\":\"A\",\"role\":\"" + role + "\"}";
    }

    @Test
    void registerReturns201WithoutHash() throws Exception {
        when(auth.register(anyString(), anyString(), anyString(), any())).thenReturn(user);

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(register("secret123", "PASSENGER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("a@x.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void shortPasswordIs400() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(register("short", "PASSENGER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateEmailIs409() throws Exception {
        when(auth.register(anyString(), anyString(), anyString(), any())).thenThrow(new AuthService.DuplicateEmailException());

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(register("secret123", "DRIVER")))
                .andExpect(status().isConflict());
    }

    @Test
    void adminRegistrationIs400() throws Exception {
        when(auth.register(anyString(), anyString(), anyString(), any()))
                .thenThrow(new IllegalArgumentException("ADMIN cannot self-register"));

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(register("secret123", "ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginReturnsTokenUserIdRole() throws Exception {
        when(auth.login("a@x.com", "secret123")).thenReturn(new AuthService.LoginResult("jwt", user));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@x.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt"))
                .andExpect(jsonPath("$.userId").value(user.id().toString()))
                .andExpect(jsonPath("$.role").value("PASSENGER"));
    }

    @Test
    void badCredentialsIs401() throws Exception {
        when(auth.login(anyString(), anyString())).thenThrow(new AuthService.InvalidCredentialsException());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@x.com\",\"password\":\"badbadbad\"}"))
                .andExpect(status().isUnauthorized());
    }
}
