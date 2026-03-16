import { ComponentFixture, TestBed } from "@angular/core/testing";
import { of, throwError } from "rxjs";
import { provideRouter, Router } from "@angular/router";
import { LoginPageComponent } from "./login-page.component";
import { AuthService } from "../core/services/auth.service";
import { ThemeService } from "../core/services/theme.service";

describe("LoginPageComponent", () => {
  let fixture: ComponentFixture<LoginPageComponent>;
  let component: LoginPageComponent;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let themeServiceSpy: jasmine.SpyObj<ThemeService>;
  let router: Router;

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj<AuthService>("AuthService", ["login"]);
    themeServiceSpy = jasmine.createSpyObj<ThemeService>("ThemeService", ["toggleTheme"]);

    await TestBed.configureTestingModule({
      imports: [LoginPageComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceSpy },
        { provide: ThemeService, useValue: themeServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(LoginPageComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  it("should create", () => {
    expect(component).toBeTruthy();
  });

  it("should keep form invalid when empty", () => {
    expect(component.form.invalid).toBeTrue();
  });

  it("should call auth service and navigate on valid submit", () => {
    const navigateSpy = spyOn(router, "navigate").and.resolveTo(true);
    authServiceSpy.login.and.returnValue(of({ token: "token-123" }));
    component.form.setValue({ email: "user@email.com", senha: "123456" });

    component.submit();

    expect(authServiceSpy.login).toHaveBeenCalledWith({
      email: "user@email.com",
      senha: "123456",
    });
    expect(navigateSpy).toHaveBeenCalledWith(["/home"]);
  });

  it("should show error when login fails", () => {
    authServiceSpy.login.and.returnValue(throwError(() => new Error("unauthorized")));
    component.form.setValue({ email: "user@email.com", senha: "123456" });

    component.submit();

    expect(component.error).toBe("Usuário inexistente ou senha inválida.");
  });
});
