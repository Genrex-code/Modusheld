package com.modushield.gateway.auth;

public record UserAccount(String username, String passwordHash, UserRole role) {
}
