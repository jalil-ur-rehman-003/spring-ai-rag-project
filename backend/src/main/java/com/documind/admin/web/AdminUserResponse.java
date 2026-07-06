package com.documind.admin.web;

import com.documind.auth.domain.UserRole;
import com.documind.auth.domain.UserStatus;

import java.util.UUID;

public record AdminUserResponse(UUID userId, String email, UserRole role, UserStatus status) {
}
