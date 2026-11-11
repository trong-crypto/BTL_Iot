package org.example.back_end_iot.api;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.example.back_end_iot.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.example.back_end_iot.security.JwtService;
import java.time.Instant;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.annotation.JsonProperty;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final UserRepository users;
    private final JwtService tokens;

    public AuthController(AuthenticationManager authenticationManager, UserRepository users, JwtService tokens) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.tokens = tokens;
    }

    @PostMapping(value = "/login", consumes = "application/json")
    public LoginResponse login(@Valid @RequestBody LoginRequest body, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        try {
            var authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(body.username(), body.password()));
            var user = users.findByUsername(authentication.getName()).orElseThrow();
            var jwt = tokens.issue(authentication.getName());
            return new LoginResponse(user.getId(), user.getUsername(), user.getFullName(), jwt.getTokenValue(),
                    "Bearer", tokens.ttlSeconds(), jwt.getExpiresAt());
        } catch (AuthenticationException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tên đăng nhập hoặc mật khẩu không đúng");
        }
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) { }
    public record LoginResponse(Integer id, String username, @JsonProperty("full_name") String fullName,
                                String accessToken, String tokenType, long expiresIn, Instant expiresAt) { }
}
