# Guia complementar: implementação do provider Cognito

## Classes sugeridas

```text
config/
  CognitoProperties.java
  AwsCognitoConfig.java

auth/
  AuthProvider.java
  AuthProviderStrategy.java
  AuthProviderStrategyResolver.java
  LocalAuthProviderStrategy.java
  CognitoAuthProviderStrategy.java
  CognitoSecretHashService.java
  ScSessionService.java
  RefreshTokenCryptoService.java
  CognitoIdentitySyncService.java
  CognitoRoleSyncService.java
```

## `CognitoProperties`

```java
@ConfigurationProperties(prefix = "aws.cognito")
public record CognitoProperties(
    String userPoolId,
    String appClientId,
    String appClientSecret,
    String groupPrefix,
    String usernameAttribute
) {
    public boolean hasClientSecret() {
        return appClientSecret != null && !appClientSecret.isBlank();
    }
}
```

## AWS client

```java
@Configuration
@EnableConfigurationProperties(CognitoProperties.class)
public class AwsCognitoConfig {

    @Bean
    CognitoIdentityProviderClient cognitoIdentityProviderClient(
            @Value("${aws.region}") String region
    ) {
        return CognitoIdentityProviderClient.builder()
            .region(Region.of(region))
            .build();
    }
}
```

A SDK deve usar a cadeia padrão de credenciais. Em produção, isso significa IAM Role da instância, ECS Task Role, EKS IRSA ou equivalente.

## Tratamento de exceções Cognito

Mapeie exceções para respostas seguras:

```text
NotAuthorizedException -> 401 genérico
UserNotFoundException -> 401 genérico
UserNotConfirmedException -> 403 com ação controlada se o fluxo suportar confirmação
PasswordResetRequiredException -> 403/409 com código interno para reset
TooManyRequestsException -> 429
UserNotEnabled/disabled -> 403
InvalidParameterException -> 400 quando for erro do admin, 401 genérico no login
```

Nunca retorne mensagem bruta da AWS para o frontend em login.

## Grupos e normalização

Exemplo:

```java
public String normalizeGroupToRole(String group) {
    String value = group == null ? "" : group.trim();

    if (groupPrefix != null && !groupPrefix.isBlank() && value.startsWith(groupPrefix)) {
        value = value.substring(groupPrefix.length());
    }

    value = value.replaceFirst("^ROLE_", "");
    value = value.replaceAll("[^A-Za-z0-9_]", "_");
    return value.toUpperCase(Locale.ROOT);
}
```

Se a aplicação já usa `ROLE_ADMIN`, converter no limite correto:

```text
grupo Cognito: ADMIN
role local: ADMIN ou ROLE_ADMIN conforme modelo atual
authority Spring: ROLE_ADMIN
```

## Regras de transação

A sincronização local deve ser transacional:

```text
1. localizar/criar usuário local
2. gravar cognito_sub
3. atualizar dados básicos
4. substituir usuario_roles conforme snapshot Cognito
5. gravar versão/hash
6. emitir JWT interno somente depois de commit ou com dados persistidos consistentes
```

Se a transação falhar, não emitir JWT interno com roles parcialmente sincronizadas.
