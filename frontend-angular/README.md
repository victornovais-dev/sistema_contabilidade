# Frontend Angular (migração do HTML estático)

Esta pasta contém uma base Angular standalone para substituir as páginas estáticas do Spring.

## O que já está pronto

- `NavbarComponent` componentizada
- `AuthService` com `login/logout/token`
- `ThemeService` com tema persistido em cookie/localStorage
- `authGuard` protegendo rotas autenticadas
- `authInterceptor` adicionando `Authorization: Bearer <token>` em `/api/*`
- `app.config.ts` com `provideRouter(appRoutes)` (padrão standalone)
- Rotas base:
  - `/login`
  - `/home`
  - `/criar-usuario`
  - `/adicionar-comprovante`
  - `/lista-comprovantes`
  - `/relatorios`

## Como rodar

1. `cd frontend-angular`
2. `npm install`
3. `npm start`

O dev server sobe em `http://localhost:4200` com proxy para backend Spring em `http://localhost:8080`.

## Testes (TDD e CI)

- Desenvolvimento (watch): `npm test`
- CI (single run + coverage): `npm run test:ci`

Fluxo recomendado (TDD frontend):
1. Escrever/ajustar teste primeiro.
2. Implementar o código para passar no teste.
3. Refatorar mantendo os testes verdes.
4. Antes de commit: executar `npm run test:ci`.

## Estratégia de migração

1. Migrar primeiro `login.html` para `login-page.component.ts`.
2. Migrar layout da `home.html`.
3. Migrar páginas com formulário (`criar-usuario` e `adicionar-comprovante`).
4. Migrar listagens e relatórios.
5. Quando Angular estiver pronto, publicar build em `src/main/resources/static` (ou servir separado).
