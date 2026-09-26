package com.modushield.gateway.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserServiceTest {

    @Test
    void configuresAdminAndStoresOnlyPasswordHash() {
        UserService service = service();

        UserAccount admin = service.authenticate("configured-admin", "admin-password-123")
                .orElseThrow();

        assertThat(admin.role()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.passwordHash()).isNotEqualTo("admin-password-123");
        assertThat(service.authenticate("configured-admin", "wrong-password")).isEmpty();
    }

    @Test
    void registersUserAndRejectsDuplicateUsername() {
        UserService service = service();

        UserAccount account = service.register("reader", "reader-password-123");

        assertThat(account.role()).isEqualTo(UserRole.USER);
        assertThat(service.authenticate("reader", "reader-password-123")).contains(account);
        assertThatThrownBy(() -> service.register("reader", "another-password"))
                .isInstanceOf(UserService.UsernameAlreadyExistsException.class);
    }

    @Test
    void authenticationRejectsMissingCredentialsAndUnknownUsers() {
        UserService service = service();

        assertThat(service.authenticate(null, "password")).isEmpty();
        assertThat(service.authenticate("reader", null)).isEmpty();
        assertThat(service.authenticate("unknown", "password")).isEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "ab", "contains space", "invalid!"})
    void rejectsInvalidUsernames(String username) {
        assertThatThrownBy(() -> service().register(username, "valid-password"))
                .isInstanceOf(UserService.InvalidCredentialsException.class)
                .hasMessageContaining("Username");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "short"})
    void rejectsInvalidPasswords(String password) {
        assertThatThrownBy(() -> service().register("reader", password))
                .isInstanceOf(UserService.InvalidCredentialsException.class)
                .hasMessageContaining("Password");
    }

    @Test
    void refusesToStartWithoutConfiguredAdminCredentials() {
        AuthProperties missingUsername = properties(null, "admin-password-123");
        AuthProperties missingPassword = properties("configured-admin", " ");

        assertThatThrownBy(() -> new UserService(missingUsername))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_USERNAME");
        assertThatThrownBy(() -> new UserService(missingPassword))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_PASSWORD");
    }

    private UserService service() {
        return new UserService(properties("configured-admin", "admin-password-123"));
    }

    private AuthProperties properties(String username, String password) {
        AuthProperties properties = new AuthProperties();
        properties.setAdminUsername(username);
        properties.setAdminPassword(password);
        return properties;
    }
}
