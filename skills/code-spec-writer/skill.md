---
name: code-spec-writer
description: Use esta skill para transformar uma ideia, demanda ou bug em uma spec técnica clara, implementável e verificável, pronta para orientar desenvolvimento no Codex ou em qualquer agente de código.
---

# Code Spec Writer

## Objetivo

Esta skill existe para converter pedidos vagos, bugs, ideias de features ou mudanças arquiteturais em uma **spec de código** clara, objetiva e executável.

A spec deve ajudar um agente de código a:
- entender o problema de negócio e técnico
- definir escopo e fora de escopo
- identificar impacto no código existente
- propor arquitetura e plano de implementação
- definir critérios de aceite e validação
- reduzir ambiguidades antes da codificação

---

## Quando usar

Use esta skill quando o usuário pedir algo como:
- "crie uma spec"
- "transforme essa ideia em uma especificação técnica"
- "descreva como implementar essa feature"
- "faça um plano técnico antes de codar"
- "documente a mudança"
- "escreva uma especificação para o Codex implementar"

Também use quando o pedido estiver ambíguo e a melhor próxima etapa for gerar uma spec antes de editar código.

---

## Processo obrigatório

### 0) Triagem do pedido

Antes de gerar, verifique se o pedido deixa claro:
- qual é o problema atual
- qual é o resultado esperado

Se ambos estiverem ausentes ou totalmente implícitos, faça **no máximo 2 perguntas objetivas** antes de gerar.  
Nunca bloqueie esperando confirmação se for possível inferir com razoável confiança — mas sinalize o que foi inferido em **Suposições**.

### 1) Entendimento do pedido

Extraia:
- problema
- objetivo
- motivação
- restrições
- impacto esperado
- partes incertas

### 2) Leitura do contexto técnico

**Se houver repositório:**
- localizar módulos relevantes
- identificar convenções do projeto
- encontrar código parecido
- observar padrões arquiteturais existentes
- respeitar stack, testes e estilo já usados

**Se não houver repositório**, infira ou pergunte (conta como uma das 2 perguntas permitidas):
- linguagem e framework principal
- tipo de arquitetura (monolito, microsserviços, serverless, desktop, etc.)
- banco de dados
- onde roda (cloud, on-prem, local, browser)

Use essas informações para calibrar os detalhes técnicos da spec. Marque o que for inferido como **Suposição**.

### 3) Fechamento de escopo

Defina claramente:
- o que entra
- o que não entra
- riscos
- dependências
- impactos em banco, API, filas, cache, autenticação, front-end, observabilidade e deploy, se aplicável

### 4) Avaliação de tamanho

Se a implementação estimada ultrapassar **3 dias de trabalho** ou envolver **mais de 5 módulos distintos**, sinalize ao final da spec:

```
⚠️ Esta spec pode ser grande demais para um único PR. Considere dividir em:
- Spec A: [nome e escopo]
- Spec B: [nome e escopo]
```

### 5) Produção da spec

Siga o template padrão abaixo.

---

## Regras de escrita

- Escreva de forma direta, técnica e sem floreios.
- Seja específico sobre arquivos, módulos, endpoints, classes, tabelas e fluxos quando puder ser inferido.
- Quando algo não puder ser confirmado, marque explicitamente como **Suposição**.
- Não invente requisitos de negócio sem sinalizar.
- Prefira listas curtas e precisas.
- A spec deve ser útil para um agente implementar sem precisar reinterpretar tudo.
- Sempre priorize clareza, verificabilidade e escopo controlado.

---

# TEMPLATE PADRÃO DE SPEC

## Metadados
- **Versão:** v1.0
- **Data:** YYYY-MM-DD
- **Solicitante:** [quem pediu]
- **Status:** Rascunho | Em revisão | Aprovada | Implementada

---

## Título
Uma linha objetiva com a mudança.

---

## Resumo
Explique em 2 a 5 frases:
- o problema atual
- a mudança proposta
- o resultado esperado

---

## Contexto
Descreva:
- fluxo atual
- limitação atual
- por que a mudança é necessária
- contexto técnico relevante

---

## Objetivo
Declare de forma objetiva o que a implementação precisa alcançar.

---

## Fora de escopo
Liste explicitamente o que não será tratado nesta mudança.

---

## Estado atual
Descreva como o sistema funciona hoje.
Inclua, se possível:
- arquivos
- classes
- endpoints
- tabelas
- serviços
- jobs
- consumers/producers
- componentes de UI
- contratos externos

---

## Mudança proposta
Descreva a solução em nível técnico.
Explique:
- arquitetura da mudança
- fluxo antes/depois
- responsabilidades por camada
- impacto em contratos
- decisões importantes e alternativas descartadas

---

## Detalhamento de implementação

### Backend
Inclua quando aplicável:
- controllers/endpoints
- services/use cases
- repositories
- entidades/DTOs
- validações
- autenticação/autorização
- tratamento de erro
- eventos/mensageria
- cache

### Banco de dados
Inclua quando aplicável:
- tabelas afetadas
- colunas novas/alteradas
- índices
- migrations
- compatibilidade com dados existentes
- estratégia de rollback

### Front-end
Inclua quando aplicável:
- telas/componentes impactados
- mudanças de estado
- validações
- mensagens de erro
- contratos consumidos

### Integrações externas
Inclua quando aplicável:
- APIs externas
- filas
- webhooks
- tolerância a falhas
- timeouts / retries / idempotência

### Observabilidade
Inclua quando aplicável:
- logs esperados (nível, mensagem, campos de contexto)
- métricas novas ou alteradas
- traces distribuídos
- alertas que devem ser criados ou atualizados

---

## Arquivos ou módulos afetados

Liste os arquivos existentes e os novos arquivos esperados:

- `src/...` — descrição da mudança
- `src/...` — novo arquivo

Se não souber nomes exatos, use caminhos prováveis e marque como **Suposição**.

---

## Critérios de aceite

Defina critérios observáveis e verificáveis.
Exemplos:
- endpoint retorna 200 em cenário válido
- retorna 404 quando recurso não existir
- retorna 409 em conflito
- evento é publicado uma única vez
- migration executa sem perda de dados
- UI exibe mensagem correta
- logs e métricas refletem a operação

---

## Casos de teste

Use o formato abaixo para cada caso:

- **Dado** [estado ou contexto inicial]
- **Quando** [ação executada]
- **Então** [resultado esperado — com código HTTP, efeito observável ou estado final]

Cubra obrigatoriamente:
- cenário feliz
- cenários de erro esperados
- casos de borda
- regressão em fluxos adjacentes
- segurança/permissão, se aplicável
- concorrência/idempotência, quando fizer sentido

---

## Riscos
Liste riscos técnicos reais:
- quebra de contrato
- impacto em dados existentes
- duplicidade de eventos
- regressão em autenticação
- aumento de latência
- inconsistência transacional

## Mitigações
Para cada risco, indique como reduzir.

---

## Dependências
Liste dependências técnicas e de negócio:
- migrations
- feature flags
- credenciais / secrets
- endpoints externos
- atualização de contratos
- aprovações necessárias

---

## Plano de implementação

Descreva em passos pequenos e ordenados. Cada passo deve ser **atômico e deployável de forma isolada**, quando possível.

Marque com `[REVIEW]` os passos que envolvem:
- mudança de contrato externo
- migration de banco de dados
- alteração em autenticação ou autorização

Exemplo:
1. criar migration `Vxx__add_column_foo.sql` `[REVIEW]`
2. ajustar entidade e repository
3. implementar regra no service
4. expor endpoint
5. adicionar testes unitários
6. adicionar testes de integração
7. atualizar documentação

---

## Plano de validação

Explique como verificar que a mudança funcionou:
- testes automatizados que devem passar
- smoke tests pós-deploy
- cenários manuais críticos
- logs e métricas a monitorar
- período de observação pós-deploy, se relevante

---

## Rollback
Descreva como desfazer a mudança caso necessário.
Inclua: quais flags desativar, quais migrations reverter, se há impacto em dados já gravados.

---

## Suposições
Liste tudo que foi inferido e não confirmado.
Cada item deve ser revisado antes da implementação começar.

---

## Próxima ação recomendada

Escolha com base no estado da spec:

| Condição | Ação recomendada |
|---|---|
| Há suposições críticas não confirmadas | Revisar ambiguidades antes de implementar |
| Spec grande demais (>3 dias ou >5 módulos) | Quebrar em specs menores ou em issues separadas |
| Spec clara e escopo pequeno | Implementar diretamente |
| Destino é um agente autônomo | Gerar checklist de tarefas atômicas |
| Precisa de rastreabilidade | Transformar em issues ou commits planejados |

---

## Regras adicionais para boas specs

### Seja implementável
A spec deve permitir que outro agente:
- saiba o que construir
- saiba onde mexer
- saiba como validar
- saiba o que evitar

### Evite specs fracas
Não escreva coisas genéricas como:
- "ajustar backend"
- "melhorar performance"
- "criar endpoint"

Sem explicar exatamente como, onde e com quais regras.

### Use linguagem verificável
Prefira:
- "deve retornar 404 quando o ID não existir"

Em vez de:
- "deve tratar erro corretamente"

### Respeite o projeto existente
Não proponha trocar stack, reestruturar módulos ou introduzir patterns que o projeto não usa — sem justificativa técnica clara.

---

## Modo de saída

Quando o usuário pedir uma spec, responda com:
1. a spec completa seguindo o template
2. a seção **Próxima ação recomendada** preenchida com a opção mais adequada ao contexto

---

## Exemplos de pedidos que acionam esta skill

- "Crie uma spec para adicionar login com Google"
- "Transforme esse bug em uma especificação técnica"
- "Faça uma spec para migrar autenticação JWT para OAuth2"
- "Gere uma spec para criar um endpoint de upload de PDF com S3"
- "Crie uma spec antes de implementar essa feature"
