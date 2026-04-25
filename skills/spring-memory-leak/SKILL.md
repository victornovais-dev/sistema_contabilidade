---
name: spring-memory-leak
description: >
  Detecta e analisa vazamentos de memória em projetos Spring Boot com Java.
  Use esta skill SEMPRE que o usuário mencionar: vazamento de memória, memory leak,
  OutOfMemoryError, heap crescendo, aplicação ficando lenta com o tempo, análise de heap dump,
  GC overhead, metaspace cheio, permgen cheio, memória não liberada, objetos acumulando,
  "minha aplicação Spring consome muita memória", "heap dump Spring Boot", "JVM memory analysis",
  análise de memória Java, profiling de memória, ou qualquer suspeita de memory leak em projeto Java/Spring.
  Inclui análise estática do código, geração de configurações de monitoramento, scripts de coleta de heap dump,
  e relatório detalhado de possíveis causas.
---

# Spring Boot Memory Leak Detector

Skill para identificar, analisar e corrigir vazamentos de memória em projetos Spring Boot.

## O que esta skill faz

1. **Análise estática do código** — varre os arquivos Java em busca de padrões de risco
2. **Geração de configurações de monitoramento** — Actuator + Micrometer + JVM flags
3. **Scripts de coleta** — comandos para tirar heap dump, GC log, thread dump
4. **Relatório detalhado** — lista problemas encontrados, severidade e como corrigir

---

## Fluxo de uso

### Passo 1 — Entender o contexto

Pergunte ao usuário (se ainda não souber):
- O código está disponível para upload? Ou só o path no servidor?
- Há sintomas observados? (OOM, lentidão, heap crescendo no APM)
- Qual versão do Java e Spring Boot?
- Usa algum cache (Ehcache, Redis, Caffeine)?
- Tem heap dump ou GC log disponível?

Se o usuário **já subiu arquivos**, pule para o Passo 2 diretamente.

### Passo 2 — Análise estática dos arquivos Java

Leia os arquivos `.java` disponíveis e procure os padrões listados em `references/patterns.md`.

Para cada arquivo analisado, aplique o checklist de padrões de risco.

### Passo 3 — Gerar artefatos de saída

Dependendo do que o usuário precisa, gere um ou mais dos seguintes:

**a) Relatório de análise estática** (sempre gerar)
→ Arquivo `memory-leak-report.md` com achados, severidade e correções sugeridas

**b) Configuração de monitoramento** (se o usuário quiser instrumentar o projeto)
→ `application-memory.yml` com Actuator + Micrometer
→ `jvm-flags.sh` com flags de GC e heap dump automático

**c) Script de diagnóstico em produção** (se o usuário precisar de coleta ao vivo)
→ `diagnose-memory.sh` com comandos jcmd/jmap/jstack

**d) Classe de listener de diagnóstico** (opcional, para monitoramento via código)
→ `MemoryLeakDiagnostics.java` — bean Spring que loga alertas de memória

### Passo 4 — Apresentar e explicar

- Mostre o relatório com os achados mais críticos primeiro
- Explique cada problema encontrado em linguagem clara
- Ofereça implementar as correções se o usuário quiser

---

## Padrões críticos a identificar

Leia `references/patterns.md` para a lista completa e detalhada.

Resumo rápido dos grupos de risco:

| Grupo | Exemplos de risco |
|---|---|
| **Coleções estáticas** | `static List/Map` sem limite de tamanho |
| **Listeners não removidos** | `addListener` sem `removeListener` |
| **ThreadLocal sem remove()** | `threadLocal.set()` sem `threadLocal.remove()` |
| **Caches sem eviction** | `@Cacheable` sem TTL/maxSize |
| **Conexões não fechadas** | streams, connections sem try-with-resources |
| **Inner classes anônimas** | classes anônimas segurando referência ao outer |
| **ClassLoader leaks** | carga dinâmica de classes sem descarga |
| **Session scope** | beans `@SessionScoped` acumulando estado |
| **Event listeners Spring** | `@EventListener` em beans com ciclo de vida incorreto |
| **Circular references** | dependências circulares criando grafos não coletáveis |

---

## Template do relatório

```markdown
# Relatório de Análise de Vazamento de Memória
**Projeto:** <nome>
**Data:** <data>
**Arquivos analisados:** <N>

## Resumo Executivo
<2-3 linhas com o estado geral>

## Achados Críticos 🔴
### [CRÍTICO] <Título do problema>
- **Arquivo:** `path/to/File.java` linha X
- **Padrão:** <nome do padrão>
- **Por que vaza:** <explicação clara>
- **Correção:** <código corrigido ou passos>

## Achados de Atenção 🟡
...

## Boas Práticas Identificadas ✅
...

## Próximos Passos
1. ...
```

---

## Referências

- `references/patterns.md` — Catálogo completo de padrões de risco com exemplos de código
- `references/monitoring-templates.md` — Templates de configuração de monitoramento
