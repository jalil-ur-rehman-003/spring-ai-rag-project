import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'documind-register',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss',
})
export class RegisterComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly isSubmitting = signal(false);
  readonly submissionError = signal<string | null>(null);

  readonly registerForm = this.formBuilder.nonNullable.group({
    organizationName: ['', [Validators.required]],
    adminEmail: ['', [Validators.required, Validators.email]],
    adminPassword: ['', [Validators.required, Validators.minLength(12)]],
  });

  submitRegistration(): void {
    if (this.registerForm.invalid) {
      this.registerForm.markAllAsTouched();
      return;
    }

    this.isSubmitting.set(true);
    this.submissionError.set(null);

    const { organizationName, adminEmail, adminPassword } = this.registerForm.getRawValue();

    this.authService.registerOrganization({ organizationName, adminEmail, adminPassword }).subscribe({
      next: () => this.logInImmediatelyAfterRegistration(adminEmail, adminPassword),
      error: () => {
        this.isSubmitting.set(false);
        this.submissionError.set('Could not register organization. The email may already be in use.');
      },
    });
  }

  private logInImmediatelyAfterRegistration(email: string, password: string): void {
    this.authService.login({ email, password }).subscribe({
      next: () => this.router.navigateByUrl('/documents'),
      error: () => {
        this.isSubmitting.set(false);
        this.submissionError.set('Organization created, but automatic sign-in failed. Please sign in manually.');
      },
    });
  }
}
