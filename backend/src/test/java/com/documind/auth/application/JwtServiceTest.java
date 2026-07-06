package com.documind.auth.application;

import com.documind.auth.domain.User;
import com.documind.auth.domain.UserRole;
import com.documind.auth.infrastructure.UserPrincipal;
import com.documind.org.domain.Organization;
import com.documind.org.domain.PlanTier;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SIGNING_KEY = "test-signing-key-must-be-at-least-256-bits-long-for-hs384";

    private JwtService jwtService;
    private UserPrincipal adminPrincipal;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties(SIGNING_KEY, 15, 30);
        jwtService = new JwtService(jwtProperties);

        Organization organization = Organization.createNew("Acme Corp", PlanTier.FREE, 1024);
        User adminUser = User.createNew(organization, "admin@acme.test", "irrelevant-hash", UserRole.ADMIN);
        adminPrincipal = new UserPrincipal(adminUser);
    }

    @Test
    void issuedTokenContainsExpectedClaims() {
        String accessToken = jwtService.generateAccessToken(adminPrincipal);

        JwtService.AccessTokenClaims claims = jwtService.parseAndValidateAccessToken(accessToken);

        assertThat(claims.userId()).isEqualTo(adminPrincipal.getUserId());
        assertThat(claims.organizationId()).isEqualTo(adminPrincipal.getOrganizationId());
        assertThat(claims.role()).isEqualTo("ADMIN");
    }

    @Test
    void rejectsTokenSignedWithADifferentKey() {
        JwtProperties differentKeyProperties = new JwtProperties(
                "a-completely-different-signing-key-also-256-bits-long-enough", 15, 30
        );
        JwtService differentKeyService = new JwtService(differentKeyProperties);
        String tokenSignedWithDifferentKey = differentKeyService.generateAccessToken(adminPrincipal);

        assertThatThrownBy(() -> jwtService.parseAndValidateAccessToken(tokenSignedWithDifferentKey))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void rejectsMalformedToken() {
        assertThatThrownBy(() -> jwtService.parseAndValidateAccessToken("not-a-real-jwt"))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }
}
