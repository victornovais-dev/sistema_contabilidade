import { Component } from "@angular/core";
import { RouterOutlet } from "@angular/router";
import { NavbarComponent } from "../shared/navbar/navbar.component";

@Component({
  selector: "app-shell-layout",
  standalone: true,
  imports: [RouterOutlet, NavbarComponent],
  template: `
    <app-navbar />
    <main class="page-container">
      <router-outlet />
    </main>
  `,
  styles: [
    `
      .page-container {
        width: min(1100px, 92vw);
        margin: 84px auto 32px;
      }
    `,
  ],
})
export class ShellLayoutComponent {}
