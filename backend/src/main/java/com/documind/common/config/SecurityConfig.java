package com.documind.common.config;

import com.documind.auth.application.JwtService;
import com.documind.auth.infrastructure.JwtAuthenticationFilter;
import com.documind.auth.infrastructure.UserRepository;
import com.documind.common.error.SecurityResponseHandler;
import com.documind.common.tenant.TenantContextFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Stateless JWT-based security: no server-side session, no CSRF token (CSRF
 * protection is only meaningful for cookie-based session auth), every
 * request authenticated fresh from its bearer token. Filter order matters:
 * JwtAuthenticationFilter must run before Spring Security's own
 * UsernamePasswordAuthenticationFilter so the JWT principal is already set
 * before Security's own machinery inspects the context; TenantContextFilter
 * runs immediately after, since it depends on that principal being present.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/**",
            "/actuator/health"
    };

    private final String allowedOrigin;

    public SecurityConfig(@Value("${documind.cors.allowed-origin}") String allowedOrigin) {
        this.allowedOrigin = allowedOrigin;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Registered with Spring Security itself (not just Spring MVC's
     * WebMvcConfigurer) so the CORS filter runs ahead of
     * `.anyRequest().authenticated()` -- otherwise a browser's unauthenticated
     * CORS preflight (OPTIONS, no Authorization header) is rejected with 403
     * before it ever reaches the CORS handling, which silently breaks every
     * authenticated endpoint when called from a real browser (curl bypasses
     * CORS entirely, so this only surfaces when actually driven from the UI).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigin));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration
    ) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity httpSecurity, JwtService jwtService, UserRepository userRepository, ObjectMapper objectMapper
    ) throws Exception {
        SecurityResponseHandler securityResponseHandler = new SecurityResponseHandler(objectMapper);

        httpSecurity
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(securityResponseHandler)
                        .accessDeniedHandler(securityResponseHandler)
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, userRepository), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new TenantContextFilter(), JwtAuthenticationFilter.class);

        return httpSecurity.build();
    }
}
