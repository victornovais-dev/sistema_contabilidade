export interface UsuarioCreateRequest {
  nome: string;
  email: string;
  senha: string;
  role: "ADMIN" | "MANAGER" | "OPERATOR" | "SUPPORT" | "CUSTOMER";
}
