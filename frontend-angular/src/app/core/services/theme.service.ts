import { Injectable } from "@angular/core";

type ThemeMode = "light" | "dark";

@Injectable({ providedIn: "root" })
export class ThemeService {
  private readonly key = "theme";

  currentTheme(): ThemeMode {
    const cookieTheme = this.readCookie(this.key);
    const storageTheme = localStorage.getItem(this.key);
    const resolved = cookieTheme ?? storageTheme;
    return resolved === "dark" ? "dark" : "light";
  }

  initializeTheme(): void {
    this.applyTheme(this.currentTheme());
  }

  toggleTheme(): void {
    const next = this.currentTheme() === "dark" ? "light" : "dark";
    this.applyTheme(next);
  }

  private applyTheme(mode: ThemeMode): void {
    document.documentElement.dataset["theme"] = mode;
    localStorage.setItem(this.key, mode);
    const expires = new Date(Date.now() + 365 * 864e5).toUTCString();
    document.cookie = `${this.key}=${encodeURIComponent(mode)}; expires=${expires}; path=/`;
  }

  private readCookie(name: string): string | null {
    const pattern = new RegExp(`(?:^|;\\s*)${name}=([^;]+)`);
    const match = document.cookie.match(pattern);
    return match ? decodeURIComponent(match[1]) : null;
  }
}
