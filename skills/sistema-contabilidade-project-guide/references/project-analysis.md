# Projeto

## Resumo

- Projeto principal: backend Spring Boot 4.0.3 com Java 25.
- Build: Maven Wrapper (`.\mvnw`) com Spotless, Checkstyle, SpotBugs, PMD, Error Prone, JaCoCo e ArchUnit.
- UI atual: primeiro render por Thymeleaf em `src/main/resources/templates`, com JS/CSS e fallbacks em `src/main/resources/static`.
- O `frontend-angular/` existe, mas o fluxo principal ainda depende do backend server-side e dos assets estaticos.

## Dependencias relevantes

- Spring Boot Web MVC, Data JPA, Security, Validation, Thymeleaf, Actuator
- Redis + Spring Cache + Caffeine
- JWT (`jjwt`) e sessao opaca em cookie
- MySQL runtime
- MapStruct + Lombok
- Playwright para PDF
- AWS S3 SDK para storage remoto
- Micrometer/Prometheus para metricas e auditoria de query count por rota

## Modulos principais

- `auth`: login, refresh, logout, sessao opaca, cookies, rotas do usuario autenticado
- `usuario`: CRUD, perfil, paginas, navbar model advice
- `rbac`: roles, permissoes e inicializacao
- `item`: lancamentos, anexos, download, observacao, verificacao, descricoes e tipos de documento
- `home`: dashboard inicial e cards
- `relatorio`: resumo financeiro, PDF Playwright e mock executivo
- `notificacao`: notificacoes derivadas de receitas
- `security`: JWT/session filter, rate limit, headers, CORS, CSRF, rotas admin secretas
- `monitoring`: contagem de queries por request e exportacao Micrometer
- `common`: utilitarios compartilhados, como classificacao de receitas e filtro de roles tecnicas

## Arquitetura e convencoes

- ArchUnit exige sufixos e pacotes por camada:
  - `*Controller` em `..controller..`
  - `*Service` em `..service..`
  - `*Repository` em `..repository..`
- Fluxo preferencial do projeto: `controller -> service -> repository`
- `ArchitectureRulesTest` e a esteira Maven sao a fonte de verdade para convencoes e limites

## Paginas e rotas

Rotas HTML servidas por `PaginaUsuarioController`:

- `/login`
- `/criar_usuario`
- `/atualizar_usuario`
- `/adicionar_comprovante`
- `/home`
- `/lista_comprovantes`
- `/relatorios`
- `/notificacoes`
- `/admin`
- `/gerenciar_roles`
- `/404`

Observacao importante:

- as paginas admin legadas continuam existindo como rotas internas, mas o acesso real no browser passa por prefixos secretos gerados por `AdminRouteService`
- o filtro admin redireciona paths legados para `/404` e tambem esconde as APIs administrativas antigas

Arquivos centrais de UI:

- templates: `home.html`, `adicionar_comprovante.html`, `lista_comprovantes.html`, `relatorios.html`, `notificacoes.html`, `admin.html`
- navbar Thymeleaf: `templates/fragments/navbar.html`
- navbar estatica: `static/partials/navbar.html`
- mock executivo: `relatorio-executivo-exemplo.html`

## Navbar, sessao e frontend compartilhado

- `auth-session.js` centraliza bootstrap, refresh e logout do frontend autenticado
- a sessao principal usa cookie opaco `SC_SESSION`
- `SC_TOKEN` continua apenas como compatibilidade legada para alguns testes e fluxos antigos
- a navbar precisa ficar sincronizada entre:
  - `src/main/resources/templates/fragments/navbar.html`
  - `src/main/resources/static/partials/navbar.html`
  - `static/assets/js/navbar.js`
  - `static/assets/css/navbar.css`
- o badge de notificacoes considera apenas notificacoes nao verificadas

## Seguranca

- `SecurityConfig` usa:
  - JWT + autenticacao por sessao opaca
  - CSRF com cookie
  - CORS configuravel por property
  - CSP, HSTS, Referrer-Policy e Permissions-Policy
  - `RateLimitFilter`
  - `JwtAuthFilter`
  - `RequestContextMdcFilter`
- `PasswordEncoder` preferencial: Argon2, com compatibilidade para hashes antigos
- `AuthController` limpa o cookie legado e trabalha com `SC_SESSION`
- `AdminRouteService` calcula paths secretos a partir de `app.admin.route-secret`
- `app.admin.route-secret` por padrao deriva de `ADMIN_ROUTE_SECRET` ou `SESSION_CRYPTO_SECRET`
- ao tocar auth/admin, leia tambem:
  - `SecurityPaths`
  - `AdminRouteService`
  - `AuthController`
  - `JwtAuthFilter`
  - `SecurityConfig`

## Fluxos importantes

### 1. Adicionar comprovante

- Pagina: `adicionar_comprovante.html`
- API principal: `POST /api/v1/itens`
- Selects dinamicos:
  - `/api/v1/itens/roles`
  - `/api/v1/itens/descricoes?tipo=...`
  - `/api/v1/itens/tipos-documento?tipo=...`
- Regras atuais:
  - anexo em PDF e obrigatorio na criacao
  - tamanho maximo atual: `20 MB`
  - validacao existe no frontend e no backend
  - `Extrato Bancario` existe na UI como tipo especial e limita descricao a `CONTA FEFC`, `CONTA FP` e `CONTA DC`
  - `CONTABIL` nao acessa a pagina
  - CPF deve ser unico; CNPJ pode repetir
- Upload/PDF:
  - validacao binaria de PDF acontece em `PdfUploadSecurityValidator`
  - storage local e S3 usam nomes sanitizados e chave final com UUID

### 2. Lista de comprovantes

- Pagina: `lista_comprovantes.html`
- API principal: `GET /api/v1/itens`
- O card do item suporta:
  - observacao
  - download de arquivo unico ou ZIP
  - anexos adicionais por item
  - exclusao
  - check de verificacao
- Regras atuais:
  - item verificado nao pode ser excluido
  - `CONTABIL` nao pode excluir
  - `SUPPORT` pode marcar vermelho -> verde, mas nao pode voltar verde -> vermelho
  - `CANDIDATO` nao pode alterar verificacao
  - receitas marcadas como verificadas atualizam o badge da navbar, mas continuam aparecendo na pagina de notificacoes
  - o modal de anexos mostra card de erro por arquivo rejeitado

### 3. Home

- Pagina: `home.html`
- API principal: `/api/v1/home/dashboard`
- Cards principais:
  - receitas totais
  - despesas totais
  - utilizado
  - saldo final
- O bloco lateral relevante hoje inclui `Datas importantes` no lugar do card antigo `Receitas vs Despesas`
- `Datas importantes` tem scroll proprio e layout visual alinhado ao painel de ultimos lancamentos

### 4. Relatorios

- Pagina: `relatorios.html`
- API principal: `/api/v1/relatorios/financeiro`
- PDF: `/api/v1/relatorios/financeiro/pdf`
- O resumo vem do backend em `RelatorioFinanceiroService`; parte do layout/agrupamento continua sendo completada pelo frontend
- `PlaywrightPdfService` renderiza o template Thymeleaf do PDF e embute a logo como data URI
- `relatorio-financeiro.html` e `PlaywrightPdfService` sao os arquivos centrais do PDF
- O mock `relatorio-executivo-exemplo.html` existe como referencia visual separada
- `Despesas por categoria` usa grafico circular e paleta fixa por categoria

### 5. Notificacoes

- Pagina: `notificacoes.html`
- API principal: `/api/v1/notificacoes`
- Toda receita sincroniza uma notificacao persistida
- Regras atuais:
  - se a receita e removida, a notificacao correspondente sai
  - se a receita fica verificada, a notificacao continua na pagina
  - o badge da navbar conta apenas notificacoes ainda nao verificadas
  - `Valor lancado` reflete itens com check verde
  - a pagina e restrita a `ADMIN` e `CONTABIL`

## Relatorio financeiro e PDF

- `RelatorioFinanceiroService` calcula:
  - receitas financeiras
  - receitas estimaveis
  - despesas consideradas
  - despesas advocacia/contabilidade
  - total de despesas
  - percentuais de categorias limitadas
  - saldo final
- o PDF usa:
  - `relatorio-financeiro.html`
  - `PlaywrightPdfService`
  - `ThymeleafTemplateRenderer`
- o download pela pagina de relatorios hoje baixa o arquivo diretamente, sem abrir `about:blank` e sem depender de `file://`

## Configuracao e ambiente

- `application.properties` importa `.env`
- profile default: `local`
- `application-local.properties` define:
  - MySQL local
  - `spring.jpa.hibernate.ddl-auto=update`
  - storage local em `uploads/itens`
  - limite de PDF: `20971520`
  - Redis local
  - `spring.thymeleaf.cache=false`
- Cuidado:
  - `.env` pode sobrescrever storage, banco, cache e segredos
  - nao exponha valores de token, senha ou secret no chat

## Cache e performance

- cache atual usa Caffeine
- caches declarados:
  - `userDetails`
  - `itemDescricoes`
  - `itemTiposDocumento`
- Redis continua configurado para cenarios que precisem cache distribuido

## Observabilidade e query count

- `QueryCountStatementInspector` conta queries Hibernate por request
- `QueryCountFilter`:
  - reseta/encerra o contexto
  - adiciona `X-Query-Count`
  - publica `http.server.query.count`
  - ignora `/actuator`, assets e `favicon`
- threshold operacional local continua configuravel por property
- stack de observabilidade local fica em `observability/`

## SonarQube e qualidade

- fluxo padrao do projeto:
  - `.\scripts\use-java25.ps1`
  - `.\mvnw spotless:apply`
  - `.\mvnw test`
  - `.\mvnw -DskipTests compile checkstyle:check spotbugs:check pmd:check`
  - `.\mvnw verify`
- script utilitario:
  - `scripts/sonar-precommit.ps1`
- o script usa:
  - `SONAR_HOST_URL`
  - `SONAR_TOKEN` ou `SONARQUBE_TOKEN`
  - `SONAR_PROJECT_KEY`
- instancia local recente do time:
  - SonarQube Community `26.4.0.121862`
  - dashboard: `http://localhost:9000`
- estado mais recente validado no repositorio:
  - issues abertas `0`
  - hotspots `0`
  - quality gate `OK`
  - new coverage `85.0%`

## Testes

- suite recente: `406` testes verdes
- existem testes dedicados para:
  - auth/session/csrf
  - controllers WebMvc
  - storage local e S3
  - PDF Playwright
  - notificacoes
  - query count audit
  - Prometheus
  - ArchUnit
- antes de alterar regra sensivel, localize primeiro o teste mais proximo e alinhe a mudanca por ali

## Estrategia pratica para mudancas

1. Descobrir se a pagina usa template Thymeleaf, static HTML, ou ambos.
2. Confirmar se a regra ja existe no backend antes de mexer no frontend.
3. Ao alterar auth/admin, checar tambem `AdminRouteService`, `SecurityPaths`, `AuthController`, `JwtAuthFilter` e `SecurityConfig`.
4. Ao alterar upload/download, checar `PdfUploadSecurityValidator`, storage local/S3, `ItemController` e os testes WebMvc.
5. Ao alterar notificacoes, checar sincronizacao entre `ItemController`, `NotificacaoService`, navbar e pagina `notificacoes`.
6. Ao alterar relatorios, separar claramente:
   - agregacao backend
   - layout frontend
   - layout PDF Playwright
7. Se mexer em navbar ou pagina principal, sincronizar template, partial estatica, CSS e JS.
8. Rodar primeiro a suite afetada e depois o fluxo completo de qualidade.
