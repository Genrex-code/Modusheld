package com.modushield.gateway.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserService {

    private final Map<String, UserAccount> users = new ConcurrentHashMap<>();
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(AuthProperties properties) {
        String adminUsername = requireConfigured(properties.getAdminUsername(), "ADMIN_USERNAME");
        String adminPassword = requireConfigured(properties.getAdminPassword(), "ADMIN_PASSWORD");
        validateUsername(adminUsername);
        validatePassword(adminPassword);
        users.put(adminUsername, new UserAccount(
                adminUsername,
                passwordEncoder.encode(adminPassword),
                UserRole.ADMIN));
    }

    public UserAccount register(String username, String password) {
        validateUsername(username);
        validatePassword(password);

        UserAccount account = new UserAccount(
                username,
                passwordEncoder.encode(password),
                UserRole.USER);
        if (users.putIfAbsent(username, account) != null) {
            throw new UsernameAlreadyExistsException();
        }
        return account;
    }

    public Optional<UserAccount> authenticate(String username, String password) {
        if (username == null || password == null) {
            return Optional.empty();
        }
        UserAccount account = users.get(username);
        if (account == null || !passwordEncoder.matches(password, account.passwordHash())) {
            return Optional.empty();
        }
        return Optional.of(account);
    }

    private String requireConfigured(String value, String environmentVariable) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(environmentVariable + " must be configured");
        }
        return value;
    }

    private void validateUsername(String username) {
        if (username == null || !username.matches("[A-Za-z0-9._-]{3,64}")) {
            throw new InvalidCredentialsException(
                    "Username must be 3-64 characters and contain only letters, numbers, '.', '_' or '-'");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw new InvalidCredentialsException("Password must be between 8 and 128 characters");
        }
    }

    public static class UsernameAlreadyExistsException extends RuntimeException {
    }

    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException(String message) {
            super(message);
        }
    }
}
