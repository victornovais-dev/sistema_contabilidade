# Projeto

## Resumo

- Projeto principal: backend Spring Boot 4.0.3 com Java 25.
- Build: Maven Wrapper (`.\mvnw`), com Spotless, Checkstyle, SpotBugs, PMD, Error Prone, Jacoco e ArchUnit.
- UI servida hoje: templates Thymeleaf em `src/main/resources/templates` para primeiro render server-side, com assets e fallbacks em `src/main/resources/static`.
- As paginas continuam dependentes de JS/CSS em `static/assets`, e a navbar tem fragmento Thymeleaf e partial estatico.
- Ha tambem um `frontend-angular/`, mas o fluxo atual servido pelo backend ainda depende fortemente dos arquivos estaticos.

## Dependencias relevantes

- Spring Boot Web MVC, Data JPA, Security, Validation, Thymeleaf, Actuator
- Redis + Spring Cache + Caffeine
- JWT (`jjwt`)
- MySQL runtime
- MapStruct + Lombok
- Playwright para PDF
- AWS S3 SDK para storage remoto
- Micrometer/Prometheus para metricas operacionais e auditoria de query count por rota

## Modulos principais

- `auth`: login e JWT
- `usuario`: CRUD de usuarios, paginas de usuario, regras de criacao/atualizacao
- `rbac`: roles e inicializacao de perfis
- `item`: lancamento de comprovantes, upload/download, observacao, verificacao, descricoes e tipos de documento
- `home`: cards e dashboard inicial
- `relatorio`: resumo financeiro e geracao de PDF
- `notificacao`: notificacoes geradas a partir de receitas
- `security`: filtros JWT, rate limit, headers, CORS, CSRF
- `monitoring`: contagem de queries SQL por request, exportacao Micrometer e integracao com Prometheus
- `common`: utilitarios compartilhados, como classificacao de receitas e roles tecnicas

## Arquitetura e convencoes

- ArchUnit exige sufixos e camadas:
  - `*Controller` em `..controller..`
  - `*Service` em `..service..`
  - `*Repository` em `..repository..`
- Dependencias permitidas:
  - controller pode acessar service e repository
  - service pode acessar repository
  - repository nao pode acessar as outras camadas
- Quando houver duvida estrutural, valide contra `ArchitectureRulesTest`.

## Paginas estaticas atuais

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

Pontos atuais importantes:

- `PaginaUsuarioController` retorna templates para as paginas autenticadas principais.
- `/login` e `/404` ainda retornam recursos HTML estaticos.
- `UsuarioNavbarModelAdvice` injeta dados de roles/usuario para a navbar Thymeleaf.
- `SecurityConfig` tambem controla acesso por pagina; atualize os testes quando mudar permissao de rota.

Arquivos principais em `src/main/resources/static`:

- `adicionar_comprovante.html`
- `criar_usuario.html`
- `home.html`
- `lista_comprovantes.html`
- `relatorios.html`
- `notificacoes.html`

Cada pagina costuma ter JS/CSS proprios em `static/assets/js` e `static/assets/css`.

Templates principais em `src/main/resources/templates`:

- `home.html`
- `adicionar_comprovante.html`
- `lista_comprovantes.html`
- `relatorios.html`
- `notificacoes.html`
- `fragments/navbar.html`

## Fluxos importantes

### 1. Adicionar comprovante

- Pagina: `adicionar_comprovante.html`
- API principal: `ItemController` em `/api/v1/itens`
- Busca dinamica de opcoes:
  - `/api/v1/itens/roles`
  - `/api/v1/itens/descricoes?tipo=...`
  - `/api/v1/itens/tipos-documento?tipo=...`
- Regras recentes importantes:
  - descricoes vem do backend
  - `CONTA FEFEC` foi removida de receitas no frontend e no backend
  - receitas `CONTA DC`, `CONTA FEFC` e `CONTA FP` exigem anexo
  - CPF deve ser unico; CNPJ pode repetir
  - tipos de documento sao separados por tipo:
    - Receita: Pix, Transferencia, Cheque, Dinheiro
    - Despesa: Nota fiscal, Fatura, Boleto, Outros
  - despesas em categorias limitadas passam por `ItemExpenseLimitService`
  - CONTABIL nao acessa a pagina de adicionar comprovante

### 2. Lista de comprovantes

- Pagina: `lista_comprovantes.html`
- API principal: `GET /api/v1/itens`
- O card do item ja suporta:
  - observacao
  - download de arquivos
  - exclusao
  - botao de verificacao persistido no banco
- Regras recentes importantes:
  - item verificado nao pode ser excluido
  - CONTABIL nao pode excluir comprovante
  - SUPPORT pode marcar vermelho -> verde, mas nao pode voltar verde -> vermelho
  - CANDIDATO nao ve o botao de check e nao pode alterar verificacao no backend
  - exclusao de item remove notificacao correspondente
  - frontend usa animacao suave ao excluir/sumir card

### 3. Home

- Pagina: `home.html`
- API principal: `/api/v1/home/dashboard`
- Hoje trabalha com cards de receitas totais, despesas totais, utilizado e saldo final
- Card `Utilizado` mostra percentual e legenda `Receita Gasta`
- Card `Saldo final` tem legenda `Saldo final`
- A classificacao de receitas passa por `RevenueClassificationUtils`
- Botao `Novo lancamento` foi removido

### 4. Relatorios

- Pagina: `relatorios.html`
- API principal: `/api/v1/relatorios/financeiro`
- PDF: `/api/v1/relatorios/financeiro/pdf`
- Cards usam agregacoes do backend e, em alguns casos, fallback no frontend para manter compatibilidade
- Card `Limites de gastos` mostra graficos verticais com gasto e teto em valores numericos
- Limites atuais:
  - Combustivel: teto de 10% das despesas totais
  - Alimentacao: teto de 10% das despesas totais
  - Locacao de Veiculos: teto de 20% das despesas totais
- Locacao no limite considera somente `ALUGUEL DE VEICULOS`
- O backend tambem bloqueia novas despesas acima do teto dessas categorias

### 5. Notificacoes

- Pagina: `notificacoes.html`
- Receitas lancadas geram notificacao persistida
- API principal: `/api/v1/notificacoes`
- Notificacoes sao atreladas a itens de receita:
  - se a receita existe em comprovantes, a notificacao correspondente deve existir
  - se a receita for deletada, a notificacao tambem deve ser removida
- O check de notificacao e o check de lista de comprovantes representam a mesma informacao de item/verificacao
- Card `Valor lancado` soma itens com check verde
- Textos atuais:
  - titulo deve renderizar como `Notificações` na UI
  - `Notificacoes` sem acento nos arquivos HTML/JS pode indicar regressao de encoding ou texto antigo
  - label operacional: `Soma dos itens lancados`
  - label de notificadas: `Soma das receitas notificadas`
- Menu de notificacoes exibe contador independente da pagina atual
- Pagina de notificacoes deve aparecer apenas para ADMIN e CONTABIL

### 6. Navbar e UI server-side

- Navbar foi embutida no HTML inicial para reduzir piscada durante troca de paginas.
- Existe navbar em:
  - `src/main/resources/templates/fragments/navbar.html`
  - `src/main/resources/static/partials/navbar.html`
- Mantenha as duas sincronizadas quando alterar estrutura visual ou permissoes de menu.
- Logos:
  - dark mode: `assets/img/sacs-contabil-navbar-branco.png`
  - light mode: `assets/img/sacs-contabil-navbar-azul.png`
- O contador de notificacoes e carregado por `assets/js/navbar.js`.
- Campo `Politico` foi renomeado para `Candidato`; opcoes tecnicas ADMIN, CONTABIL, MANAGER e SUPPORT nao devem aparecer como candidatos.

## Seguranca

- `SecurityConfig` usa:
  - JWT stateless
  - CSRF com cookie
  - CORS configuravel por property
  - rate limit filter
  - CSP, HSTS, Referrer-Policy e Permissions-Policy
- `/criar_usuario`, `/admin`, `/gerenciar_roles` exigem `ADMIN`
- `/adicionar_comprovante` exige autenticacao e bloqueia CONTABIL
- `/notificacoes` exige ADMIN ou CONTABIL
- Demais paginas principais exigem autenticacao
- Roles tecnicas atuais:
  - ADMIN
  - MANAGER
  - SUPPORT
  - CONTABIL
  - CANDIDATO
- `CandidateRoleUtils` centraliza roles tecnicas que devem ser filtradas de seletores de candidato.
- `UsuarioPadraoInitializer` nao deve mais depender de senha hardcoded:
  - nome: `DEFAULT_ADMIN_NAME` (fallback `Administrador`)
  - email: `DEFAULT_ADMIN_EMAIL` (fallback `admin@sistema.local`)
  - senha: `DEFAULT_ADMIN_PASSWORD`
- Se a base estiver vazia e `DEFAULT_ADMIN_PASSWORD` nao estiver configurada, o initializer registra aviso e nao cria usuario padrao.

## Configuracao e armadilhas de ambiente

- `application.properties` importa `.env` com `spring.config.import=optional:file:.env[.properties]`
- O profile default atual e `local`
- `application-local.properties` define:
  - MySQL local
  - `spring.jpa.hibernate.ddl-auto=update`
  - storage local em `uploads/itens`
  - Redis local
- Armadilha importante:
  - variaveis no `.env` podem sobrescrever o comportamento local
  - exemplo classico: `APP_STORAGE_TYPE=s3` quebra o fluxo local de upload se o bucket nao estiver configurado
- Em producao Docker/RDS, o app espera:
  - `SPRING_DATASOURCE_URL`
  - `DB_USERNAME_PROD` ou `DB_USERNAME`
  - `DB_PASSWORD_PROD` ou `DB_PASSWORD`
  - `SESSION_CRYPTO_SECRET`
- Para observabilidade local:
  - `APP_QUERY_MONITOR_ENABLED` ativa/desativa auditoria
  - `APP_QUERY_MONITOR_THRESHOLD` define o limite por request (padrao `15`)
  - `APP_QUERY_MONITOR_ADD_RESPONSE_HEADER` controla `X-Query-Count`
- Se o container reiniciar apos o banner Spring, verifique `/app/logs/*.json`.
- Erro `Unable to determine Dialect without JDBC metadata` costuma indicar URL JDBC ausente/invalida ou falha de conexao/autenticacao no banco.
- Na EC2, teste rede RDS com `nc -vz <host> 3306`; teste credencial com `mariadb -h <host> -P 3306 -u <user> -p`.
- Nao exponha senhas/tokens no chat ou em commits; se vazarem, rotacione.

## Cache e performance

- Cache atual usa Caffeine
- Caches declarados:
  - `userDetails`
  - `itemDescricoes`
  - `itemTiposDocumento`
- O projeto tambem possui Redis configurado; confirme se a mudanca e cache local, distribuido ou apenas configuracao preparada

## Observabilidade e auditoria de queries

- Hibernate usa `QueryCountStatementInspector` via `spring.jpa.properties.hibernate.session_factory.statement_inspector`.
- `QueryCountContext` mantem o contador por thread/request.
- `QueryCountFilter`:
  - reseta e encerra o contador por request
  - adiciona header `X-Query-Count`
  - registra a metrica Micrometer `http.server.query.count`
  - usa tags `method`, `uri` e `status`
  - ignora `/actuator`, assets estaticos e `favicon`
  - registra warning quando passar do limite configurado
- Endpoints criticos ja cobertos por auditoria de query budget:
  - `GET /api/v1/itens`
  - `GET /api/v1/itens/{id}`
  - `GET /api/v1/itens/{id}/arquivos`
  - `GET /api/v1/itens/{id}/arquivos/download`
  - `GET /api/v1/relatorios/financeiro`
- O alvo operacional atual e manter `X-Query-Count <= 15` nesses endpoints.
- `management.endpoints.web.exposure.include` expõe `health`, `info`, `metrics` e `prometheus`.
- O stack local de observabilidade fica em `observability/`:
  - Prometheus: `http://localhost:9090`
  - Grafana: `http://localhost:3001`
  - credenciais locais padrao: `admin / admin`
- Arquivos importantes:
  - `observability/prometheus/prometheus.yml`
  - `observability/prometheus/rules/query-count-alerts.yml`
  - `observability/grafana/dashboards/query-count-dashboard.json`
  - `observability/grafana/provisioning/**`
- O dashboard local mostra piores rotas por query count, media por request, ranking por rota e rotas acima do threshold.

## Testes

- Ha dezenas de arquivos de teste em `src/test/java`, incluindo WebMvc, service, repository, arquitetura e observabilidade
- Suite completa recente: 350 testes verdes
- Ha testes dedicados para:
  - query count header
  - auditoria de endpoints criticos
  - exportacao da metrica em `/actuator/prometheus`
  - fallback/compatibilidade de storage S3
- SonarQube recente:
  - Quality Gate OK
  - new code issues: 0
  - new coverage: 80.9%
  - duplicated lines density: 0.0%
- Suites relevantes por area:
  - controller webmvc tests
  - service tests
  - DTO tests
  - initializer tests
  - `ArchitectureRulesTest`
  - `QueryCountAuditIntegrationTest`
  - `QueryCountPrometheusIntegrationTest`
- Para mudancas localizadas, prefira rodar a suite diretamente afetada antes do fluxo completo

## Comandos operacionais

Antes de Maven no PowerShell:

```powershell
.\scripts\use-java25.ps1
```

Comandos recorrentes:

```powershell
.\mvnw test
.\mvnw verify
.\mvnw spotless:apply
.\mvnw -Dtest=ArchitectureRulesTest test
```

Fluxo de qualidade completo usado no projeto:

```powershell
.\scripts\use-java25.ps1
.\mvnw spotless:apply
.\mvnw test
.\mvnw -DskipTests compile checkstyle:check spotbugs:check pmd:check
.\mvnw verify
```

SonarQube:

- `scripts/sonar-precommit.ps1` usa `SONAR_TOKEN`/`SONARQUBE_TOKEN` e `SONAR_HOST_URL`.
- Se Sonar estiver em Docker sem porta publicada, rode scanner na rede do container ou publique a porta 9000.
- O filtro que mais aparece nas tarefas recentes e:
  - `issueStatuses=OPEN,CONFIRMED`
  - `inNewCodePeriod=true`
- Relatorio local esperado apos analise limpa:
  - Quality Gate OK
  - Issues abertas 0

## Estrategia pratica para mudancas

1. Identificar a pagina ou modulo principal.
2. Confirmar se a regra de negocio ja existe no backend antes de mexer no frontend.
3. Verificar se ha cache, fallback, seed ou catalogo envolvido.
4. Espelhar validacoes criticas em frontend e backend quando o fluxo e de formulario.
5. Rodar os testes da area afetada e depois o fluxo de qualidade exigido pelo repositorio.
6. Se mexer em pagina com navbar, atualize template Thymeleaf, partial estatico, CSS e JS juntos.
7. Se alterar roles/permissoes, atualize backend, frontend, `SecurityConfig`, `PaginaUsuarioControllerTest` e filtros de candidato.
8. Se mexer em listagem/relatorio/download de itens, observe tambem o budget de queries e atualize os testes/a metrica quando necessario.
