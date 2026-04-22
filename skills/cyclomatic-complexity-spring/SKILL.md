---
name: cyclomatic-complexity-spring
description: >
  Analisa e reporta a complexidade ciclomática de projetos Spring Boot em Java.
  Use esta skill SEMPRE que o usuário mencionar: complexidade ciclomática, métricas de código,
  qualidade de código Java/Spring, refatoração de métodos complexos, análise estática de código,
  "meu código está complexo", "quero medir a qualidade do meu código Spring", ou qualquer pedido
  de análise de complexidade em projetos Java/Spring Boot. Gera relatórios detalhados, identifica
  métodos problemáticos, sugere refatorações e pode integrar com ferramentas como SonarQube,
  Checkstyle e PMD. Também cobre: configurar thresholds de complexidade, análise de branches
  if/else/switch/try-catch, métricas por classe/método/pacote, e CI/CD gates de qualidade.
---

# Complexidade Ciclomática em Spring Boot

## O que é Complexidade Ciclomática

A **complexidade ciclomática** (McCabe, 1976) mede o número de caminhos independentes no fluxo de execução de um método. A fórmula básica é:

```
CC = E - N + 2P
```
onde E = arestas do grafo, N = nós, P = componentes conectados.

**Na prática**, cada estrutura de decisão incrementa a complexidade em +1:
- Método simples: CC = 1
- Cada `if`, `else if`, `?:` (ternário): +1
- Cada `case` em `switch`: +1
- Cada `for`, `while`, `do-while`: +1
- Cada `catch`, `&&`, `||`: +1

### Escala de Referência

| CC | Risco | Ação |
|----|-------|------|
| 1–5 | Baixo | ✅ OK |
| 6–10 | Moderado | ⚠️ Monitorar |
| 11–20 | Alto | 🔴 Refatorar |
| > 20 | Crítico | 🚨 Urgente |

---

## Abordagens de Análise

Escolha conforme o contexto do usuário:

### A) Análise Manual (sem ferramentas extras)
Leia o código Java fornecido e calcule manualmente. Ver `references/manual-analysis.md`.

### B) Via Maven + plugins (recomendado para CI/CD)
Ver `references/maven-setup.md` — cobre PMD, Checkstyle, JaCoCo, SonarQube.

### C) Script de análise local
Ver `scripts/analyze.sh` — script bash que usa PMD CLI para análise rápida sem Maven.

### D) Relatório + Refatoração
Após análise, sugerir refatorações específicas. Ver `references/refactoring-patterns.md`.

---

## Workflow Padrão

1. **Identificar contexto**: O usuário tem o código? Quer configurar ferramenta? Quer refatorar?
2. **Analisar**: Calcular CC por método (manual ou via ferramenta)
3. **Priorizar**: Listar métodos com CC > 10 primeiro
4. **Reportar**: Tabela clara com método, CC, localização, risco
5. **Sugerir**: Para cada método crítico, propor refatoração concreta

---

## Análise Manual Rápida

Quando o usuário colar código Java, use este algoritmo:

```
CC = 1
+1 para cada: if, else if, for, while, do, case, catch, &&, ||, ?:
```

**Exemplo de relatório:**

```
📊 RELATÓRIO DE COMPLEXIDADE CICLOMÁTICA
════════════════════════════════════════

Classe: OrderService
Pacote: com.example.service

┌─────────────────────────────┬────┬──────────┬────────────┐
│ Método                      │ CC │ Linhas   │ Risco      │
├─────────────────────────────┼────┼──────────┼────────────┤
│ processOrder()              │ 18 │ 45-112   │ 🚨 CRÍTICO │
│ validatePayment()           │ 12 │ 115-158  │ 🔴 ALTO    │
│ calculateDiscount()         │  7 │ 160-185  │ ⚠️ MÉDIO  │
│ getOrderStatus()            │  3 │ 187-195  │ ✅ BAIXO   │
└─────────────────────────────┴────┴──────────┴────────────┘

Total de métodos: 4
CC média: 10.0
CC máxima: 18 (processOrder)
⚠️  2 método(s) acima do threshold recomendado (10)
```

---

## Configuração Maven (pom.xml snippet)

```xml
<!-- PMD para complexidade ciclomática -->
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-pmd-plugin</artifactId>
  <version>3.21.0</version>
  <configuration>
    <targetJdk>17</targetJdk>
    <rulesets>
      <ruleset>/rulesets/java/cyclomatic.xml</ruleset>
    </rulesets>
  </configuration>
</plugin>
```

Para detalhes completos de configuração (SonarQube, Checkstyle, GitHub Actions), leia `references/maven-setup.md`.

---

## Padrões de Refatoração Comuns

Para métodos com CC alta em Spring Boot:

| Padrão | Quando usar | CC esperada após |
|--------|-------------|-----------------|
| Extract Method | Bloco lógico isolável | -2 a -5 por extração |
| Strategy Pattern | Múltiplos `if` por tipo | -3 a -8 |
| Chain of Responsibility | Validações encadeadas | -4 a -10 |
| Guard Clauses | `if` aninhados profundos | -2 a -4 |
| Specification Pattern | Regras de negócio complexas | -5 a -12 |

Para exemplos de código completos, leia `references/refactoring-patterns.md`.

---

## Spring Boot: Hotspots Comuns

Classes Spring Boot que frequentemente acumulam alta CC:

- **`@Service`**: lógica de negócio com muitas regras
- **`@RestController`**: endpoints com validação inline
- **`@Scheduled`**: jobs com múltiplos caminhos de erro
- **Interceptors/Filters**: validação de requisições
- **`@EventListener`**: handlers de eventos com múltiplos tipos

Dica: métodos em `@Service` com CC > 15 são candidatos a aplicar o **Strategy Pattern** ou decompor em serviços menores.
