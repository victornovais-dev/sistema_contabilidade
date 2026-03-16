import { inject, Injectable } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { Observable } from "rxjs";
import { UsuarioCreateRequest } from "../models/usuario-create-request";

@Injectable({ providedIn: "root" })
export class UsuarioService {
  private readonly http = inject(HttpClient);

  criarUsuario(payload: UsuarioCreateRequest): Observable<void> {
    return this.http.post<void>("/api/v1/usuarios", payload);
  }
}
