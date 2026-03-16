import { Component, inject } from "@angular/core";
import { RouterLink } from "@angular/router";
import { ThemeService } from "../../core/services/theme.service";
import { AuthService } from "../../core/services/auth.service";

@Component({
  selector: "app-navbar",
  standalone: true,
  imports: [RouterLink],
  template: `
    <nav class="navbar">
      <div class="navbar-content">
        <a class="brand" routerLink="/home" aria-label="Ir para a home">
          <span class="brand-mark">SC</span>
          <span class="brand-name">Sistema</span>
        </a>
        <div class="nav-actions">
          <button class="logout-btn" type="button" (click)="logout()">Logout</button>
          <button
            class="theme-toggle"
            type="button"
            [attr.aria-label]="isDark ? 'Ativar modo claro' : 'Ativar modo escuro'"
            (click)="toggleTheme()"
          >
            {{ isDark ? "☀" : "☾" }}
          </button>
        </div>
      </div>
    </nav>
  `,
  styles: [
    `
      .navbar {
        position: fixed;
        top: 0;
        left: 0;
        right: 0;
        height: 64px;
        border-bottom: 1px solid var(--stroke);
        background: color-mix(in srgb, var(--bg) 84%, transparent);
        z-index: 20;
      }

      .navbar-content {
        height: 100%;
        width: min(1100px, 92vw);
        margin: 0 auto;
        display: flex;
        align-items: center;
        justify-content: space-between;
      }

      .brand {
        display: inline-flex;
        align-items: center;
        gap: 10px;
        text-decoration: none;
        font-weight: 700;
      }

      .brand-mark {
        width: 36px;
        height: 36px;
        border-radius: 12px;
        display: grid;
        place-items: center;
        color: white;
        background: linear-gradient(135deg, #f97316, #ea580c);
      }

      .nav-actions {
        display: flex;
        align-items: center;
        gap: 10px;
      }

      .logout-btn {
        height: 42px;
        padding: 0 14px;
        border-radius: 999px;
        border: 1px solid rgba(249, 115, 22, 0.55);
        background: rgba(249, 115, 22, 0.25);
        color: #7c2d12;
        font-weight: 600;
        cursor: pointer;
      }

      html[data-theme="dark"] .logout-btn {
        background: rgba(249, 115, 22, 0.42);
        color: #fff;
        border-color: rgba(251, 146, 60, 0.72);
      }

      .theme-toggle {
        width: 42px;
        height: 42px;
        border-radius: 999px;
        border: 1px solid var(--stroke);
        background: var(--card);
        color: var(--ink);
        cursor: pointer;
      }
    `,
  ],
})
export class NavbarComponent {
  private readonly themeService = inject(ThemeService);
  private readonly authService = inject(AuthService);

  get isDark(): boolean {
    return this.themeService.currentTheme() === "dark";
  }

  toggleTheme(): void {
    this.themeService.toggleTheme();
  }

  logout(): void {
    this.authService.logout();
  }
}
