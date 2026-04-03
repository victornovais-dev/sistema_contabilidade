# Metadados
- **Versão:** v1.0
- **Data:** 2026-04-02
- **Solicitante:** Usuário (Victor)
- **Status:** Rascunho

---

# Título
Deploy do `sistema_contabilidade` em AWS (EC2) com CI/CD no Bitbucket e banco no Amazon RDS (MySQL).

---

# Resumo
Hoje o projeto roda localmente com MySQL em `localhost` e configuração via `.env`/variáveis de ambiente. A proposta é colocar a aplicação em uma instância AWS (EC2), usar Bitbucket para versionamento e pipeline de build/deploy, e hospedar o banco em Amazon RDS (MySQL), com credenciais armazenadas com segurança (SSM/Secrets Manager) e rede segura (VPC/Security Groups). O resultado esperado é um deploy repetível, com rollback simples e aplicação apontando para o RDS em produção.

---

# Contexto
- Backend: Spring Boot (Java 25) com páginas estáticas em `src/main/resources/static` e APIs REST em `/api/v1/**`.
- Banco: MySQL (driver runtime) com `spring.jpa.hibernate.ddl-auto=update` (risco em produção).
- Config atual em `A:\Projetos IA\Sistema\projeto\sistema_contabilidade\src\main\resources\application.properties`:
  - `spring.datasource.url=jdbc:mysql://localhost:3306/${DB_NAME:...}`
  - `spring.datasource.username=${DB_USERNAME}`
  - `spring.datasource.password=${DB_PASSWORD}`
  - `app.item.arquivos-dir=${APP_ITEM_ARQUIVOS_DIR:uploads/itens}` (precisa de disco persistente em EC2)
  - Variáveis sensíveis: `SESSION_CRYPTO_SECRET`, `JWT_EC_PRIVATE_KEY`, `JWT_EC_PUBLIC_KEY`

---

# Objetivo
- Publicar o app em uma instância EC2 usando Java 25.
- Usar Amazon RDS MySQL como banco de dados (sem `localhost`).
- Implementar CI/CD no Bitbucket para build e deploy automatizados.
- Garantir boas práticas de segurança: sem secrets no repo, rede restrita, IAM mínimo necessário.

---

# Suposições
- O alvo de “instância da Amazon” é **EC2** (não ECS/Beanstalk).
- O banco desejado no RDS é **MySQL** (compatível com o `mysql-connector-j` já usado).
- O deploy será inicial (sem necessidade de zero-downtime).
- O tráfego será servido pelo próprio EC2 (com Nginx opcional) ou por um ALB na frente.

---

# Fora de escopo
- Alta disponibilidade (multi-AZ/auto-scaling/load balancer) e blue/green deployment.
- Migrações robustas (Flyway/Liquibase) e governança de schema (apenas recomendações).
- Observabilidade completa (traces/metrics centralizadas); apenas logs e health básicos.
- CDN / WAF / proteção avançada.

---

# Estado atual
## Repositório
- `A:\Projetos IA\Sistema\projeto\sistema_contabilidade\pom.xml` define `java.version=25`.
- Páginas HTML/CSS/JS em:
  - `A:\Projetos IA\Sistema\projeto\sistema_contabilidade\src\main\resources\static\*.html`
  - `A:\Projetos IA\Sistema\projeto\sistema_contabilidade\src\main\resources\static\assets\**`
- API de relatório consolidado:
  - `GET /api/v1/relatorios/financeiro`

## Configuração
- Import opcional de `.env`: `spring.config.import=optional:file:.env[.properties]`
- DB local por default (`localhost:3306`), credenciais obrigatórias via env.

---

# Mudança proposta
## Arquitetura (alto nível)
1) **RDS MySQL** (produção): banco gerenciado, acesso permitido apenas a partir da instância EC2 (Security Group).
2) **EC2** (aplicação):
   - Executa o `jar` do Spring Boot via `systemd` (serviço).
   - Configuração via variáveis de ambiente (Systemd drop-in) ou carregamento via SSM Parameter Store.
   - Diretório de uploads em disco persistente (ex.: EBS em `/var/lib/sistema-contabilidade/uploads`).
3) **Bitbucket Pipelines**:
   - Build do `jar` com `./mvnw clean package`.
   - Publicação do artefato (recomendado: S3) e deploy por SSH/SSM na EC2.
   - Sem chaves permanentes no pipeline (recomendado: OIDC do Bitbucket → IAM Role na AWS).

## Decisões importantes
- Deploy em EC2 via `systemd` (simples e compatível com “instância da Amazon”).
- Banco no RDS MySQL 8.x.
- Segredos fora do repositório: SSM/Secrets Manager + IAM Instance Profile.

---

# Detalhamento de implementação

## Backend
### 1) Tornar URL do banco parametrizável para RDS
**Mudança recomendada (produção):**
- Alterar `spring.datasource.url` para aceitar host/porta via env, por exemplo:
  - `DB_HOST` (endpoint do RDS)
  - `DB_PORT` (default 3306)
  - `DB_NAME`

Exemplo de alvo (a implementar no código/config):
- `spring.datasource.url=jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:sistema_contabilidade}?useSSL=true&serverTimezone=America/Sao_Paulo`

### 2) Pool de conexões (Hikari)
Para evitar excesso de conexões no RDS:
- Configurar `spring.datasource.hikari.maximum-pool-size` (ex.: 10–30) e timeouts conforme carga.

### 3) Schema em produção
Risco atual: `spring.jpa.hibernate.ddl-auto=update`.
Recomendação mínima:
- Em produção, usar `validate` e introduzir migrações (Flyway/Liquibase) quando possível.

### 4) Uploads (arquivos de comprovante)
Definir `APP_ITEM_ARQUIVOS_DIR` para um path persistente no EC2, ex.:
- `/var/lib/sistema-contabilidade/uploads/itens`

## Banco de dados (Amazon RDS MySQL)
### 1) Criar instância RDS
- Engine: MySQL 8.x
- Storage: gp3
- Multi-AZ: opcional (fora de escopo se custo for problema)
- Backup retention: pelo menos 7 dias
- Parameter Group: ajustar se necessário (timeouts/charset)
- Charset/collation: `utf8mb4` (ideal)

### 2) Rede e segurança
- Criar um Security Group do RDS permitindo **apenas** inbound `3306` a partir do Security Group da EC2.
- Desabilitar acesso público (Public accessibility = No).

### 3) Usuário e permissões
- Criar usuário específico do app (ex.: `app_user`) com permissões apenas no schema do app.
- Armazenar senha no Secrets Manager ou SSM Parameter Store (SecureString).

## Infra (AWS EC2)
### 1) Criar a instância
- AMI: Amazon Linux 2023 ou Ubuntu LTS
- Tipo: t3.small/t3.medium (ajustar conforme carga)
- Disco: EBS (>= 20GB) + diretório para uploads

### 2) Instalar Java 25
- Usar distribuição suportada (ex.: Amazon Corretto 25, Temurin 25) e garantir `JAVA_HOME` apontando para JDK/JRE 25.

### 3) Serviço systemd
Criar um unit file, ex.: `/etc/systemd/system/sistema-contabilidade.service`:
- `ExecStart=/usr/bin/java -jar /opt/sistema-contabilidade/app.jar`
- `EnvironmentFile=/etc/sistema-contabilidade/env`
- `Restart=always`

### 4) Reverse proxy (opcional mas recomendado)
Se expor porta 80/443:
- Nginx como reverse proxy para `localhost:8080`
- TLS via ACM + ALB (melhor) ou Let’s Encrypt no próprio host (mais simples)

### 5) IAM / Secrets
- Instance profile com permissão de leitura somente dos parâmetros/segredos necessários.
- Preferir SSM Parameter Store/Secrets Manager ao invés de `.env` no servidor.

## CI/CD (Bitbucket)
### 1) Estrutura mínima no repositório
Adicionar:
- `bitbucket-pipelines.yml`:
  - step build: `./mvnw -DskipTests clean package` (ou `test` dependendo da maturidade)
  - step deploy (manual/auto): publicar artefato no S3 e acionar deploy na EC2

### 2) Autenticação Bitbucket → AWS (recomendado)
- Configurar OIDC do Bitbucket para assumir uma IAM Role na AWS (sem AWS_ACCESS_KEY no repo).
Alternativa (menos segura):
- Usar `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` como variáveis de repositório no Bitbucket.

### 3) Estratégia de deploy
Opção A (simples): SSH
- Pipeline faz `scp` do `jar` e reinicia `systemd`.
Requer:
- Chave SSH guardada com segurança no Bitbucket (variável/secure file) e `known_hosts`.

Opção B (recomendado): S3 + SSM
- Pipeline envia `jar` para S3 (versionado por commit/tag).
- Pipeline dispara `aws ssm send-command` para a EC2 baixar do S3 e reiniciar o serviço.

---

# Variáveis de ambiente (produção)
Obrigatórias (não commitar):
- `DB_HOST` (endpoint RDS)
- `DB_PORT` (3306)
- `DB_NAME`
- `DB_USERNAME`
- `DB_PASSWORD`
- `SESSION_CRYPTO_SECRET`
- `JWT_EC_PRIVATE_KEY`
- `JWT_EC_PUBLIC_KEY`
- `APP_ENV=prod`
- `APP_ITEM_ARQUIVOS_DIR=/var/lib/sistema-contabilidade/uploads/itens`
- `APP_CORS_ALLOWED_ORIGINS` (domínio do frontend)

---

# Riscos e mitigação
- **DDL automático em produção (`ddl-auto=update`)**: risco de alteração não controlada → migrar para migrações e `validate`.
- **Conexões demais no RDS**: ajustar pool Hikari e monitorar `Threads_connected`.
- **Upload local em EC2**: risco de perda em rebuild → usar EBS persistente e backup.
- **Secrets vazarem no pipeline**: usar OIDC + SSM/Secrets Manager.

---

# Critérios de aceite
- A aplicação em EC2 sobe via `systemd` e responde em `http(s)://<domínio-ou-ip>/home`.
- O app conecta no RDS e consegue CRUD de lançamentos sem erros de conexão.
- Secrets não estão versionados no repo e são injetados via env/SSM/Secrets Manager.
- Pipeline do Bitbucket consegue:
  - buildar o `jar`
  - publicar e executar deploy (manual ou automático) com rollback possível (artefato anterior).

---

# Validação e verificação
- Teste de saúde após deploy:
  - `curl -I http://localhost:8080/home` (na EC2)
  - logs do serviço: `journalctl -u sistema-contabilidade -f`
- Verificar conectividade ao RDS:
  - logs sem `Communications link failure`
  - métricas RDS (Connections, CPU, FreeStorageSpace)

---

# Rollback
- Manter os últimos N artefatos no S3 (por tag/commit).
- Em rollback:
  1) baixar artefato anterior
  2) substituir `/opt/sistema-contabilidade/app.jar`
  3) `systemctl restart sistema-contabilidade`
