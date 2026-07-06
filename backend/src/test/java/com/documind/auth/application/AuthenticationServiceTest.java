package com.documind.auth.application;

import com.documind.auth.domain.User;
import com.documind.auth.domain.UserRole;
import com.documind.auth.infrastructure.UserPrincipal;
import com.documind.auth.infrastructure.RefreshTokenRepository;
import com.documind.auth.infrastructure.UserRepository;
import com.documind.common.error.AuthenticationFailedException;
import com.documind.org.application.OrganizationService;
import com.documind.org.domain.Organization;
import com.documind.org.domain.PlanTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-level coverage of the auth use-case orchestration, with all
 * persistence mocked. The Postgres-backed happy path (including the
 * LazyInitializationException regression this project already hit once) is
 * covered separately by AuthControllerIntegrationTest, since a mocked
 * repository can't reproduce a real Hibernate session boundary.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private OrganizationService organizationService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private RefreshTokenService refreshTokenService;
    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        jwtService = new JwtService(new JwtProperties("test-signing-key-must-be-at-least-256-bits-long-for-hs384", 15, 30));
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, 30);

        authenticationService = new AuthenticationService(
                organizationService, userRepository, passwordEncoder, authenticationManager, jwtService, refreshTokenService
        );
    }

    @Test
    void registeringANewOrganizationCreatesItsFirstUserAsAdmin() {
        when(userRepository.existsByEmail("admin@acme.test")).thenReturn(false);
        Organization organization = Organization.createNew("Acme Corp", PlanTier.FREE, 1024);
        when(organizationService.createOrganizationOnFreeTier("Acme Corp")).thenReturn(organization);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User createdAdmin = authenticationService.registerOrganizationWithAdmin("Acme Corp", "admin@acme.test", "SuperSecurePassword123");

        assertThat(createdAdmin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(createdAdmin.getOrganization()).isEqualTo(organization);
        assertThat(passwordEncoder.matches("SuperSecurePassword123", createdAdmin.getPasswordHash())).isTrue();
    }

    @Test
    void registeringWithAnAlreadyUsedEmailIsRejected() {
        when(userRepository.existsByEmail("admin@acme.test")).thenReturn(true);

        assertThatThrownBy(() ->
                authenticationService.registerOrganizationWithAdmin("Acme Corp", "admin@acme.test", "SuperSecurePassword123")
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loginIssuesAnAccessAndRefreshTokenPairForTheAuthenticatedUser() {
        Organization organization = Organization.createNew("Acme Corp", PlanTier.FREE, 1024);
        User adminUser = User.createNew(organization, "admin@acme.test", "irrelevant-hash", UserRole.ADMIN);
        UserPrincipal principal = new UserPrincipal(adminUser);

        when(authenticationManager.authenticate(any()))
                .thenReturn(new TestingAuthenticationToken(principal, null, principal.getAuthorities()));

        AuthenticationService.TokenPair tokenPair = authenticationService.login("admin@acme.test", "whatever-the-real-password-is");

        assertThat(tokenPair.accessToken()).isNotBlank();
        assertThat(tokenPair.refreshToken()).isNotBlank();
        verify(refreshTokenRepository).save(any());
    }

    @Test
    void loginWithBadCredentialsPropagatesTheAuthenticationFailure() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authenticationService.login("admin@acme.test", "wrong-password"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void refreshingWithAnExpiredTokenIsRejected() {
        Organization organization = Organization.createNew("Acme Corp", PlanTier.FREE, 1024);
        User adminUser = User.createNew(organization, "admin@acme.test", "irrelevant-hash", UserRole.ADMIN);

        var expiredRefreshToken = com.documind.auth.domain.RefreshToken.issueFor(
                adminUser, "irrelevant-hash-value", java.time.Instant.now().minusSeconds(1)
        );
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(java.util.Optional.of(expiredRefreshToken));

        assertThatThrownBy(() -> authenticationService.refresh("some-raw-refresh-token"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("expired");
    }
}
