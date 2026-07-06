import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';

const AUTH_ENDPOINTS_EXEMPT_FROM_BEARER_TOKEN = ['/auth/login', '/auth/register', '/auth/refresh'];

/**
 * Attaches the current access token as a Bearer header to every outgoing API
 * request except the auth endpoints that must work without one. On a 401
 * (expired/invalid access token), attempts exactly one silent refresh-and-
 * retry before giving up and forcing a logout -- avoids infinite retry loops
 * if the refresh token itself has also expired or been revoked.
 */
export const jwtInterceptor: HttpInterceptorFn = (request, next) => {
  const authService = inject(AuthService);

  if (isAuthEndpointExemptFromBearerToken(request.url)) {
    return next(request);
  }

  const accessToken = authService.readAccessToken();
  const authorizedRequest = accessToken
    ? request.clone({ setHeaders: { Authorization: `Bearer ${accessToken}` } })
    : request;

  return next(authorizedRequest).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401) {
        return authService.refreshAccessToken().pipe(
          switchMap((refreshedTokens) =>
            next(request.clone({ setHeaders: { Authorization: `Bearer ${refreshedTokens.accessToken}` } }))
          ),
          catchError((refreshError: unknown) => {
            authService.logout();
            return throwError(() => refreshError);
          })
        );
      }
      return throwError(() => error);
    })
  );
};

function isAuthEndpointExemptFromBearerToken(requestUrl: string): boolean {
  return AUTH_ENDPOINTS_EXEMPT_FROM_BEARER_TOKEN.some((exemptPath) => requestUrl.includes(exemptPath));
}
