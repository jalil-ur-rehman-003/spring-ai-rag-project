package com.documind.admin.web;

import com.documind.admin.application.AdminUserManagementService;
import com.documind.admin.application.OrganizationUsageSummary;
import com.documind.admin.application.UsageAnalyticsService;
import com.documind.auth.domain.User;
import com.documind.auth.infrastructure.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Every endpoint here is admin-only and always scoped to the caller's own
 * organization -- there is no cross-org admin capability, matching the
 * multi-tenant model where an ADMIN manages their own org, not the platform.
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UsageAnalyticsService usageAnalyticsService;
    private final AdminUserManagementService adminUserManagementService;

    public AdminController(UsageAnalyticsService usageAnalyticsService, AdminUserManagementService adminUserManagementService) {
        this.usageAnalyticsService = usageAnalyticsService;
        this.adminUserManagementService = adminUserManagementService;
    }

    @GetMapping("/usage")
    public OrganizationUsageResponse getUsage(@AuthenticationPrincipal UserPrincipal principal) {
        OrganizationUsageSummary summary = usageAnalyticsService.summarizeUsage(principal.getOrganizationId());
        return new OrganizationUsageResponse(
                summary.totalDocuments(), summary.readyDocuments(), summary.failedDocuments(),
                summary.totalChatSessions(), summary.totalChatMessages(),
                summary.storageUsedBytes(), summary.storageQuotaBytes()
        );
    }

    @GetMapping("/users")
    public List<AdminUserResponse> listUsers(@AuthenticationPrincipal UserPrincipal principal) {
        return adminUserManagementService.listUsers(principal.getOrganizationId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping("/users/{userId}/role")
    public void changeUserRole(
            @PathVariable UUID userId, @Valid @RequestBody ChangeUserRoleRequest request, @AuthenticationPrincipal UserPrincipal principal
    ) {
        adminUserManagementService.changeRole(principal.getOrganizationId(), userId, request.role());
    }

    @PostMapping("/users/{userId}/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disableUser(@PathVariable UUID userId, @AuthenticationPrincipal UserPrincipal principal) {
        adminUserManagementService.disableUser(principal.getOrganizationId(), userId);
    }

    @PostMapping("/users/{userId}/enable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enableUser(@PathVariable UUID userId, @AuthenticationPrincipal UserPrincipal principal) {
        adminUserManagementService.enableUser(principal.getOrganizationId(), userId);
    }

    private AdminUserResponse toResponse(User user) {
        return new AdminUserResponse(user.getId(), user.getEmail(), user.getRole(), user.getStatus());
    }
}
