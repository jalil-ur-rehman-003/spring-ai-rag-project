package com.documind.admin.application;

import com.documind.auth.domain.User;
import com.documind.auth.domain.UserRole;
import com.documind.auth.infrastructure.UserRepository;
import com.documind.common.error.EntityNotFoundException;
import com.documind.org.domain.Organization;
import com.documind.org.domain.PlanTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserManagementServiceTest {

    @Mock
    private UserRepository userRepository;

    private AdminUserManagementService adminUserManagementService;
    private Organization organization;
    private User targetUser;

    @BeforeEach
    void setUp() {
        adminUserManagementService = new AdminUserManagementService(userRepository);
        organization = Organization.createNew("Acme Corp", PlanTier.FREE, 1024);
        targetUser = User.createNew(organization, "member@acme.test", "irrelevant-hash", UserRole.VIEWER);
    }

    @Test
    void listsUsersBelongingToTheOrganization() {
        when(userRepository.findByOrganizationId(organization.getId())).thenReturn(List.of(targetUser));

        List<User> users = adminUserManagementService.listUsers(organization.getId());

        assertThat(users).containsExactly(targetUser);
    }

    @Test
    void changesAUsersRoleAndPersistsTheChange() {
        when(userRepository.findById(targetUser.getId())).thenReturn(Optional.of(targetUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        adminUserManagementService.changeRole(organization.getId(), targetUser.getId(), UserRole.EDITOR);

        assertThat(targetUser.getRole()).isEqualTo(UserRole.EDITOR);
        verify(userRepository).save(targetUser);
    }

    @Test
    void rejectsChangingTheRoleOfAUserFromAnotherOrganization() {
        Organization otherOrganization = Organization.createNew("Other Org", PlanTier.FREE, 1024);
        User otherOrgUser = User.createNew(otherOrganization, "other@other.test", "irrelevant-hash", UserRole.VIEWER);
        when(userRepository.findById(otherOrgUser.getId())).thenReturn(Optional.of(otherOrgUser));

        assertThatThrownBy(() -> adminUserManagementService.changeRole(organization.getId(), otherOrgUser.getId(), UserRole.ADMIN))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void disablesAUserAndPersistsTheChange() {
        when(userRepository.findById(targetUser.getId())).thenReturn(Optional.of(targetUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        adminUserManagementService.disableUser(organization.getId(), targetUser.getId());

        assertThat(targetUser.isActive()).isFalse();
        verify(userRepository).save(targetUser);
    }

    @Test
    void rejectsOperatingOnANonexistentUser() {
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserManagementService.disableUser(organization.getId(), UUID.randomUUID()))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
