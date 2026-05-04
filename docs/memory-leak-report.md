# Relatorio de Analise de Vazamento de Memoria
**Projeto:** sistema_contabilidade  
**Data:** 2026-04-28  
**Arquivos analisados:** varredura estatica em `src/main/java` e `src/test/java`

## Resumo Executivo
Nao encontrei colecoes estaticas sem limite, `@SessionScope` pesado, listeners sem remocao ou caches sem eviction no backend principal.  
Achei um ponto concreto de risco em `PlaywrightPdfService`: `InputStream` da logo era aberto sem fechamento explicito. Correcao aplicada no codigo com `try-with-resources`.

## Achados Criticos
Nenhum achado critico aberto apos a implementacao desta mudanca.

## Achados de Atencao
### [ALTO] Stream de recurso do PDF sem fechamento explicito
- **Arquivo:** `src/main/java/com/sistema_contabilidade/relatorio/service/PlaywrightPdfService.java`
- **Padrao:** recurso nao fechado
- **Por que vaza:** leitura repetida de recurso sem fechamento pode reter descritores e memoria nativa em execucoes sucessivas do gerador PDF
- **Correcao:** `resource.getInputStream()` agora roda dentro de `try-with-resources`

### [BAIXO] `ThreadLocal` de query count exige limpeza rigorosa
- **Arquivo:** `src/main/java/com/sistema_contabilidade/monitoring/query/QueryCountContext.java`
- **Padrao:** `ThreadLocal`
- **Por que merece atencao:** `ThreadLocal` em pool de threads vira leak se nao houver `remove()`
- **Status atual:** seguro; `QueryCountFilter` chama `QueryCountContext.clear()` em `finally`

### [BAIXO] Caches aplicacionais ja possuem limite e expiracao
- **Arquivos:** `src/main/resources/application.properties`, `src/main/java/com/sistema_contabilidade/SistemaContabilidadeApplication.java`
- **Padrao:** `@Cacheable` com Caffeine
- **Status atual:** seguro; ha `maximumSize` e `expireAfterWrite`

## Boas Praticas Identificadas
- `QueryCountContext` usa `ThreadLocal.remove()` no fluxo de encerramento do filtro
- caches `userDetails`, `itemDescricoes` e `itemTiposDocumento` usam Caffeine com eviction
- nao encontrei `@SessionScope` com payload grande
- nao encontrei `addListener(...)` sem remocao correspondente no backend principal

## Implementacoes Adicionadas
- metricas Prometheus: `app_memory_heap_usage_ratio` e `app_memory_metaspace_usage_ratio`
- configuracoes `app.memory-monitor.*` para threshold e logging agendado
- servico interno `MemoryMonitoringService` para snapshot e alerta de pressao de memoria

## Proximos Passos
1. Ativar `APP_MEMORY_MONITOR_SCHEDULED_LOGGING_ENABLED=true` em ambiente de investigacao quando houver suspeita real de crescimento de heap.
2. Monitorar tendencia de `app_memory_heap_usage_ratio` e `app_memory_metaspace_usage_ratio` no Prometheus/Grafana.
3. Se alerta persistir, coletar heap dump e thread dump com `jcmd`/`jstack` antes de alterar limites de heap.
