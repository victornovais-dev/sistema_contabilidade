import { Routes } from "@angular/router";
import { authGuard } from "./core/guards/auth.guard";
import { ShellLayoutComponent } from "./layout/shell-layout.component";
import { LoginPageComponent } from "./pages/login-page.component";
import { HomePageComponent } from "./pages/home-page.component";
import { CriarUsuarioPageComponent } from "./pages/criar-usuario-page.component";
import { AdicionarComprovantePageComponent } from "./pages/adicionar-comprovante-page.component";
import { ListaComprovantesPageComponent } from "./pages/lista-comprovantes-page.component";
import { RelatoriosPageComponent } from "./pages/relatorios-page.component";

export const appRoutes: Routes = [
  { path: "login", component: LoginPageComponent },
  {
    path: "",
    component: ShellLayoutComponent,
    canActivate: [authGuard],
    children: [
      { path: "home", component: HomePageComponent },
      { path: "criar-usuario", component: CriarUsuarioPageComponent },
      { path: "adicionar-comprovante", component: AdicionarComprovantePageComponent },
      { path: "lista-comprovantes", component: ListaComprovantesPageComponent },
      { path: "relatorios", component: RelatoriosPageComponent },
      { path: "", pathMatch: "full", redirectTo: "home" },
    ],
  },
  { path: "**", redirectTo: "home" },
];
