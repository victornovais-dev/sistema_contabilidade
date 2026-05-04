# Relatorio de Race Conditions
**Projeto:** sistema_contabilidade  
**Data:** 2026-04-28

## Resumo Executivo
Dois riscos concretos apareceram no codigo:
- `UsuarioService` tinha janela `findByEmail -> save`, onde duas requisicoes simultaneas podiam passar na validacao e disputar insert do mesmo email.
- `Item` nao tinha versionamento otimista, entao updates concorrentes podiam sobrescrever observacao, verificacao, anexos ou exclusao sem detectar conflito.

## Correcoes Aplicadas
### [ALTO] Lost update em `Item`
- **Arquivos:** `item/model/Item.java`, testes de concorrencia em `item/repository`
- **Solucao:** `@Version` na entidade para detectar update/delete concorrente com `OptimisticLockingFailureException`
- **Efeito:** requisicao stale agora falha com `409` em vez de sobrescrever estado mais novo

### [ALTO] Duplicate insert por corrida de email em `UsuarioService`
- **Arquivos:** `usuario/service/UsuarioService.java`, `usuario/model/Usuario.java`
- **Solucao:** manter unique constraint do banco como ultima defesa e traduzir `DataIntegrityViolationException` para `409 Email ja cadastrado`
- **Efeito:** se duas criacoes paralelas do mesmo email passarem na pre-validacao, segunda ainda recebe erro de negocio consistente

### [MEDIO] Feedback de concorrencia para cliente
- **Arquivo:** `usuario/controller/UsuarioExceptionHandler.java`
- **Solucao:** mapear optimistic lock para `409`
- **Efeito:** frontend/cliente recebe resposta explicita de conflito concorrente

## Testes Adicionados
- teste concorrente real com `CountDownLatch` para provar que apenas um update de `Item` vence
- teste unitario para corrida de email capturada pelo banco
- teste unitario para handler de optimistic lock

## Proximos Passos
1. Se quiser blindar tambem CPF duplicado em multiplas instancias, adicionar constraint materializada/coluna normalizada no banco.
2. Se houver retries automáticos no frontend para PATCH/PUT, tratar `409` com refresh de estado antes de reenviar.
