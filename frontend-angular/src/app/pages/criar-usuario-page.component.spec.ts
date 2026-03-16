import { ComponentFixture, TestBed } from "@angular/core/testing";
import { of, throwError } from "rxjs";
import { CriarUsuarioPageComponent } from "./criar-usuario-page.component";
import { UsuarioService } from "../core/services/usuario.service";

describe("CriarUsuarioPageComponent", () => {
  let fixture: ComponentFixture<CriarUsuarioPageComponent>;
  let component: CriarUsuarioPageComponent;
  let usuarioServiceSpy: jasmine.SpyObj<UsuarioService>;

  beforeEach(async () => {
    usuarioServiceSpy = jasmine.createSpyObj<UsuarioService>("UsuarioService", ["criarUsuario"]);

    await TestBed.configureTestingModule({
      imports: [CriarUsuarioPageComponent],
      providers: [{ provide: UsuarioService, useValue: usuarioServiceSpy }],
    }).compileComponents();

    fixture = TestBed.createComponent(CriarUsuarioPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it("should create", () => {
    expect(component).toBeTruthy();
  });

  it("should call service and show success feedback on submit success", () => {
    usuarioServiceSpy.criarUsuario.and.returnValue(of(void 0));
    component.form.setValue({
      nome: "Victor Novais",
      email: "victornovais77@gmail.com",
      senha: "123456",
      role: "ADMIN",
    });

    component.submit();

    expect(usuarioServiceSpy.criarUsuario).toHaveBeenCalledWith({
      nome: "Victor Novais",
      email: "victornovais77@gmail.com",
      senha: "123456",
      role: "ADMIN",
    });
    expect(component.feedbackVisible).toBeTrue();
    expect(component.feedbackType).toBe("success");
  });

  it("should show error feedback when service fails", () => {
    usuarioServiceSpy.criarUsuario.and.returnValue(
      throwError(() => ({ error: { message: "Email ja cadastrado" } })),
    );
    component.form.setValue({
      nome: "Victor Novais",
      email: "victornovais77@gmail.com",
      senha: "123456",
      role: "ADMIN",
    });

    component.submit();

    expect(component.feedbackVisible).toBeTrue();
    expect(component.feedbackType).toBe("error");
    expect(component.feedbackMessage).toBe("Email ja cadastrado");
  });

  it("should not call service when form is invalid", () => {
    component.form.setValue({
      nome: "",
      email: "invalido",
      senha: "1",
      role: "ADMIN",
    });

    component.submit();

    expect(usuarioServiceSpy.criarUsuario).not.toHaveBeenCalled();
  });
});
