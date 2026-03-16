import { Routes } from "@angular/router";
import { authGuard } from "./core/guards/auth.guard";

export const appRoutes: Routes = [
  {
    path: "login",
    loadComponent: () => import("./pages/login-page.component").then((m) => m.LoginPageComponent),
  },
  {
    path: "",
    canActivate: [authGuard],
    loadComponent: () => import("./layout/shell-layout.component").then((m) => m.ShellLayoutComponent),
    children: [
      {
        path: "home",
        loadComponent: () => import("./pages/home-page.component").then((m) => m.HomePageComponent),
      },
      {
        path: "criar-usuario",
        loadComponent: () =>
          import("./pages/criar-usuario-page.component").then((m) => m.CriarUsuarioPageComponent),
      },
      {
        path: "adicionar-comprovante",
        loadComponent: () =>
          import("./pages/adicionar-comprovante-page.component").then((m) => m.AdicionarComprovantePageComponent),
      },
      {
        path: "lista-comprovantes",
        loadComponent: () =>
          import("./pages/lista-comprovantes-page.component").then((m) => m.ListaComprovantesPageComponent),
      },
      {
        path: "relatorios",
        loadComponent: () => import("./pages/relatorios-page.component").then((m) => m.RelatoriosPageComponent),
      },
      { path: "", pathMatch: "full", redirectTo: "home" },
    ],
  },
  { path: "**", redirectTo: "home" },
];
