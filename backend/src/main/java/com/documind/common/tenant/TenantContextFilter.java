package com.documind.common.tenant;

import com.documind.auth.infrastructure.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs after JwtAuthenticationFilter in the security filter chain (see
 * SecurityConfig). Copies the already-authenticated principal's org/user id
 * into TenantContext for the duration of the request, and always clears it
 * afterward -- otherwise a value could leak into a later request reusing the
 * same pooled thread.
 */
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            bindTenantContextFromAuthenticatedPrincipal();
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private void bindTenantContextFromAuthenticatedPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            TenantContext.set(principal.getOrganizationId(), principal.getUserId());
        }
    }
}
