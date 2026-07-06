package com.documind.admin.web;

import com.documind.auth.domain.UserRole;
import jakarta.validation.constraints.NotNull;

public record ChangeUserRoleRequest(@NotNull(message = "Role is required") UserRole role) {
}
