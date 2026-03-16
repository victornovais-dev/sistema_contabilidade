import { inject, Injectable } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { Router } from "@angular/router";
import { Observable, tap } from "rxjs";

type LoginPayload = { email: string; senha: string };
type LoginResponse = { token: string };

@Injectable({ providedIn: "root" })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly tokenKey = "sc_access_token";

  login(payload: LoginPayload): Observable<LoginResponse> {
    return this.http.post<LoginResponse>("/api/v1/auth/login", payload).pipe(
      tap((response) => {
        localStorage.setItem(this.tokenKey, response.token);
      }),
    );
  }

  logout(): void {
    localStorage.removeItem(this.tokenKey);
    document.cookie = "SC_TOKEN=; Max-Age=0; path=/";
    this.router.navigateByUrl("/login");
  }

  token(): string | null {
    return localStorage.getItem(this.tokenKey);
  }

  isAuthenticated(): boolean {
    return Boolean(this.token());
  }
}
