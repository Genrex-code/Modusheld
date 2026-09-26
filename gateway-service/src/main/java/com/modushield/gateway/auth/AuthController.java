package com.modushield.gateway.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;

    public AuthController(UserService userService, JwtService jwtService) {
        this.userService = userService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@RequestBody Credentials credentials) {
        if (credentials == null) {
            throw new UserService.InvalidCredentialsException("Username and password are required");
        }
        UserAccount account = userService.register(credentials.username(), credentials.password());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new UserResponse(account.username(), account.role()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Credentials credentials) {
        if (credentials == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(error("INVALID_CREDENTIALS", "Invalid username or password"));
        }
        return userService.authenticate(credentials.username(), credentials.password())
                .<ResponseEntity<?>>map(account -> {
                    JwtService.Token token = jwtService.createToken(account);
                    return ResponseEntity.ok(new LoginResponse(
                            token.value(),
                            "Bearer",
                            account.username(),
                            account.role(),
                            token.expiresAt()));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(error("INVALID_CREDENTIALS", "Invalid username or password")));
    }

    @ExceptionHandler(UserService.UsernameAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> usernameExists() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(error("USERNAME_EXISTS", "Username is already registered"));
    }

    @ExceptionHandler(UserService.InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> invalidRegistration(RuntimeException exception) {
        return ResponseEntity.badRequest()
                .body(error("INVALID_REGISTRATION", exception.getMessage()));
    }

    private Map<String, Object> error(String code, String message) {
        return Map.of(
                "timestamp", Instant.now().toString(),
                "status", code.equals("INVALID_CREDENTIALS") ? 401
                        : code.equals("USERNAME_EXISTS") ? 409 : 400,
                "error", code,
                "message", message);
    }

    public record Credentials(String username, String password) {
    }

    public record UserResponse(String username, UserRole role) {
    }

    public record LoginResponse(
            String token,
            String tokenType,
            String username,
            UserRole role,
            Instant expiresAt
    ) {
    }
}
