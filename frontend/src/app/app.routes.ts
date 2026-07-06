import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login.component').then((module) => module.LoginComponent),
  },
  {
    path: 'register',
    loadComponent: () =>
      import('./features/auth/register/register.component').then((module) => module.RegisterComponent),
  },
  {
    // Placeholder landing page for authenticated users until the document-list feature (Phase 2) exists.
    path: 'documents',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/documents/document-list-placeholder.component').then(
        (module) => module.DocumentListPlaceholderComponent
      ),
  },
];
