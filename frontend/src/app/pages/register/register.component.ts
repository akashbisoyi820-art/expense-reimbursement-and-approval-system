import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-register',
  imports: [FormsModule, RouterLink],
  templateUrl: './register.component.html',
  styleUrl: './register.component.css'
})
export class RegisterComponent {
  name = '';
  email = '';
  password = '';
  confirmPassword = '';

  error = signal('');
  success = signal('');
  loading = signal(false);

  constructor(private authService: AuthService, private router: Router) {}

  onRegister() {
    this.error.set('');
    this.success.set('');

    if (!this.name.trim() || !this.email.trim() || !this.password) {
      this.error.set('All fields are required');
      return;
    }
    if (this.password.length < 6) {
      this.error.set('Password must be at least 6 characters');
      return;
    }
    if (this.password !== this.confirmPassword) {
      this.error.set('Passwords do not match');
      return;
    }

    this.loading.set(true);

    this.authService.register(this.name.trim(), this.email.trim(), this.password).subscribe({
      next: () => {
        this.loading.set(false);
        this.success.set('Account created successfully. Redirecting to login...');
        setTimeout(() => this.router.navigate(['/login']), 1500);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(
          err.status === 0
            ? 'Cannot connect to the server. Please try again later.'
            : 'Registration failed. This email may already be registered.'
        );
      }
    });
  }
}
