import { Component, inject } from "@angular/core";
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { UsuarioService } from "../core/services/usuario.service";
import { AutoFocusDirective } from "../shared/directives/auto-focus.directive";
import { TrimDirective } from "../shared/directives/trim.directive";
import { UsuarioCreateRequest } from "../core/models/usuario-create-request";

@Component({
  selector: "app-criar-usuario-page",
  standalone: true,
  imports: [ReactiveFormsModule, AutoFocusDirective, TrimDirective],
  template: `
    <section class="card">
      <h1>Criar Usuário</h1>
      <form [formGroup]="form" (ngSubmit)="submit()">
        <label>
          <span>Nome</span>
          <input type="text" formControlName="nome" appTrim appAutoFocus />
        </label>
        @if (form.controls.nome.touched && form.controls.nome.hasError("required")) {
          <p class="error">Nome é obrigatório.</p>
        }

        <label>
          <span>Email</span>
          <input type="email" formControlName="email" appTrim />
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
        @if (form.controls.senha.touched && form.controls.senha.hasError("minlength")) {
          <p class="error">Senha deve ter no mínimo 6 caracteres.</p>
        }

        <label>
          <span>Role</span>
          <select formControlName="role">
            @for (role of roles; track role) {
              <option [value]="role">{{ role }}</option>
            }
          </select>
        </label>

        <button type="submit" [disabled]="form.invalid || loading">Criar usuário</button>
      </form>
    </section>

    @if (feedbackVisible) {
      <div class="feedback-backdrop" (click)="closeFeedback()">
        <article class="feedback-card" [class.error-card]="feedbackType === 'error'" (click)="$event.stopPropagation()">
          <div class="icon">{{ feedbackType === "success" ? "✓" : "✕" }}</div>
          <h2>{{ feedbackType === "success" ? "Usuário criado" : "Erro ao criar usuário" }}</h2>
          @if (feedbackMessage) {
            <p>{{ feedbackMessage }}</p>
          }
          <button type="button" (click)="closeFeedback()">Ok</button>
        </article>
      </div>
    }
  `,
  styles: [
    `
      .card {
        border: 1px solid var(--stroke);
        background: var(--card);
        border-radius: 18px;
        padding: 20px;
      }

      form {
        display: grid;
        gap: 12px;
        margin-top: 12px;
      }

      label {
        display: grid;
        gap: 6px;
      }

      input,
      select {
        height: 42px;
        border-radius: 10px;
        border: 1px solid var(--stroke);
        background: transparent;
        color: var(--ink);
        padding: 0 10px;
      }

      button[type="submit"] {
        height: 44px;
        border: none;
        border-radius: 12px;
        color: white;
        background: linear-gradient(135deg, #f97316, #ea580c);
        font-weight: 600;
        cursor: pointer;
      }

      .error {
        margin: -6px 0 0;
        color: #dc2626;
        font-size: 0.9rem;
      }

      .feedback-backdrop {
        position: fixed;
        inset: 0;
        display: grid;
        place-items: center;
        background: rgba(0, 0, 0, 0.45);
        z-index: 30;
      }

      .feedback-card {
        width: min(420px, 92vw);
        border-radius: 18px;
        border: 1px solid rgba(16, 185, 129, 0.4);
        background: var(--card);
        padding: 18px;
        display: grid;
        gap: 10px;
      }

      .feedback-card.error-card {
        border-color: rgba(220, 38, 38, 0.45);
      }

      .icon {
        width: 38px;
        height: 38px;
        border-radius: 999px;
        display: grid;
        place-items: center;
        color: white;
        background: #10b981;
      }

      .feedback-card.error-card .icon {
        background: #dc2626;
      }

      .feedback-card button {
        justify-self: end;
        height: 40px;
        min-width: 88px;
        border-radius: 10px;
        border: 1px solid var(--stroke);
        background: transparent;
        color: var(--ink);
        cursor: pointer;
      }
    `,
  ],
})
export class CriarUsuarioPageComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly usuarioService = inject(UsuarioService);

  readonly roles: UsuarioCreateRequest["role"][] = ["ADMIN", "MANAGER", "OPERATOR", "SUPPORT", "CUSTOMER"];

  readonly form = this.fb.group({
    nome: ["", [Validators.required]],
    email: ["", [Validators.required, Validators.email]],
    senha: ["", [Validators.required, Validators.minLength(6)]],
    role: ["CUSTOMER" as UsuarioCreateRequest["role"], [Validators.required]],
  });

  loading = false;
  feedbackVisible = false;
  feedbackType: "success" | "error" = "success";
  feedbackMessage = "";

  submit(): void {
    if (this.form.invalid || this.loading) {
      return;
    }

    this.loading = true;
    const payload = this.form.getRawValue();
    this.usuarioService.criarUsuario(payload).subscribe({
      next: () => {
        this.loading = false;
        this.form.reset({ nome: "", email: "", senha: "", role: "CUSTOMER" });
        this.openFeedback("success", "");
      },
      error: (error: { error?: { message?: string; error?: string } }) => {
        this.loading = false;
        const message = error?.error?.message ?? error?.error?.error ?? "Falha ao criar usuário.";
        this.openFeedback("error", message);
      },
    });
  }

  closeFeedback(): void {
    this.feedbackVisible = false;
  }

  private openFeedback(type: "success" | "error", message: string): void {
    this.feedbackType = type;
    this.feedbackMessage = message;
    this.feedbackVisible = true;
  }
}
