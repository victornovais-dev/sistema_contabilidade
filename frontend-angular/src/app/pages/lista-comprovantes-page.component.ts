import { Component } from "@angular/core";

@Component({
  selector: "app-lista-comprovantes-page",
  standalone: true,
  template: `
    <section class="card">
      <h1>Lista de Comprovantes</h1>
      <p>Migre aqui o conteúdo de <code>lista_comprovantes.html</code>.</p>
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
export class ListaComprovantesPageComponent {}
