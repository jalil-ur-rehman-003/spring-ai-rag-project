package com.documind.auth.web;

import com.documind.auth.application.AuthenticationService;
import com.documind.auth.domain.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Unauthenticated endpoints for establishing and refreshing a session; permitted without a JWT (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterOrganizationResponse register(@Valid @RequestBody RegisterOrganizationRequest request) {
        User adminUser = authenticationService.registerOrganizationWithAdmin(
                request.organizationName(), request.adminEmail(), request.adminPassword()
        );
        return new RegisterOrganizationResponse(adminUser.getOrganization().getId(), adminUser.getId());
    }

    @PostMapping("/login")
    public AuthTokenResponse login(@Valid @RequestBody LoginRequest request) {
        AuthenticationService.TokenPair tokenPair = authenticationService.login(request.email(), request.password());
        return new AuthTokenResponse(tokenPair.accessToken(), tokenPair.refreshToken());
    }

    @PostMapping("/refresh")
    public AuthTokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthenticationService.TokenPair tokenPair = authenticationService.refresh(request.refreshToken());
        return new AuthTokenResponse(tokenPair.accessToken(), tokenPair.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authenticationService.logout(request.refreshToken());
    }
}
