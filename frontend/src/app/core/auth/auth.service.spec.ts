import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';
import { environment } from '../../../environments/environment';
import { AuthTokenResponse } from './auth.models';
import { AuthService } from './auth.service';
import { TokenStorageService } from './token-storage.service';

@Component({ selector: 'documind-test-login-stub', standalone: true, template: '' })
class LoginStubComponent {}

/** Builds a syntactically valid (unsigned) JWT carrying the given payload, matching the shape the backend issues. */
function buildFakeAccessToken(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: 'none', typ: 'JWT' }));
  const body = btoa(JSON.stringify(payload));
  return `${header}.${body}.fake-signature`;
}

describe('AuthService', () => {
  let authService: AuthService;
  let httpTestingController: HttpTestingController;
  let tokenStorageService: TokenStorageService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'login', component: LoginStubComponent }]),
      ],
    });

    tokenStorageService = TestBed.inject(TokenStorageService);
    tokenStorageService.clearTokenPair();

    authService = TestBed.inject(AuthService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('starts unauthenticated when no token is stored', () => {
    expect(authService.isAuthenticated()).toBe(false);
    expect(authService.currentRole()).toBeNull();
  });

  it('becomes authenticated and exposes decoded claims after a successful login', () => {
    const fakeAccessToken = buildFakeAccessToken({
      sub: 'user-123',
      org_id: 'org-456',
      role: 'ADMIN',
      exp: Math.floor(Date.now() / 1000) + 900,
    });
    const tokenResponse: AuthTokenResponse = { accessToken: fakeAccessToken, refreshToken: 'refresh-abc' };

    authService.login({ email: 'admin@acme.test', password: 'SuperSecurePassword123' }).subscribe();

    const request = httpTestingController.expectOne(`${environment.apiBaseUrl}/auth/login`);
    expect(request.request.method).toBe('POST');
    request.flush(tokenResponse);

    expect(authService.isAuthenticated()).toBe(true);
    expect(authService.currentUserId()).toBe('user-123');
    expect(authService.currentOrganizationId()).toBe('org-456');
    expect(authService.currentRole()).toBe('ADMIN');
    expect(tokenStorageService.readAccessToken()).toBe(fakeAccessToken);
  });

  it('clears stored tokens and navigates to /login on logout', () => {
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigateByUrl');
    tokenStorageService.saveTokenPair('some-access-token', 'some-refresh-token');

    authService.logout();

    const request = httpTestingController.expectOne(`${environment.apiBaseUrl}/auth/logout`);
    request.flush(null);

    expect(tokenStorageService.readAccessToken()).toBeNull();
    expect(tokenStorageService.readRefreshToken()).toBeNull();
    expect(authService.isAuthenticated()).toBe(false);
    expect(navigateSpy).toHaveBeenCalledWith('/login');
  });

  it('does not call the logout endpoint when there is no refresh token to revoke', () => {
    authService.logout();

    httpTestingController.expectNone(`${environment.apiBaseUrl}/auth/logout`);
  });
});
