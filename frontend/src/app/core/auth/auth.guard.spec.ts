import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, UrlTree } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from './auth.service';

describe('authGuard', () => {
  const isAuthenticatedSignal = signal(false);
  const authServiceStub: Pick<AuthService, 'isAuthenticated'> = { isAuthenticated: isAuthenticatedSignal };

  function runGuard(): boolean | UrlTree {
    return TestBed.runInInjectionContext(() => authGuard({} as never, {} as never)) as boolean | UrlTree;
  }

  beforeEach(() => {
    isAuthenticatedSignal.set(false);

    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: authServiceStub }],
    });
  });

  it('allows navigation when the user is authenticated', () => {
    isAuthenticatedSignal.set(true);

    expect(runGuard()).toBe(true);
  });

  it('redirects to /login when the user is not authenticated', () => {
    isAuthenticatedSignal.set(false);
    const router = TestBed.inject(Router);

    const result = runGuard();

    expect(result).toEqual(router.parseUrl('/login'));
  });
});
