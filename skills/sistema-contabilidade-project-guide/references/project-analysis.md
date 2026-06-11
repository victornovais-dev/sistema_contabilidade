# Projeto

## Resumo

- Projeto principal: backend Spring Boot 4.0.3 com Java 25.
- Build: Maven Wrapper (`.\mvnw`) com Spotless, Checkstyle, SpotBugs, PMD, Error Prone, JaCoCo e ArchUnit.
- UI atual: primeiro render por Thymeleaf em `src/main/resources/templates`, com JS/CSS e fallbacks em `src/main/resources/static`.
- Excecao importante: as paginas publicas `/login` e `/primeiro_acesso` sao servidas diretamente de `src/main/resources/static`, enquanto as paginas autenticadas continuam vindo de Thymeleaf.
- O `frontend-angular/` existe, mas o fluxo principal ainda depende do backend server-side e dos assets estaticos.
- Em producao, o auth agora e orientado a provider (`local|cognito`), com `application-prod.properties` defaultando para `cognito`.

## Dependencias relevantes

- Spring Boot Web MVC, Data JPA, Security, Validation, Thymeleaf, Actuator
- Redis + Spring Cache + Caffeine
- JWT (`jjwt`) e sessao opaca em cookie
- MySQL runtime
- MapStruct + Lombok
- Playwright para PDF
- AWS S3 SDK para storage remoto
- Micrometer/Prometheus para metricas, auditoria de query count por rota, timing HTTP e memoria JVM

## Modulos principais

- `auth`: login, refresh, logout, sessao opaca, cookies, rotas do usuario autenticado
- `usuario`: CRUD, perfil, paginas, navbar model advice
- `rbac`: roles, permissoes e inicializacao
- `item`: lancamentos paginados, anexos, download, observacao, verificacao, descricoes e tipos de documento
- `home`: dashboard inicial e cards
- `relatorio`: resumo financeiro, PDF Playwright e mock executivo
- `notificacao`: notificacoes derivadas de receitas
- `security`: JWT/session filter, rate limit, headers, CORS, CSRF, rotas admin secretas
- `monitoring`: contagem de queries por request, timing HTTP, memoria JVM e exportacao Micrometer
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

- `/` redireciona dinamicamente:
  - anonimo -> `/login`
  - autenticado -> `/home`
- `/login`
- `/primeiro_acesso`
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
- o primeiro acesso Cognito usa a pagina publica `/primeiro_acesso` e o endpoint `/api/v1/auth/complete-new-password`
- o login pode responder `202 Accepted` quando o provider retorna `NEW_PASSWORD_REQUIRED`; nesse caso o frontend passa a depender do cookie `SC_LOGIN_CHALLENGE` antes da criacao da sessao normal
- `auth-session.js` tambem centraliza cache compartilhado de roles do usuario via `SCAuth.getUserRoles()`
- `GET /api/v1/auth/routes` continua restrito a admin em `SecurityConfig`; `403` no console para usuario nao admin pode ser apenas ruido de frontend e nao necessariamente a causa raiz de um problema de login
- assets versionados do frontend agora usam sufixo no nome do arquivo em vez de `?v=`:
  - exemplo: `auth-session-20260502-startup-perf-1.js`
  - exemplo: `navbar-20260420-navbar-notification-count-fix-3.css`
  - exemplo: `lista_comprovantes-20260513-descender-fix-1.css`
- `auth/routes` e chamadas auxiliares da navbar devem ficar fora do caminho critico da primeira renderizacao quando possivel
- a navbar precisa ficar sincronizada entre:
  - `src/main/resources/templates/fragments/navbar.html`
  - `src/main/resources/static/partials/navbar.html`
  - `static/assets/js/navbar-20260502-startup-perf-1.js`
  - `static/assets/css/navbar-20260420-navbar-notification-count-fix-3.css`
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
- `AuthController` tambem pode devolver challenge de primeiro acesso (`NEW_PASSWORD_REQUIRED`) e depende de cookie temporario de challenge ate a troca de senha ser concluida
- `app.security.cors.allowed-origins` vem de `APP_CORS_ALLOWED_ORIGINS`; em producao, dominio ausente nessa env quebra `/api/v1/auth/refresh` e login com `Invalid CORS request`
- `AdminRouteService` calcula paths secretos a partir de `app.admin.route-secret`
- `app.admin.route-secret` por padrao deriva de `ADMIN_ROUTE_SECRET` ou `SESSION_CRYPTO_SECRET`
- `AuthService` nao fala direto com local/Cognito; ele delega para `AuthProviderStrategyResolver`, que escolhe `LocalAuthProviderStrategy` ou `CognitoAuthProviderStrategy`
- quando `app.auth.provider=cognito` no profile `prod`, o boot valida `AWS_REGION`, `COGNITO_USER_POOL_ID`, `COGNITO_APP_CLIENT_ID`, `SESSION_CRYPTO_SECRET`, `JWT_EC_PRIVATE_KEY` e `JWT_EC_PUBLIC_KEY`
- `COGNITO_APP_CLIENT_SECRET` continua opcional; se vier configurado, `CognitoSecretHashService` adiciona `SECRET_HASH` nas chamadas ao Cognito
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
- Observacao operacional recente:
  - o drag-and-drop usa uma area dedicada e nao deve mais consumir `drop` globalmente no documento; isso reduz navegacao acidental/crash ao arrastar arquivo vindo de fontes estranhas, inclusive preview dentro de `.zip`
- Upload/PDF:
  - validacao binaria de PDF acontece em `PdfUploadSecurityValidator`
  - storage local e S3 usam nomes sanitizados e chave final com UUID

### 2. Lista de comprovantes

- Pagina: `lista_comprovantes.html`
- API principal: `GET /api/v1/itens`
- A API principal usa paginacao server-side:
  - entrada: `page`, `pageSize`, `role`, `tipo`, `dataInicio`, `dataFim`, `descricao`, `razao`
  - saida: envelope `ItemListPageResponse` com `items`, `page`, `pageSize`, `totalItems`, `totalPages`, `hasNext`, `hasPrevious`
  - ordenacao padrao: `horarioCriacao desc, id desc`
  - `descricao` usa filtro exato
  - `razao` usa filtro `like`
- A consulta paginada passa por `ItemListService` e `ItemListPageRepositoryImpl`.
- O hot path atual ja foi otimizado para `Slice` e evita `count(*)` por requisicao.
- A busca textual de `razaoSocialNome` agora usa o campo derivado `razaoSocialBusca`, com tokens normalizados e busca por prefixo; em MySQL/MariaDB o projeto tenta subir para `FULLTEXT` quando o indice existe ou pode ser criado.
- Indices atuais em `itens`:
  - `idx_itens_horario_id (horario_criacao, id)`
  - `idx_itens_role_horario_id (role_nome, horario_criacao, id)`
- O card do item suporta:
  - observacao
  - download de arquivo unico ou ZIP
  - anexos adicionais por item
  - exclusao
  - check de verificacao
- Regras atuais:
  - `CONTABIL` pode acessar detalhes e endpoints de leitura/atualizacao do item quando o escopo permitir
  - item verificado nao pode ser excluido
  - `CONTABIL` nao pode excluir
  - `SUPPORT` pode marcar vermelho -> verde, mas nao pode voltar verde -> vermelho
  - `CANDIDATO` nao pode alterar verificacao
  - `Item` usa optimistic locking com `@Version`; itens legados com `version = null` sao normalizados antes de alteracoes de verificacao
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
- O endpoint web retorna resumo leve em `RelatorioFinanceiroResumoResponse`, sem listas completas de receitas/despesas.
- O resumo e consolidado em `RelatorioFinanceiroConsolidador`; o `RelatorioFinanceiroService` fica como orquestrador.
- O PDF usa caminho detalhado separado em `RelatorioFinanceiroPdfDataFactory`.
- Parte do layout/agrupamento visual continua sendo completada pelo frontend.
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

- `RelatorioFinanceiroService` orquestra o relatorio e delega consolidacao/factory para classes menores.
- `RelatorioFinanceiroConsolidador` calcula:
  - receitas financeiras
  - receitas estimaveis
  - despesas consideradas
  - despesas advocacia/contabilidade
  - total de despesas
  - percentuais de categorias limitadas
  - saldo final
- `RelatorioFinanceiroPdfDataFactory` monta o payload detalhado do PDF.
- o PDF usa:
  - `relatorio-financeiro.html`
  - `PlaywrightPdfService`
  - `ThymeleafTemplateRenderer`
- o download pela pagina de relatorios hoje baixa o arquivo diretamente, sem abrir `about:blank` e sem depender de `file://`

## Configuracao e ambiente

- `application.properties` importa `.env`
- profile default: `local`
- `application.properties` define `app.auth.provider=${APP_AUTH_PROVIDER:local}`
- `application-prod.properties` define `app.auth.provider=${APP_AUTH_PROVIDER:cognito}`
- `application.properties` le `app.security.cors.allowed-origins` de `APP_CORS_ALLOWED_ORIGINS`, com fallback local para `http://localhost:3000`
- `application-local.properties` define:
  - MySQL local
  - `spring.jpa.hibernate.ddl-auto=update`
  - storage local em `uploads/itens`
  - limite de PDF: `20971520`
  - Redis local
  - `spring.thymeleaf.cache=false`
- root `docker-compose.yml` sobe Redis local em `127.0.0.1:6379` com volume `redis-data`, AOF e healthcheck `redis-cli ping`
- Cuidado:
  - `.env` pode sobrescrever storage, banco, cache e segredos
  - em deploy Docker com `docker run --env-file .env`, mudar a `.env` e dar apenas `restart` nao reaplica as variaveis; o container precisa ser recriado
  - no provider Cognito, `AWS_REGION` ausente derruba o boot antes do app subir
  - nao exponha valores de token, senha ou secret no chat

## Cognito, usuarios e roles

- O login Cognito usa estrategia dedicada em `CognitoAuthProviderStrategy`.
- O primeiro acesso (`NEW_PASSWORD_REQUIRED`) foi separado em pagina publica `/primeiro_acesso`.
- O backend sincroniza identidade e memberships do Cognito para a projecao local por:
  - `CognitoIdentitySyncService`
  - `CognitoRoleSyncService`
- `CognitoRoleSyncService` agora cria a `Role` local ausente a partir do grupo do Cognito normalizado, em vez de falhar quando o grupo ainda nao existe no banco.
- A administracao de usuarios em modo Cognito usa `CognitoUserManagementService`.
- A tela de criar/atualizar usuario nao deve mais depender apenas da lista local de roles:
  - `GET /api/v1/admin/roles/disponiveis` mescla roles locais com grupos do Cognito
  - isso passa por `RoleService.listarRolesDisponiveisParaUsuarios()`
- O catalogo de candidatos agora e compartilhado:
  - `CandidateRoleCatalogService` alimenta os filtros e listas de `item`, `notificacao` e `relatorio`
  - em provider Cognito, ele deriva apenas os grupos com descricao `CANDIDATO`
- O nome exibido na home pode vir do atributo `name` do Cognito quando o nome local estiver vazio ou igual ao email.
- Em producao, alguns incidentes recentes nao foram do Cognito em si, mas de dados legados no banco:
  - `usuarios.version` nulo
  - `sessoes_usuario.atualizada_em` com zero date
  - `refresh_token_cifrado` e `groups_snapshot` curtos demais para payload cifrado/snapshot
- Para o backend listar grupos do Cognito na UI admin, a identidade AWS precisa de permissao `cognito-idp:ListGroups`.

## Cache e performance

- cache atual usa Caffeine
- caches declarados:
  - `userDetails`
  - `itemDescricoes`
  - `itemTiposDocumento`
- `CustomUserDetailsService` aquece e invalida `userDetails` por email e por `id:<uuid>`; mudancas de grupo/role nao devem mais exigir restart do container para aparecerem em novos carregamentos de sessao.
- Redis continua configurado e pode rodar via Docker, mas nao acelera automaticamente a listagem principal de comprovantes enquanto `spring.cache.type=caffeine` e `SistemaContabilidadeApplication.cacheManager()` continuarem usando Caffeine.
- os assets principais versionados ja migraram de `?v=` para nomes de arquivo versionados, o que simplifica cache de browser/CDN para `/assets/**`
- a aplicacao habilita `server.compression` para HTML/CSS/JS/JSON; se a compressao sumir em producao, o problema provavelmente esta no proxy/CDN e nao na configuracao base do Spring
- Bons candidatos a cache sao dados auxiliares estaveis, como roles disponiveis, descricoes e tipos de documento.
- A listagem `/api/v1/itens` muda com verificacao, observacao, upload e exclusao; cachear esse endpoint exige invalidacao cuidadosa.

## CloudFront, ALB e DNS

- O projeto ja rodou atras de CloudFront com origin no ALB.
- Para `/assets/*`, um behavior separado pode precisar de `Origin request policy = Managed-AllViewer`; sem isso, os assets podem voltar `502` mesmo quando o HTML principal responde.
- O dominio raiz e o `www` podem compartilhar a mesma distribuicao CloudFront.
- O certificado ideal do CloudFront em `us-east-1` cobre:
  - `sacsdigital.com.br`
  - `*.sacsdigital.com.br`
- So o certificado do CloudFront nao basta para `www`: se o listener do ALB continuar com certificado sem cobertura para `www.sacsdigital.com.br`, o CloudFront pode retornar `502` no hostname `www` ate o certificado da origem tambem ser corrigido.
- `NS` adicional no Route 53 nao e a solucao para esse cenario; o ajuste real fica em aliases `A`, CloudFront, certificado e listener/origem.

## Observabilidade e query count

- `QueryCountStatementInspector` conta queries Hibernate por request
- `QueryCountFilter`:
  - reseta/encerra o contexto
  - adiciona `X-Query-Count`
  - publica `http.server.query.count`
  - ignora `/actuator`, assets e `favicon`
- threshold operacional local continua configuravel por property
- stack de observabilidade local fica em `observability/`

## Timing HTTP e memoria

- `RequestTimingFilter`:
  - adiciona `X-App-Time-Ms`
  - adiciona `Server-Timing`
  - publica metrica Micrometer de duracao por rota
  - registra log de request lenta acima do threshold configurado
- `RequestMonitoringPathUtils` centraliza paths ignorados por filtros de monitoramento.
- `MemoryMonitoringMetrics` expoe gauges de heap e metaspace.
- `MemoryMonitoringService` registra snapshots/alertas de pressao de memoria quando habilitado.

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
  - coverage aproximado recente `83.6%`
  - duplicated lines `0.0%`

## Testes

- suite recente: `427` testes verdes
- existem testes dedicados para:
  - auth/session/csrf
  - controllers WebMvc
  - storage local e S3
  - PDF Playwright
  - notificacoes
  - paginacao server-side de itens
  - optimistic locking e verificacao de item legado
  - memory/request timing monitoring
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
