import { Component } from "@angular/core";

@Component({
  selector: "app-relatorios-page",
  standalone: true,
  template: `
    <section class="card">
      <h1>Relatórios</h1>
      <p>Migre aqui o conteúdo de <code>relatorios.html</code>.</p>
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
export class RelatoriosPageComponent {}
