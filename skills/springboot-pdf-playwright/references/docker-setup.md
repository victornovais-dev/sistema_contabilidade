# Docker Setup — Playwright no Spring Boot

## Problema

O Playwright baixa o Chromium em `~/.cache/ms-playwright/` na primeira execução.
Em ambientes Docker e CI esse diretório não existe por padrão — é necessário instalar
durante o build da imagem.

---

## Dockerfile recomendado (multi-stage)

```dockerfile
# ── Stage 1: build ──────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn package -DskipTests

# ── Stage 2: runtime ────────────────────────────────────────────
FROM mcr.microsoft.com/playwright/java:v1.44.0-jammy

WORKDIR /app

# Copia o jar buildado
COPY --from=build /app/target/*.jar app.jar

# Instala só o Chromium (evita baixar Firefox e WebKit desnecessários)
RUN mvn dependency:resolve -f pom.xml -q || true
# Playwright CLI — instala navegadores via variável de ambiente
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright
RUN java -cp app.jar com.microsoft.playwright.CLI install chromium

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Alternativa mais simples: imagem oficial Playwright

A Microsoft disponibiliza uma imagem base com Chromium já instalado:

```dockerfile
FROM mcr.microsoft.com/playwright/java:v1.44.0-jammy
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

> Essa imagem inclui todas as dependências de sistema (libglib, libnss, etc.) que o
> Chromium headless precisa no Linux. É a opção mais confiável para produção.

---

## GitHub Actions / CI

```yaml
- name: Install Playwright Chromium
  run: mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"
```

Ou, se usar a imagem `mcr.microsoft.com/playwright/java` como container do job, não
precisa desse step — o Chromium já está presente.

---

## application.properties / variáveis de ambiente úteis

```properties
# Força o diretório de browsers (útil em containers)
# PLAYWRIGHT_BROWSERS_PATH=/ms-playwright

# Desabilita download automático (use quando a imagem já tem o browser)
# PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
```

---

## Dependências de sistema (Linux bare-metal)

Se não usar a imagem oficial, instale manualmente:

```bash
apt-get install -y \
  libnss3 libatk1.0-0 libatk-bridge2.0-0 libcups2 \
  libdrm2 libxkbcommon0 libxcomposite1 libxdamage1 \
  libxfixes3 libxrandr2 libgbm1 libasound2 \
  libpango-1.0-0 libcairo2 libgtk-3-0
```

Depois instale o Chromium:

```bash
java -cp target/*.jar com.microsoft.playwright.CLI install chromium
```
