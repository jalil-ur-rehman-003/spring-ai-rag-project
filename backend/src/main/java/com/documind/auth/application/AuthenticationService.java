package com.documind.auth.application;

import com.documind.auth.domain.User;
import com.documind.auth.domain.UserRole;
import com.documind.auth.infrastructure.UserPrincipal;
import com.documind.auth.infrastructure.UserRepository;
import com.documind.common.error.AuthenticationFailedException;
import com.documind.org.domain.Organization;
import com.documind.org.application.OrganizationService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the four auth use cases exposed by AuthController. Registration
 * is the one place a new tenant (Organization) comes into existence, always
 * paired with that tenant's first ADMIN user in a single transaction so an
 * org can never exist without at least one admin able to manage it.
 */
@Service
public class AuthenticationService {

    private final OrganizationService organizationService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthenticationService(
            OrganizationService organizationService,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            RefreshTokenService refreshTokenService
    ) {
        this.organizationService = organizationService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public User registerOrganizationWithAdmin(String organizationName, String adminEmail, String adminPassword) {
        if (userRepository.existsByEmail(adminEmail)) {
            throw new IllegalArgumentException("Email is already registered: " + adminEmail);
        }

        Organization organization = organizationService.createOrganizationOnFreeTier(organizationName);
        String passwordHash = passwordEncoder.encode(adminPassword);
        User adminUser = User.createNew(organization, adminEmail, passwordHash, UserRole.ADMIN);
        return userRepository.save(adminUser);
    }

    @Transactional
    public TokenPair login(String email, String password) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password)
        );
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return issueTokenPairFor(principal.getUser());
    }

    // @Transactional is required here, not just a convenience: RefreshToken.user is a lazy
    // association, and issueTokenPairFor() below both reads it (user.isActive(), user.getRole())
    // and persists a new RefreshToken row -- all of that must happen inside one Hibernate
    // session, or the lazy proxy fails to initialize once the original session that loaded it closes.
    @Transactional
    public TokenPair refresh(String rawRefreshToken) {
        User user = refreshTokenService.validateAndGetOwner(rawRefreshToken);
        if (!user.isActive()) {
            throw new AuthenticationFailedException("User account is disabled");
        }
        // Rotate: revoke the presented refresh token and issue a fresh pair, limiting how long a stolen token stays valid.
        refreshTokenService.revoke(rawRefreshToken);
        return issueTokenPairFor(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    private TokenPair issueTokenPairFor(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        String accessToken = jwtService.generateAccessToken(principal);
        String refreshToken = refreshTokenService.issueRawTokenFor(user);
        return new TokenPair(accessToken, refreshToken);
    }

    public record TokenPair(String accessToken, String refreshToken) {
    }
}
