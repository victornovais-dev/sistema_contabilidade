---
name: aws-cognito-spring-security
description: Use esta skill quando o projeto Spring Boot precisar integrar AWS Cognito em produção mantendo a tela /login atual, preservando os endpoints /api/v1/auth/login, /refresh, /logout, /me e /me/roles, emitindo JWT interno curto da aplicação, sincronizando Cognito Groups para roles locais e mantendo permissões detalhadas no banco local.
---

# Skill: AWS Cognito + Spring Boot com login atual, JWT interno e roles sincronizadas

## Objetivo da skill

Implemente ou revise a autenticação do projeto para o modelo híbrido abaixo:

```text
Produção:
Tela /login atual
  -> /api/v1/auth/login
  -> backend autentica no AWS Cognito via AWS SDK
  -> backend cria/atualiza SC_SESSION
  -> backend emite JWT curto interno da aplicação
  -> frontend continua usando Authorization: Bearer <jwt-interno>

Autorização:
Cognito Groups = fonte oficial da associação usuário -> role
Banco local = catálogo/perfil do usuário + vínculos de domínio + projeção sincronizada de roles
Permissões detalhadas = continuam no banco/código local
```

Este projeto NÃO deve ser convertido para Cognito Hosted UI no primeiro rollout. A tela `/login` atual deve ser mantida.

## Regras de arquitetura obrigatórias

1. Produção usa somente Cognito para autenticação.
2. Ambientes locais/testes podem continuar usando login local via `app.auth.provider=local`.
3. Use estratégia por provider:
   - `local`: mantém o fluxo atual.
   - `cognito`: autentica no Cognito usando AWS SDK `CognitoIdentityProviderClient`.
4. Preserve o contrato atual do frontend:
   - `POST /api/v1/auth/login`
   - `POST /api/v1/auth/refresh`
   - `POST /api/v1/auth/logout`
   - `GET /api/v1/auth/me`
   - `GET /api/v1/auth/me/roles`
5. Preserve o JWT curto da própria aplicação como bearer token consumido pelo JavaScript atual.
6. Authorities do JWT interno devem nascer da projeção local sincronizada com Cognito Groups.
7. O banco local continua existindo para:
   - perfil/catálogo do usuário;
   - `cognito_sub`;
   - vínculos de domínio como `Item.criadoPor`, dashboard, relatórios, notificações;
   - roles/permissions como catálogo da aplicação;
   - projeção sincronizada da associação usuário -> roles.
8. Cognito Groups é a origem oficial de "quem pertence a qual role".
9. Permissões detalhadas continuam locais.
10. Não armazene senha local para autenticação em produção.
11. Não envie `refreshToken` do Cognito para o frontend.
12. Não grave access key/secret key AWS no código. Use IAM Role da instância/serviço.

## Antes de alterar código

Inspecione primeiro:

```text
pom.xml ou build.gradle
application*.properties/yml
SecurityConfig.java
AuthController.java
AuthService.java
JwtAuthFilter.java
CustomUserDetailsService.java
UsuarioRepository
Usuario entity/model
Role/Permissao entities
controllers/services de criar_usuario e atualizar_usuario
mecanismo atual do SC_SESSION
mecanismo atual de emissão/validação do JWT interno
testes existentes de autenticação/autorização
```

Depois adapte com menor ruptura possível.

## Configuração esperada

Adicione provider configurável:

```properties
app.auth.provider=local
```

Em produção:

```properties
app.auth.provider=cognito
aws.region=${AWS_REGION}
aws.cognito.user-pool-id=${COGNITO_USER_POOL_ID}
aws.cognito.app-client-id=${COGNITO_APP_CLIENT_ID}
aws.cognito.app-client-secret=${COGNITO_APP_CLIENT_SECRET:}
aws.cognito.group-prefix=${COGNITO_GROUP_PREFIX:}
aws.cognito.username-attribute=email
```

Para produção, também manter os secrets do JWT interno enquanto o plano preservar esse JWT:

```properties
jwt.ec.private-key=${JWT_EC_PRIVATE_KEY}
jwt.ec.public-key=${JWT_EC_PUBLIC_KEY}
jwt.access-token-ttl=15m
```

Não use `DEFAULT_ADMIN_PASSWORD` em produção com Cognito.

## Dependências recomendadas

Maven:

```xml
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>cognitoidentityprovider</artifactId>
</dependency>

<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>sts</artifactId>
</dependency>
```

Se o projeto usa Spring Cloud AWS ou BOM da AWS SDK v2, mantenha o padrão já existente.

Para testes:

```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

Use Mockito/WireMock ou mocks do `CognitoIdentityProviderClient` para testes. Não dependa de Cognito real em teste unitário/integrado padrão.

## Modelo de strategy

Crie uma abstração para o provedor de autenticação.

```java
public interface AuthProviderStrategy {
    AuthLoginResult login(LoginRequest request);
    AuthRefreshResult refresh(RefreshRequest request, ScSession session);
    void logout(ScSession session);
    boolean supports(AuthProvider provider);
}
```

Enum:

```java
public enum AuthProvider {
    LOCAL,
    COGNITO
}
```

O `AuthService` deve delegar:

```java
@Service
public class AuthService {
    private final AuthProviderStrategyResolver resolver;

    public LoginResponse login(LoginRequest request) {
        AuthProviderStrategy strategy = resolver.current();
        AuthLoginResult result = strategy.login(request);

        // 1. garantir/sincronizar Usuario local
        // 2. sincronizar groups -> usuario_roles
        // 3. criar/atualizar SC_SESSION
        // 4. emitir JWT interno curto com authorities locais sincronizadas
        // 5. retornar exatamente o contrato atual do frontend
    }
}
```

Não duplique regra de autenticação nos controllers.

## Provider local

O provider local deve manter o fluxo atual para desenvolvimento/testes.

Regras:

1. Não usar provider local em produção.
2. Falhar no startup se `app.auth.provider=local` e profile ativo for `prod`, a menos que exista flag explícita de emergência documentada.
3. Não depender de senha local quando `app.auth.provider=cognito`.

## Provider Cognito

Crie algo equivalente a:

```java
@Service
public class CognitoAuthProviderStrategy implements AuthProviderStrategy {

    private final CognitoIdentityProviderClient cognito;
    private final CognitoProperties properties;
    private final CognitoSecretHashService secretHashService;

    @Override
    public AuthLoginResult login(LoginRequest request) {
        Map<String, String> params = new HashMap<>();
        params.put("USERNAME", request.usernameOrEmail());
        params.put("PASSWORD", request.password());

        secretHashService.addSecretHashIfNeeded(params, request.usernameOrEmail());

        AdminInitiateAuthResponse response = cognito.adminInitiateAuth(AdminInitiateAuthRequest.builder()
            .userPoolId(properties.userPoolId())
            .clientId(properties.appClientId())
            .authFlow(AuthFlowType.ADMIN_USER_PASSWORD_AUTH)
            .authParameters(params)
            .build());

        // Tratar challenges quando existirem.
        // Rollout inicial é sem MFA, mas NEW_PASSWORD_REQUIRED pode ocorrer em migração/reset.
        // Buscar dados do usuário e grupos.
    }
}
```

Configuração necessária no App Client do Cognito:

```text
ALLOW_ADMIN_USER_PASSWORD_AUTH
ALLOW_REFRESH_TOKEN_AUTH
```

Se o App Client tiver client secret, calcule `SECRET_HASH`:

```java
public String secretHash(String username) {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(clientSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    mac.update(username.getBytes(StandardCharsets.UTF_8));
    byte[] raw = mac.doFinal(clientId.getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(raw);
}
```

O fluxo inicial fica sem MFA. Mesmo assim, trate respostas inesperadas de challenge com erro claro e auditável.

### Claims e dados mínimos do Cognito

No login, obtenha:

```text
cognitoSub
username
email
email_verified quando disponível
enabled/status quando disponível
groups
accessToken
idToken se necessário apenas para perfil
refreshToken
expiresIn
```

Use `sub` como identificador estável local. Não use email como chave primária definitiva, pois email pode mudar.

## SC_SESSION

Manter `SC_SESSION` em produção, mas a sessão deve guardar dados suficientes para refresh/logout Cognito.

Dados mínimos:

```text
sessionId opaco
cognitoSub
username/email
refreshToken cifrado
snapshot de grupos
versão/hash de sincronização
createdAt
updatedAt
expiresAt
revokedAt
```

Regras:

1. O frontend não deve receber refresh token Cognito.
2. Preferir cookie `SC_SESSION` com:
   - `HttpOnly`
   - `Secure` em produção
   - `SameSite=Lax` ou `Strict`, conforme compatibilidade do frontend
   - path restrito quando possível
3. Se o projeto já usa `SC_SESSION`, preserve nome e contrato.
4. Se o `SC_SESSION` atual for stateless, não coloque refresh token dentro dele. Use sessão server-side em banco/Redis.
5. Criptografe o refresh token em repouso:
   - preferencial: AWS KMS/Secrets Manager + AES-GCM;
   - alternativa aceitável: chave simétrica em secret de produção;
   - nunca texto puro.

## JWT interno da aplicação

O plano preserva o JWT curto interno para não quebrar o frontend atual.

Regras:

1. Continuar emitindo o JWT interno após login e refresh.
2. O JWT interno deve conter apenas dados necessários:
   - subject local ou cognitoSub;
   - userId local;
   - username/email;
   - roles/authorities locais sincronizadas;
   - exp curto.
3. Não coloque refresh token no JWT.
4. Não use roles vindas do frontend.
5. `JwtAuthFilter` deve continuar validando o JWT interno.
6. `CustomUserDetailsService` deve carregar authorities pela projeção local sincronizada, sem depender de senha local em produção.
7. Enquanto o JWT interno existir, manter `JWT_EC_PRIVATE_KEY` e `JWT_EC_PUBLIC_KEY` em produção.

## Sincronização de identidade local

Adicione campo estável:

```text
usuario.cognito_sub
```

Se ainda não existir, crie migration.

Regras:

1. `cognito_sub` deve ser único quando presente.
2. Email também pode ser único se o sistema já exigir, mas não deve substituir `cognito_sub`.
3. No primeiro login de usuário migrado:
   - localizar por `cognito_sub`;
   - se não houver, localizar por email;
   - gravar `cognito_sub`;
   - atualizar dados básicos;
   - sincronizar roles.
4. Se houver conflito de email/sub, falhar com erro administrativo e log de auditoria.
5. Não criar usuário fantasma com permissões se o Cognito não tiver grupo válido.

Exemplo de migration:

```sql
ALTER TABLE usuario ADD COLUMN cognito_sub VARCHAR(80);
CREATE UNIQUE INDEX uk_usuario_cognito_sub ON usuario (cognito_sub);
```

Ajuste nomes de tabela/coluna conforme o projeto.

## Sincronização de grupos para roles locais

Cognito Groups é fonte oficial de membership. Banco local é projeção.

Fluxo:

```text
Cognito groups
  -> normalização de nomes
  -> roles locais
  -> usuario_roles
  -> permissions locais
  -> authorities no JWT interno
```

Crie serviço equivalente:

```java
@Service
public class CognitoRoleSyncService {
    public RoleSyncResult syncMemberships(Usuario usuario, Collection<String> cognitoGroups) {
        // 1. remover prefixo configurável, se existir
        // 2. normalizar nome: ADMIN, CONTABIL, MANAGER, SUPPORT, CANDIDATO...
        // 3. validar se role existe no catálogo local
        // 4. substituir memberships locais por snapshot do Cognito
        // 5. persistir versão/hash da sincronização
        // 6. devolver authorities locais
    }
}
```

Regras:

1. Se `COGNITO_GROUP_PREFIX` existir, remova apenas esse prefixo.
2. Normalize para uppercase e padrão já usado pelo sistema.
3. Não crie permissões novas automaticamente em produção.
4. Se grupo não existir no catálogo local, registre alerta e ignore ou falhe conforme configuração. Para segurança, preferir falhar em produção quando grupo desconhecido vier com intenção de acesso.
5. Usuário sem grupo não acessa páginas protegidas.
6. Grupos mínimos esperados:
   - `ADMIN`
   - `CONTABIL`
   - `MANAGER`
   - `SUPPORT`
   - `CANDIDATO`
   - grupos de candidatos se hoje forem roles funcionais/filtros de dados.
7. Como o sistema usa role para filtro de dados, preserve a mesma normalização de nome dos grupos de candidato.

## Refresh

Endpoint atual `/api/v1/auth/refresh` deve:

```text
1. ler SC_SESSION
2. validar sessão local não revogada
3. descriptografar refreshToken Cognito
4. chamar Cognito refresh
5. se necessário, consultar grupos atuais
6. sincronizar projeção local quando houver mudança relevante
7. emitir novo JWT interno curto
8. manter o contrato atual da resposta
```

Use `AdminInitiateAuth` ou `InitiateAuth` com `REFRESH_TOKEN_AUTH`, conforme padrão adotado no projeto e permissões do App Client.

Parâmetros típicos:

```java
params.put("REFRESH_TOKEN", refreshToken);
secretHashService.addSecretHashIfNeeded(params, username);
```

Cenários:

1. Refresh token expirado: revogar SC_SESSION local e retornar 401.
2. Usuário desabilitado no Cognito: revogar SC_SESSION local e retornar 401/403.
3. Grupo removido entre logins: sincronizar e emitir JWT interno sem a role removida.
4. Falha temporária Cognito: retornar erro controlado, sem apagar sessão se não houver certeza de invalidação.

## Logout

Endpoint atual `/api/v1/auth/logout` deve:

```text
1. ler SC_SESSION
2. revogar sessão local
3. chamar Cognito GlobalSignOut/AdminUserGlobalSignOut quando aplicável
4. limpar cookie SC_SESSION
5. manter contrato atual do frontend
```

Se logout Cognito falhar por usuário já inválido/desabilitado, ainda limpe a sessão local.

## /me e /me/roles

Devem continuar funcionando pelo JWT interno/sessão atual.

Regras:

1. `/me` retorna o perfil local.
2. `/me/roles` retorna roles locais sincronizadas.
3. Não consultar Cognito em toda requisição comum.
4. Consultar Cognito apenas em login, refresh relevante, alteração administrativa ou job explícito de reconciliação.

## Admin de usuários em produção

Fluxos de `criar_usuario` e `atualizar_usuario` devem administrar Cognito quando `app.auth.provider=cognito`.

Criar usuário:

```text
1. criar usuário no User Pool
2. definir/resetar senha conforme regra do rollout
3. atribuir/remover grupos no Cognito
4. obter/gravar cognito_sub
5. refletir usuário e memberships no banco local
6. auditar operação
```

Atualizar usuário:

```text
1. atualizar atributos permitidos no Cognito
2. atualizar grupos no Cognito
3. sincronizar projeção local
4. manter vínculos de domínio no banco
5. auditar operação
```

Operações Cognito esperadas:

```text
AdminCreateUser
AdminSetUserPassword
AdminUpdateUserAttributes
AdminDisableUser
AdminEnableUser
AdminAddUserToGroup
AdminRemoveUserFromGroup
AdminListGroupsForUser
AdminGetUser
```

Não permitir que tela administrativa altere roles apenas no banco local em produção. Em produção, banco local é projeção.

## Migração dos usuários atuais

Codex deve preparar scripts/serviços seguros para:

```text
1. exportar usuários locais
2. criar contas no Cognito
3. atribuir grupos conforme usuario_roles
4. gravar cognito_sub no banco local
5. invalidar login local após a virada
6. criar admin bootstrap no Cognito antes do corte
```

Não tente migrar senha em texto puro. Se o sistema atual só tem hash local, use fluxo de senha temporária/reset de senha no Cognito.

## Bootstrap de produção

1. Não criar admin local com `DEFAULT_ADMIN_PASSWORD` quando provider for `cognito`.
2. Exigir existência de admin bootstrap no Cognito antes do corte.
3. Validar no startup:
   - `APP_AUTH_PROVIDER=cognito`;
   - `AWS_REGION`;
   - `COGNITO_USER_POOL_ID`;
   - `COGNITO_APP_CLIENT_ID`;
   - chaves do JWT interno enquanto ele existir;
   - configuração de criptografia do refresh token;
   - profile de produção não permite login local.

## SecurityConfig, JwtAuthFilter e UserDetails

Preserve o modelo atual se ele valida JWT interno.

Pontos obrigatórios:

1. `SecurityConfig` continua protegendo rotas por JWT interno.
2. `JwtAuthFilter` não deve aceitar token Cognito diretamente como bearer principal neste rollout, salvo se o projeto explicitamente decidir suportar os dois.
3. `CustomUserDetailsService` carrega usuário local por id/sub/email conforme claim do JWT interno.
4. Authorities vêm da projeção local sincronizada.
5. `@PreAuthorize` e regras existentes continuam funcionando.
6. Não confiar em role enviada por request.

## Segurança contra pentest

Implemente ou preserve:

1. Rate limit no login e refresh.
2. Resposta genérica para login inválido, evitando enumeração de usuário.
3. Logs de auditoria sem senha/token.
4. SC_SESSION seguro.
5. Refresh token cifrado.
6. Não expor Cognito access/id/refresh tokens ao frontend se o contrato atual não exige.
7. Validação de provider no profile de produção.
8. Usuário sem grupo não acessa rotas protegidas.
9. Remoção de grupo no Cognito revoga role no próximo login/refresh relevante.
10. Operações administrativas só para usuários autorizados.
11. Falha segura em inconsistência Cognito x banco local.
12. CORS restrito.
13. CSRF avaliado conforme uso de cookies. Se o JWT interno é enviado no header Authorization e SC_SESSION só é usado em endpoints auth, restrinja escopo e valide origem.

## Testes obrigatórios

Criar ou ajustar testes para:

```text
SecurityConfig
AuthController
AuthService
CognitoAuthProviderStrategy
LocalAuthProviderStrategy
JwtAuthFilter
CustomUserDetailsService
CognitoRoleSyncService
Usuario admin service/controller
```

Cenários:

1. Login Cognito válido gera:
   - SC_SESSION;
   - JWT interno;
   - authorities corretas;
   - usuário local sincronizado.
2. Login inválido retorna 401 sem vazar detalhe.
3. Refresh válido renova JWT interno sem novo login.
4. Refresh token expirado revoga sessão local e retorna 401.
5. Logout revoga sessão local e tenta encerrar Cognito.
6. Usuário sem grupo não acessa páginas protegidas.
7. ADMIN acessa rotas admin secretas.
8. CONTABIL mantém restrições atuais.
9. Criar usuário em produção cria Cognito user e reflete localmente.
10. Atualizar usuário em produção altera grupos no Cognito e reflete localmente.
11. Primeiro login de usuário migrado grava `cognito_sub`.
12. Grupo removido entre logins remove role local no refresh/login.
13. Usuário desabilitado no Cognito não autentica/refresh.
14. Inconsistência Cognito x projeção local gera falha segura e log/auditoria.
15. Provider `local` bloqueado em profile `prod`.

Use mocks do Cognito. Não chamar AWS real nos testes padrão.

## Critérios de aceite

O trabalho só está completo quando:

```text
[ ] Produção usa Cognito como provedor único de autenticação.
[ ] Tela /login atual foi mantida.
[ ] Endpoints /api/v1/auth/login, /refresh, /logout, /me e /me/roles mantêm contrato atual.
[ ] Login válido gera SC_SESSION, JWT interno e authorities corretas.
[ ] Refresh usa sessão Cognito e emite novo JWT interno.
[ ] Logout limpa sessão local e tenta encerrar sessão Cognito.
[ ] Usuário sem grupo não acessa páginas protegidas.
[ ] ADMIN mantém acesso às rotas admin.
[ ] CONTABIL mantém restrições atuais.
[ ] Criar/atualizar usuário em produção administra Cognito e sincroniza banco.
[ ] Primeiro login migrado sincroniza cognito_sub e memberships.
[ ] Login local não funciona em produção.
[ ] Refresh token Cognito não é exposto ao frontend.
[ ] Refresh token é cifrado em repouso.
[ ] IAM Role é usada, sem access key fixa.
[ ] Testes cobrem sucesso, falha, refresh expirado, usuário desabilitado, grupo removido e inconsistência.
```

## O que não fazer

1. Não trocar a tela atual por Hosted UI neste rollout.
2. Não remover o JWT interno se isso quebrar o frontend.
3. Não usar ID Token como autorização principal da API.
4. Não deixar senha local ativa em produção.
5. Não armazenar refresh token em texto puro.
6. Não salvar AWS access key/secret key no repositório.
7. Não confiar em roles do frontend.
8. Não criar permissões detalhadas automaticamente a partir do Cognito.
9. Não consultar Cognito em todas as requisições comuns.
10. Não ignorar conflitos entre Cognito e banco local.

## Ordem recomendada de implementação

1. Adicionar configuração `app.auth.provider`.
2. Criar `AuthProviderStrategy` e resolver.
3. Isolar provider local existente.
4. Criar `CognitoProperties`.
5. Configurar `CognitoIdentityProviderClient`.
6. Criar `CognitoSecretHashService`.
7. Criar `CognitoAuthProviderStrategy`.
8. Criar/ajustar armazenamento de `SC_SESSION`.
9. Criar criptografia do refresh token.
10. Adicionar `cognito_sub` no usuário local.
11. Criar `CognitoIdentitySyncService`.
12. Criar `CognitoRoleSyncService`.
13. Adaptar `AuthService`.
14. Adaptar `/login`, `/refresh`, `/logout`, `/me`, `/me/roles` sem quebrar contrato.
15. Adaptar `CustomUserDetailsService` e `JwtAuthFilter`.
16. Adaptar admin de usuários para Cognito em produção.
17. Ajustar bootstrap de produção.
18. Criar testes.
19. Criar scripts/rotina de migração.
20. Revisar checklist de segurança.

## Resposta esperada do Codex ao usar esta skill

Ao implementar, explique em poucas linhas:

```text
- quais arquivos foram alterados;
- como o fluxo local e Cognito ficaram separados;
- como os grupos Cognito são sincronizados;
- como validar em produção;
- quais testes foram criados/ajustados.
```

Se o projeto não tiver alguma classe citada neste guia, adaptar ao nome equivalente existente sem inventar arquitetura paralela.
