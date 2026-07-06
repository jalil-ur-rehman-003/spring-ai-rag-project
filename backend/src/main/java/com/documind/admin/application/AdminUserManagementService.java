package com.documind.admin.application;

import com.documind.auth.domain.User;
import com.documind.auth.domain.UserRole;
import com.documind.auth.infrastructure.UserRepository;
import com.documind.common.error.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Admin-facing user management, always scoped to the calling admin's own
 * organization -- a target user belonging to a different organization is
 * rejected the same way as a nonexistent one (EntityNotFoundException, not
 * AccessDeniedException), so as not to reveal that a user with that id
 * exists in another tenant at all. Every mutator explicitly saves via
 * UserRepository rather than relying on Hibernate dirty-checking, since
 * the target User may be loaded and mutated across what looks like one
 * transactional method but isn't guaranteed to share a persistence context
 * with whatever loaded it originally (see the quota-enforcement bug this
 * exact assumption caused in DocumentUploadService).
 */
@Service
public class AdminUserManagementService {

    private final UserRepository userRepository;

    public AdminUserManagementService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> listUsers(UUID organizationId) {
        return userRepository.findByOrganizationId(organizationId);
    }

    @Transactional
    public void changeRole(UUID organizationId, UUID targetUserId, UserRole newRole) {
        User targetUser = findUserScopedToOrganization(organizationId, targetUserId);
        targetUser.changeRole(newRole);
        userRepository.save(targetUser);
    }

    @Transactional
    public void disableUser(UUID organizationId, UUID targetUserId) {
        User targetUser = findUserScopedToOrganization(organizationId, targetUserId);
        targetUser.disable();
        userRepository.save(targetUser);
    }

    @Transactional
    public void enableUser(UUID organizationId, UUID targetUserId) {
        User targetUser = findUserScopedToOrganization(organizationId, targetUserId);
        targetUser.enable();
        userRepository.save(targetUser);
    }

    private User findUserScopedToOrganization(UUID organizationId, UUID targetUserId) {
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> EntityNotFoundException.forEntity("User", targetUserId));

        if (!targetUser.getOrganization().getId().equals(organizationId)) {
            throw EntityNotFoundException.forEntity("User", targetUserId);
        }

        return targetUser;
    }
}
