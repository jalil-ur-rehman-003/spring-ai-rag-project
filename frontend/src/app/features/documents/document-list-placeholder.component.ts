import { Component, inject } from '@angular/core';
import { AuthService } from '../../core/auth/auth.service';

/**
 * Stand-in landing page for authenticated users until the real
 * document-list feature (Phase 2 of the roadmap) is built. Confirms the
 * auth flow works end-to-end without pretending to be the final feature.
 */
@Component({
  selector: 'documind-document-list-placeholder',
  standalone: true,
  template: `
    <div class="placeholder-page">
      <h1>You're signed in.</h1>
      <p>Organization id: {{ authService.currentOrganizationId() }}</p>
      <p>Role: {{ authService.currentRole() }}</p>
      <button type="button" (click)="authService.logout()">Log out</button>
    </div>
  `,
  styles: `
    .placeholder-page {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
      gap: 0.5rem;
    }
  `,
})
export class DocumentListPlaceholderComponent {
  protected readonly authService = inject(AuthService);
}
