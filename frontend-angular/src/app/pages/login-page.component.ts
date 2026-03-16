import { Component, inject } from "@angular/core";
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { Router } from "@angular/router";
import { AuthService } from "../core/services/auth.service";
import { ThemeService } from "../core/services/theme.service";
import { AutoFocusDirective } from "../shared/directives/auto-focus.directive";
import { TrimDirective } from "../shared/directives/trim.directive";

@Component({
  selector: "app-login-page",
  standalone: true,
  imports: [ReactiveFormsModule, AutoFocusDirective, TrimDirective],
  template: `
    <main class="login-page">
      <section class="card">
        <h1>Login</h1>
        <form [formGroup]="form" (ngSubmit)="submit()">
          <label>
            <span>Email</span>
            <input type="email" formControlName="email" appTrim appAutoFocus />
          </label>
          @if (form.controls.email.touched && form.controls.email.hasError("required")) {
            <p class="error">Email é obrigatório.</p>
          }
          @if (form.controls.email.touched && form.controls.email.hasError("email")) {
            <p class="error">Informe um email válido.</p>
          }
          <label>
            <span>Senha</span>
            <input type="password" formControlName="senha" />
          </label>
          @if (form.controls.senha.touched && form.controls.senha.hasError("required")) {
            <p class="error">Senha é obrigatória.</p>
          }
          <button type="submit" [disabled]="form.invalid || loading">Entrar</button>
        </form>
        @if (error) {
          <p class="error">{{ error }}</p>
        }
        <button class="theme" type="button" (click)="themeService.toggleTheme()">Alternar tema</button>
      </section>
    </main>
  `,
  styles: [
    `
      .login-page {
        min-height: 100vh;
        display: grid;
        place-items: center;
        padding: 24px;
      }

      .card {
        width: min(420px, 100%);
        border: 1px solid var(--stroke);
        background: var(--card);
        border-radius: 18px;
        padding: 20px;
        display: grid;
        gap: 14px;
      }

      form {
        display: grid;
        gap: 12px;
      }

      label {
        display: grid;
        gap: 6px;
      }

      input {
        height: 42px;
        border-radius: 10px;
        border: 1px solid var(--stroke);
        background: transparent;
        color: var(--ink);
        padding: 0 10px;
      }

      button {
        height: 42px;
        border: none;
        border-radius: 10px;
        cursor: pointer;
      }

      button[type="submit"] {
        background: linear-gradient(135deg, #f97316, #ea580c);
        color: white;
        font-weight: 600;
      }

      .theme {
        border: 1px solid var(--stroke);
        background: transparent;
        color: var(--ink);
      }

      .error {
        margin: 0;
        color: #dc2626;
      }
    `,
  ],
})
export class LoginPageComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  readonly themeService = inject(ThemeService);

  loading = false;
  error = "";

  readonly form = this.fb.group({
    email: ["", [Validators.required, Validators.email]],
    senha: ["", [Validators.required]],
  });

  submit(): void {
    if (this.form.invalid || this.loading) {
      return;
    }

    this.loading = true;
    this.error = "";

    this.authService.login(this.form.getRawValue()).subscribe({
      next: () => {
        this.loading = false;
        this.router.navigateByUrl("/home");
      },
      error: () => {
        this.loading = false;
        this.error = "Usuário inexistente ou senha inválida.";
      },
    });
  }
}
