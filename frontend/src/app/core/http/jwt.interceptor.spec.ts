import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AuthService } from '../auth/auth.service';
import { jwtInterceptor } from './jwt.interceptor';

describe('jwtInterceptor', () => {
  let httpClient: HttpClient;
  let httpTestingController: HttpTestingController;
  let authServiceStub: Pick<AuthService, 'readAccessToken' | 'refreshAccessToken' | 'logout'>;

  beforeEach(() => {
    authServiceStub = {
      readAccessToken: () => 'initial-access-token',
      refreshAccessToken: () => of({ accessToken: 'refreshed-access-token', refreshToken: 'refreshed-refresh-token' }),
      logout: () => {},
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([jwtInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authServiceStub },
      ],
    });

    httpClient = TestBed.inject(HttpClient);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('attaches the current access token as a Bearer header on API requests', () => {
    httpClient.get('/api/v1/documents').subscribe();

    const request = httpTestingController.expectOne('/api/v1/documents');
    expect(request.request.headers.get('Authorization')).toBe('Bearer initial-access-token');
    request.flush({});
  });

  it('does not attach a Bearer header on the login endpoint', () => {
    httpClient.post('/api/v1/auth/login', {}).subscribe();

    const request = httpTestingController.expectOne('/api/v1/auth/login');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
  });

  it('retries once with a refreshed token after a 401, and the retry succeeds', () => {
    httpClient.get('/api/v1/documents').subscribe();

    const firstAttempt = httpTestingController.expectOne('/api/v1/documents');
    expect(firstAttempt.request.headers.get('Authorization')).toBe('Bearer initial-access-token');
    firstAttempt.flush({ message: 'expired' }, { status: 401, statusText: 'Unauthorized' });

    const retriedAttempt = httpTestingController.expectOne('/api/v1/documents');
    expect(retriedAttempt.request.headers.get('Authorization')).toBe('Bearer refreshed-access-token');
    retriedAttempt.flush({ ok: true });
  });

  it('logs out if the refresh attempt itself fails after a 401', () => {
    authServiceStub.refreshAccessToken = () => throwError(() => new Error('refresh token expired'));
    const logoutSpy = vi.spyOn(authServiceStub, 'logout');

    httpClient.get('/api/v1/documents').subscribe({ error: () => {} });

    const firstAttempt = httpTestingController.expectOne('/api/v1/documents');
    firstAttempt.flush({ message: 'expired' }, { status: 401, statusText: 'Unauthorized' });

    expect(logoutSpy).toHaveBeenCalled();
  });
});
