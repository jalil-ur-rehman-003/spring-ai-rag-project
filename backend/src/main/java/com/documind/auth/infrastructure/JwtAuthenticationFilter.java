package com.documind.auth.infrastructure;

import com.documind.auth.application.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Authenticates requests bearing "Authorization: Bearer <accessToken>". The
 * user is reloaded from the database by id (rather than trusting the JWT's
 * role claim alone) so a role change or account disable takes effect
 * immediately on the next request instead of only after the short-lived
 * access token expires -- a deliberate freshness-over-fewer-DB-hits tradeoff.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        extractBearerToken(request).ifPresent(token -> authenticateFromToken(token, request));
        filterChain.doFilter(request, response);
    }

    private Optional<String> extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return Optional.of(header.substring(BEARER_PREFIX.length()));
        }
        return Optional.empty();
    }

    private void authenticateFromToken(String token, HttpServletRequest request) {
        try {
            JwtService.AccessTokenClaims claims = jwtService.parseAndValidateAccessToken(token);
            userRepository.findById(claims.userId())
                    .filter(user -> user.isActive())
                    .ifPresent(user -> {
                        UserPrincipal principal = new UserPrincipal(user);
                        var authenticationToken = new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities()
                        );
                        authenticationToken.setDetails(request);
                        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                    });
        } catch (JwtException | IllegalArgumentException exception) {
            // Leave the security context unauthenticated; downstream authorization will reject the request with 401/403.
            SecurityContextHolder.clearContext();
        }
    }
}
