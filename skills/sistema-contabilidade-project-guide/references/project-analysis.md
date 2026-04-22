# Projeto

## Resumo

- Projeto principal: backend Spring Boot 4.0.3 com Java 25.
- Build: Maven Wrapper (`.\mvnw`), com Spotless, Checkstyle, SpotBugs, PMD, Error Prone, Jacoco e ArchUnit.
- UI servida hoje: paginas estaticas em `src/main/resources/static`, protegidas por Spring Security e expostas por `PaginaUsuarioController`.
- Ha tambem um `frontend-angular/`, mas o fluxo atual servido pelo backend ainda depende fortemente dos arquivos estaticos.

## Dependencias relevantes

- Spring Boot Web MVC, Data JPA, Security, Validation, Thymeleaf, Actuator
- Redis + Spring Cache + Caffeine
- JWT (`jjwt`)
- MySQL runtime
- MapStruct + Lombok
- Playwright para PDF
- AWS S3 SDK para storage remoto

## Modulos principais

- `auth`: login e JWT
- `usuario`: CRUD de usuarios, paginas de usuario, regras de criacao/atualizacao
- `rbac`: roles e inicializacao de perfis
- `item`: lancamento de comprovantes, upload/download, observacao, verificacao, descricoes e tipos de documento
- `home`: cards e dashboard inicial
- `relatorio`: resumo financeiro e geracao de PDF
- `notificacao`: notificacoes geradas a partir de receitas
- `security`: filtros JWT, rate limit, headers, CORS, CSRF
- `common`: utilitarios compartilhados, como classificacao de receitas

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

Arquivos principais em `src/main/resources/static`:

- `adicionar_comprovante.html`
- `criar_usuario.html`
- `home.html`
- `lista_comprovantes.html`
- `relatorios.html`
- `notificacoes.html`

Cada pagina costuma ter JS/CSS proprios em `static/assets/js` e `static/assets/css`.

## Fluxos importantes

### 1. Adicionar comprovante

- Pagina: `adicionar_comprovante.html`
- API principal: `ItemController` em `/api/v1/itens`
- Busca dinamica de opcoes:
  - `/api/v1/itens/roles`
  - `/api/v1/itens/descricoes?tipo=...`
  - `/api/v1/itens/tipos-documento`
- Regras recentes importantes:
  - descricoes vem do backend
  - tipos de documento vem do backend
  - alguns campos tem validacao no front e no backend
  - receitas `CONTA DC`, `CONTA FEFC`, `CONTA FP` e `CONTA FEFEC` exigem anexo

### 2. Lista de comprovantes

- Pagina: `lista_comprovantes.html`
- API principal: `GET /api/v1/itens`
- O card do item ja suporta:
  - observacao
  - download de arquivos
  - exclusao
  - botao de verificacao persistido no banco

### 3. Home

- Pagina: `home.html`
- API principal: `/api/v1/home/dashboard`
- Hoje trabalha com cards de receitas totais, despesas totais, utilizado e saldo final
- A classificacao de receitas passa por `RevenueClassificationUtils`

### 4. Relatorios

- Pagina: `relatorios.html`
- API principal: `/api/v1/relatorios/financeiro`
- PDF: `/api/v1/relatorios/financeiro/pdf`
- Cards usam agregacoes do backend e, em alguns casos, fallback no frontend para manter compatibilidade

### 5. Notificacoes

- Pagina: `notificacoes.html`
- Receitas lancadas geram notificacao persistida
- API principal: `/api/v1/notificacoes`

## Seguranca

- `SecurityConfig` usa:
  - JWT stateless
  - CSRF com cookie
  - CORS configuravel por property
  - rate limit filter
  - CSP, HSTS, Referrer-Policy e Permissions-Policy
- `/criar_usuario`, `/admin`, `/gerenciar_roles` exigem `ADMIN`
- Demais paginas principais exigem autenticacao

## Configuracao e armadilhas de ambiente

- `application.properties` importa `.env` com `spring.config.import=optional:file:.env[.properties]`
- O profile default e `local`
- `application-local.properties` define:
  - MySQL local
  - `spring.jpa.hibernate.ddl-auto=update`
  - storage local em `uploads/itens`
  - Redis local
- Armadilha importante:
  - variaveis no `.env` podem sobrescrever o comportamento local
  - exemplo classico: `APP_STORAGE_TYPE=s3` quebra o fluxo local de upload se o bucket nao estiver configurado

## Cache e performance

- Cache atual usa Caffeine
- Caches declarados:
  - `userDetails`
  - `itemDescricoes`
  - `itemTiposDocumento`
- O projeto tambem possui Redis configurado; confirme se a mudanca e cache local, distribuido ou apenas configuracao preparada

## Testes

- Ha cerca de 60 arquivos de teste em `src/test/java`
- Suites relevantes por area:
  - controller webmvc tests
  - service tests
  - DTO tests
  - initializer tests
  - `ArchitectureRulesTest`
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

## Estrategia pratica para mudancas

1. Identificar a pagina ou modulo principal.
2. Confirmar se a regra de negocio ja existe no backend antes de mexer no frontend.
3. Verificar se ha cache, fallback, seed ou catalogo envolvido.
4. Espelhar validacoes criticas em frontend e backend quando o fluxo e de formulario.
5. Rodar os testes da area afetada e depois o fluxo de qualidade exigido pelo repositorio.
