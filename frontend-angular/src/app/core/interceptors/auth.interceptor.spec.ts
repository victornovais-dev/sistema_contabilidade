import { TestBed } from "@angular/core/testing";
import { HttpClient, provideHttpClient, withInterceptors } from "@angular/common/http";
import { HttpTestingController, provideHttpClientTesting } from "@angular/common/http/testing";
import { authInterceptor } from "./auth.interceptor";

describe("authInterceptor", () => {
  let httpClient: HttpClient;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });

    httpClient = TestBed.inject(HttpClient);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    localStorage.clear();
    httpTestingController.verify();
  });

  it("should append Authorization header for API requests when token exists", () => {
    localStorage.setItem("sc_access_token", "token-abc");

    httpClient.get("/api/v1/usuarios").subscribe();

    const request = httpTestingController.expectOne("/api/v1/usuarios");
    expect(request.request.headers.get("Authorization")).toBe("Bearer token-abc");
    request.flush([]);
  });

  it("should not append Authorization header for non-API requests", () => {
    localStorage.setItem("sc_access_token", "token-abc");

    httpClient.get("/assets/config.json").subscribe();

    const request = httpTestingController.expectOne("/assets/config.json");
    expect(request.request.headers.has("Authorization")).toBeFalse();
    request.flush({});
  });

  it("should not append Authorization header when token is absent", () => {
    httpClient.get("/api/v1/usuarios").subscribe();

    const request = httpTestingController.expectOne("/api/v1/usuarios");
    expect(request.request.headers.has("Authorization")).toBeFalse();
    request.flush([]);
  });
});
