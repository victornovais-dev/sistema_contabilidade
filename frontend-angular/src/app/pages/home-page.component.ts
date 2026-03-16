import { Component } from "@angular/core";

@Component({
  selector: "app-home-page",
  standalone: true,
  template: `
    <section class="card">
      <h1>Home</h1>
      <p>Estrutura Angular pronta. Migre aqui o conteúdo de <code>home.html</code>.</p>
    </section>
  `,
  styles: [
    `
      .card {
        border: 1px solid var(--stroke);
        background: var(--card);
        border-radius: 18px;
        padding: 20px;
      }
    `,
  ],
})
export class HomePageComponent {}
