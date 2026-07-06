import { Injectable } from '@angular/core';

const ACCESS_TOKEN_STORAGE_KEY = 'documind.accessToken';
const REFRESH_TOKEN_STORAGE_KEY = 'documind.refreshToken';

/**
 * Thin wrapper around localStorage for the two auth tokens. Isolated behind a
 * service so the storage mechanism (localStorage vs. a future httpOnly-cookie
 * approach) can change without touching every call site.
 */
@Injectable({ providedIn: 'root' })
export class TokenStorageService {
  saveTokenPair(accessToken: string, refreshToken: string): void {
    localStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, accessToken);
    localStorage.setItem(REFRESH_TOKEN_STORAGE_KEY, refreshToken);
  }

  readAccessToken(): string | null {
    return localStorage.getItem(ACCESS_TOKEN_STORAGE_KEY);
  }

  readRefreshToken(): string | null {
    return localStorage.getItem(REFRESH_TOKEN_STORAGE_KEY);
  }

  clearTokenPair(): void {
    localStorage.removeItem(ACCESS_TOKEN_STORAGE_KEY);
    localStorage.removeItem(REFRESH_TOKEN_STORAGE_KEY);
  }
}
