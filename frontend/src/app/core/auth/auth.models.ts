// Shapes mirror the backend's auth DTOs (com.documind.auth.web) exactly, so a
// change to one side is a visible contract break rather than a silent mismatch.

export type UserRole = 'ADMIN' | 'EDITOR' | 'VIEWER';

export interface RegisterOrganizationRequest {
  organizationName: string;
  adminEmail: string;
  adminPassword: string;
}

export interface RegisterOrganizationResponse {
  organizationId: string;
  adminUserId: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

export interface AuthTokenResponse {
  accessToken: string;
  refreshToken: string;
}

/** Decoded shape of the access token's JWT payload, used to read claims client-side without a network call. */
export interface AccessTokenClaims {
  sub: string; // user id
  org_id: string;
  role: UserRole;
  exp: number; // epoch seconds
}
