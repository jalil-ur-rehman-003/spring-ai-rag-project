package com.documind.common.tenant;

import java.util.UUID;

/**
 * Holds the current request's organization and user identity so that
 * service/repository code can enforce tenant scoping without threading
 * an organizationId parameter through every call site. Populated by
 * {@link TenantContextFilter} once per request and always cleared
 * afterward — Spring MVC's one-thread-per-request model (preserved even
 * with virtual threads enabled) makes a ThreadLocal safe here.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_ORGANIZATION_ID = new ThreadLocal<>();
    private static final ThreadLocal<UUID> CURRENT_USER_ID = new ThreadLocal<>();

    private TenantContext() {
    }

    static void set(UUID organizationId, UUID userId) {
        CURRENT_ORGANIZATION_ID.set(organizationId);
        CURRENT_USER_ID.set(userId);
    }

    static void clear() {
        CURRENT_ORGANIZATION_ID.remove();
        CURRENT_USER_ID.remove();
    }

    public static UUID currentOrganizationId() {
        UUID organizationId = CURRENT_ORGANIZATION_ID.get();
        if (organizationId == null) {
            throw new IllegalStateException("No organization id bound to the current request context");
        }
        return organizationId;
    }

    public static UUID currentUserId() {
        UUID userId = CURRENT_USER_ID.get();
        if (userId == null) {
            throw new IllegalStateException("No user id bound to the current request context");
        }
        return userId;
    }
}
