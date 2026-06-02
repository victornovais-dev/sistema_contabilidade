# Matriz de testes

## Login

| Cenário | Esperado |
|---|---|
| Cognito login válido | 200, SC_SESSION, JWT interno, usuário local sincronizado |
| Senha inválida | 401 genérico, sem detalhe de enumeração |
| Usuário inexistente | 401 genérico |
| Usuário desabilitado | 403 ou 401 conforme padrão, sem JWT |
| Cognito indisponível | 503/502 controlado, sem criar sessão |
| Usuário sem grupo | login pode ocorrer, mas sem acesso a páginas protegidas |

## Refresh

| Cenário | Esperado |
|---|---|
| Refresh válido | novo JWT interno |
| Refresh expirado | sessão local revogada, 401 |
| Grupo removido | novo JWT sem role removida |
| Usuário desabilitado | sessão local revogada |
| Falha temporária AWS | erro controlado, sem vazar token |

## Logout

| Cenário | Esperado |
|---|---|
| Logout válido | limpa SC_SESSION e revoga sessão local |
| Cognito já inválido | limpa sessão local mesmo assim |
| Sem sessão | idempotente ou 204/200 conforme contrato atual |

## Admin

| Cenário | Esperado |
|---|---|
| Criar usuário em produção | cria Cognito + banco local |
| Atualizar grupos | altera Cognito + sincroniza banco |
| Alterar só banco local em prod | proibido |
| Grupo desconhecido | falha segura ou alerta conforme config |

## Segurança

| Cenário | Esperado |
|---|---|
| app.auth.provider=local em prod | startup falha |
| refreshToken em resposta | nunca ocorre |
| AWS keys em properties | nunca ocorre |
| role no request | ignorada |
