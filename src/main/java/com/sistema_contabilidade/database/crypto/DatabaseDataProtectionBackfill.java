package com.sistema_contabilidade.database.crypto;

import com.sistema_contabilidade.database.crypto.service.BlindIndexService;
import com.sistema_contabilidade.database.crypto.service.DatabaseCryptoService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "app.database.crypto-backfill-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class DatabaseDataProtectionBackfill implements ApplicationRunner {

  private static final String COGNITO_SUB_COLUMN = "cognito_sub";
  private static final String COGNITO_USERNAME_COLUMN = "cognito_username";
  private static final String COGNITO_GROUPS_HASH_COLUMN = "cognito_groups_hash";
  private static final String DOCUMENT_COLUMN = "cnpj_cpf";
  private static final String EMAIL_COLUMN = "email";
  private static final String FILE_PATH_COLUMN = "caminho_arquivo_pdf";
  private static final String NAME_COLUMN = "nome";
  private static final String PASSWORD_COLUMN = "senha";

  private static final List<ProtectedTable> ENCRYPTED_TABLES =
      List.of(
          new ProtectedTable(
              "sessoes_usuario",
              "id",
              List.of("auth_username", COGNITO_SUB_COLUMN, "groups_snapshot", "groups_hash")),
          new ProtectedTable(
              "duvidas_publicas", "id", List.of(NAME_COLUMN, EMAIL_COLUMN, "duvida")),
          new ProtectedTable(
              "itens",
              "id",
              List.of(
                  FILE_PATH_COLUMN,
                  "tipo_documento",
                  "numero_documento",
                  "razao_social",
                  "observacao")),
          new ProtectedTable("itens_arquivos", "id", List.of(FILE_PATH_COLUMN)),
          new ProtectedTable("itens_parcelas_pagamento", "id", List.of(FILE_PATH_COLUMN)),
          new ProtectedTable("itens_parcelas_pagamento_arquivos", "id", List.of(FILE_PATH_COLUMN)),
          new ProtectedTable("notificacoes", "id", List.of("descricao", "razao_social_nome")));

  private static final ProtectedTable USERS_TABLE =
      new ProtectedTable(
          "usuarios",
          "id",
          List.of(
              NAME_COLUMN,
              EMAIL_COLUMN,
              PASSWORD_COLUMN,
              COGNITO_SUB_COLUMN,
              COGNITO_USERNAME_COLUMN,
              COGNITO_GROUPS_HASH_COLUMN,
              "email_bidx",
              "cognito_sub_bidx",
              "cognito_username_bidx"));
  private static final ProtectedTable ITEMS_TABLE =
      new ProtectedTable("itens", "id", List.of(DOCUMENT_COLUMN, "cnpj_cpf_bidx"));

  private final JdbcTemplate jdbcTemplate;
  private final DatabaseCryptoService cryptoService;
  private final BlindIndexService blindIndexService;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "JdbcTemplate is an injected Spring bean and is intentionally shared.")
  public DatabaseDataProtectionBackfill(
      JdbcTemplate jdbcTemplate,
      DatabaseCryptoService cryptoService,
      BlindIndexService blindIndexService) {
    this.jdbcTemplate = jdbcTemplate;
    this.cryptoService = cryptoService;
    this.blindIndexService = blindIndexService;
  }

  @Override
  public void run(ApplicationArguments arguments) {
    protectUsers();
    protectItemDocuments();
    ENCRYPTED_TABLES.forEach(this::protectTable);
  }

  private void protectUsers() {
    String sql =
        "select id, nome, email, senha, cognito_sub, cognito_username, cognito_groups_hash"
            + " from usuarios";
    for (Map<String, Object> row : jdbcTemplate.queryForList(sql)) {
      Map<String, String> protectedValues = new LinkedHashMap<>();
      protect(protectedValues, NAME_COLUMN, stringValue(row.get(NAME_COLUMN)));
      protect(protectedValues, EMAIL_COLUMN, stringValue(row.get(EMAIL_COLUMN)));
      protect(protectedValues, PASSWORD_COLUMN, stringValue(row.get(PASSWORD_COLUMN)));
      protect(protectedValues, COGNITO_SUB_COLUMN, stringValue(row.get(COGNITO_SUB_COLUMN)));
      protect(
          protectedValues, COGNITO_USERNAME_COLUMN, stringValue(row.get(COGNITO_USERNAME_COLUMN)));
      protect(
          protectedValues,
          COGNITO_GROUPS_HASH_COLUMN,
          stringValue(row.get(COGNITO_GROUPS_HASH_COLUMN)));

      String email = cryptoService.decrypt(stringValue(row.get(EMAIL_COLUMN)));
      String cognitoSub = cryptoService.decrypt(stringValue(row.get(COGNITO_SUB_COLUMN)));
      String cognitoUsername = cryptoService.decrypt(stringValue(row.get(COGNITO_USERNAME_COLUMN)));
      protectedValues.put("email_bidx", blindIndexService.email(email));
      protectedValues.put("cognito_sub_bidx", blindIndexService.cognitoSub(cognitoSub));
      protectedValues.put(
          "cognito_username_bidx", blindIndexService.cognitoUsername(cognitoUsername));
      updateRow(USERS_TABLE, row.get("id"), protectedValues);
    }
  }

  private void protectItemDocuments() {
    String sql = "select id, cnpj_cpf from itens";
    for (Map<String, Object> row : jdbcTemplate.queryForList(sql)) {
      String storedDocument = stringValue(row.get(DOCUMENT_COLUMN));
      Map<String, String> protectedValues = new LinkedHashMap<>();
      protect(protectedValues, DOCUMENT_COLUMN, storedDocument);
      protectedValues.put(
          "cnpj_cpf_bidx", blindIndexService.document(cryptoService.decrypt(storedDocument)));
      updateRow(ITEMS_TABLE, row.get("id"), protectedValues);
    }
  }

  @SuppressWarnings("java:S2077")
  private void protectTable(ProtectedTable table) {
    String sql =
        "select "
            + table.idColumn()
            + ", "
            + String.join(", ", table.columns())
            + " from "
            + table.tableName();
    for (Map<String, Object> row : jdbcTemplate.queryForList(sql)) {
      Map<String, String> protectedValues = new LinkedHashMap<>();
      for (String column : table.columns()) {
        protect(protectedValues, column, stringValue(row.get(column)));
      }
      if (!protectedValues.isEmpty()) {
        updateRow(table, row.get(table.idColumn()), protectedValues);
      }
    }
  }

  private void protect(Map<String, String> target, String column, String value) {
    if (value != null && !cryptoService.isEncrypted(value)) {
      target.put(column, cryptoService.encrypt(value));
    }
  }

  @SuppressWarnings("java:S2077")
  private void updateRow(ProtectedTable table, Object id, Map<String, String> protectedValues) {
    if (protectedValues.isEmpty()) {
      return;
    }
    if (!table.columns().containsAll(protectedValues.keySet())) {
      throw new IllegalArgumentException("Coluna de backfill nao permitida");
    }
    String assignments =
        protectedValues.keySet().stream()
            .map(column -> column + " = ?")
            .collect(java.util.stream.Collectors.joining(", "));
    Object[] parameters = new Object[protectedValues.size() + 1];
    int index = 0;
    for (String value : protectedValues.values()) {
      parameters[index] = value;
      index++;
    }
    parameters[index] = id;
    jdbcTemplate.update(
        "update "
            + table.tableName()
            + " set "
            + assignments
            + " where "
            + table.idColumn()
            + " = ?",
        parameters);
  }

  private String stringValue(Object value) {
    return value == null ? null : value.toString();
  }

  private record ProtectedTable(String tableName, String idColumn, List<String> columns) {
    private ProtectedTable {
      if (!isSqlIdentifier(tableName)
          || !isSqlIdentifier(idColumn)
          || columns.isEmpty()
          || columns.stream().anyMatch(column -> !isSqlIdentifier(column))) {
        throw new IllegalArgumentException("Identificador SQL de backfill invalido");
      }
      columns = List.copyOf(columns);
    }

    private static boolean isSqlIdentifier(String value) {
      return value != null && value.matches("[a-z_]+$");
    }
  }
}
