import { TestBed } from "@angular/core/testing";
import { provideHttpClient } from "@angular/common/http";
import { HttpTestingController, provideHttpClientTesting } from "@angular/common/http/testing";
import { provideRouter, Router } from "@angular/router";
import { AuthService } from "./auth.service";

describe("AuthService", () => {
  let service: AuthService;
  let httpTestingController: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    localStorage.clear();

    TestBed.configureTestingModule({
      providers: [AuthService, provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });

    service = TestBed.inject(AuthService);
    httpTestingController = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    httpTestingController.verify();
    localStorage.clear();
  });

  it("should persist token on successful login", () => {
    service.login({ email: "user@email.com", senha: "123456" }).subscribe((response) => {
      expect(response.token).toBe("token-123");
      expect(localStorage.getItem("sc_access_token")).toBe("token-123");
    });

    const request = httpTestingController.expectOne("/api/v1/auth/login");
    expect(request.request.method).toBe("POST");
    expect(request.request.body).toEqual({ email: "user@email.com", senha: "123456" });
    request.flush({ token: "token-123" });
  });

  it("should clear token and navigate to /login on logout", () => {
    localStorage.setItem("sc_access_token", "token-123");
    const navigateSpy = spyOn(router, "navigate").and.resolveTo(true);

    service.logout();

    expect(localStorage.getItem("sc_access_token")).toBeNull();
    expect(navigateSpy).toHaveBeenCalledWith(["/login"]);
  });
});
