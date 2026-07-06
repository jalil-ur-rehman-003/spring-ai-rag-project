import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AccessTokenClaims,
  AuthTokenResponse,
  LoginRequest,
  RefreshTokenRequest,
  RegisterOrganizationRequest,
  RegisterOrganizationResponse,
  UserRole,
} from './auth.models';
import { TokenStorageService } from './token-storage.service';

/**
 * Owns the client-side auth state as signals so components can react to
 * login/logout without manually subscribing. Decodes the access token's
 * claims locally (no network round-trip) purely to drive UI state (current
 * role, current org) -- the backend is still the source of truth and
 * re-validates the token on every request.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly httpClient = inject(HttpClient);
  private readonly tokenStorageService = inject(TokenStorageService);
  private readonly router = inject(Router);

  private readonly accessTokenClaimsSignal = signal<AccessTokenClaims | null>(this.decodeStoredAccessToken());

  readonly isAuthenticated = computed(() => this.accessTokenClaimsSignal() !== null);
  readonly currentUserId = computed(() => this.accessTokenClaimsSignal()?.sub ?? null);
  readonly currentOrganizationId = computed(() => this.accessTokenClaimsSignal()?.org_id ?? null);
  readonly currentRole = computed<UserRole | null>(() => this.accessTokenClaimsSignal()?.role ?? null);

  registerOrganization(request: RegisterOrganizationRequest): Observable<RegisterOrganizationResponse> {
    return this.httpClient.post<RegisterOrganizationResponse>(`${environment.apiBaseUrl}/auth/register`, request);
  }

  login(request: LoginRequest): Observable<AuthTokenResponse> {
    return this.httpClient.post<AuthTokenResponse>(`${environment.apiBaseUrl}/auth/login`, request).pipe(
      tap((tokenResponse) => this.applyTokenResponse(tokenResponse))
    );
  }

  refreshAccessToken(): Observable<AuthTokenResponse> {
    const refreshRequest: RefreshTokenRequest = { refreshToken: this.requireRefreshToken() };
    return this.httpClient.post<AuthTokenResponse>(`${environment.apiBaseUrl}/auth/refresh`, refreshRequest).pipe(
      tap((tokenResponse) => this.applyTokenResponse(tokenResponse))
    );
  }

  logout(): void {
    const refreshToken = this.tokenStorageService.readRefreshToken();
    if (refreshToken) {
      this.httpClient.post(`${environment.apiBaseUrl}/auth/logout`, { refreshToken } satisfies RefreshTokenRequest).subscribe();
    }
    this.tokenStorageService.clearTokenPair();
    this.accessTokenClaimsSignal.set(null);
    this.router.navigateByUrl('/login');
  }

  readAccessToken(): string | null {
    return this.tokenStorageService.readAccessToken();
  }

  private requireRefreshToken(): string {
    const refreshToken = this.tokenStorageService.readRefreshToken();
    if (!refreshToken) {
      throw new Error('No refresh token is available to refresh the session');
    }
    return refreshToken;
  }

  private applyTokenResponse(tokenResponse: AuthTokenResponse): void {
    this.tokenStorageService.saveTokenPair(tokenResponse.accessToken, tokenResponse.refreshToken);
    this.accessTokenClaimsSignal.set(this.decodeAccessToken(tokenResponse.accessToken));
  }

  private decodeStoredAccessToken(): AccessTokenClaims | null {
    const storedAccessToken = this.tokenStorageService.readAccessToken();
    return storedAccessToken ? this.decodeAccessToken(storedAccessToken) : null;
  }

  private decodeAccessToken(accessToken: string): AccessTokenClaims | null {
    try {
      const payloadSegment = accessToken.split('.')[1];
      const decodedPayload = atob(payloadSegment.replace(/-/g, '+').replace(/_/g, '/'));
      return JSON.parse(decodedPayload) as AccessTokenClaims;
    } catch {
      return null;
    }
  }
}
