package dev.scarletnexus.identity;

import java.util.UUID;

public record UserAccount(UUID id, String email, String passwordHash, String status) {}
