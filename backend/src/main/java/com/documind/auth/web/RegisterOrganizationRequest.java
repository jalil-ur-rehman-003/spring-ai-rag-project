package com.documind.auth.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Registers a brand-new tenant: creates the Organization and its first ADMIN user in one transaction. */
public record RegisterOrganizationRequest(
        @NotBlank(message = "Organization name is required") String organizationName,
        @NotBlank(message = "Admin email is required") @Email(message = "Admin email must be a valid email address") String adminEmail,
        @NotBlank(message = "Admin password is required") @Size(min = 12, message = "Admin password must be at least 12 characters") String adminPassword
) {
}
