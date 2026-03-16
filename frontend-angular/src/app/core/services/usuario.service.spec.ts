import { TestBed } from "@angular/core/testing";
import { provideHttpClient } from "@angular/common/http";
import { HttpTestingController, provideHttpClientTesting } from "@angular/common/http/testing";
import { UsuarioService } from "./usuario.service";
import { UsuarioCreateRequest } from "../models/usuario-create-request";

describe("UsuarioService", () => {
  let service: UsuarioService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [UsuarioService, provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(UsuarioService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it("should send POST to /api/v1/usuarios", () => {
    const payload: UsuarioCreateRequest = {
      nome: "Victor Novais",
      email: "victornovais77@gmail.com",
      senha: "123456",
      role: "ADMIN",
    };

    service.criarUsuario(payload).subscribe();

    const request = httpTestingController.expectOne("/api/v1/usuarios");
    expect(request.request.method).toBe("POST");
    expect(request.request.body).toEqual(payload);
    request.flush({});
  });
});
