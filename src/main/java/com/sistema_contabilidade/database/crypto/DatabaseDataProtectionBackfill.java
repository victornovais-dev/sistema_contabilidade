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
  private static final String EMAIL_COLUMN = "email";
  private static final String FILE_PATH_COLUMN = "caminho_arquivo_pdf";

  private static final List<TableColumns> ENCRYPTED_TABLES =
      List.of(
          new TableColumns(
              "sessoes_usuario",
              "id",
              List.of("auth_username", COGNITO_SUB_COLUMN, "groups_snapshot", "groups_hash")),
          new TableColumns("duvidas_publicas", "id", List.of("nome", EMAIL_COLUMN, "duvida")),
          new TableColumns(
              "itens",
              "id",
              List.of(
                  FILE_PATH_COLUMN,
                  "tipo_documento",
                  "numero_documento",
                  "razao_social",
                  "observacao")),
          new TableColumns("itens_arquivos", "id", List.of(FILE_PATH_COLUMN)),
          new TableColumns("itens_parcelas_pagamento", "id", List.of(FILE_PATH_COLUMN)),
          new TableColumns("itens_parcelas_pagamento_arquivos", "id", List.of(FILE_PATH_COLUMN)),
          new TableColumns("notificacoes", "id", List.of("descricao", "razao_social_nome")));

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
      protect(protectedValues, "nome", stringValue(row.get("nome")));
      protect(protectedValues, EMAIL_COLUMN, stringValue(row.get(EMAIL_COLUMN)));
      protect(protectedValues, "senha", stringValue(row.get("senha")));
      protect(protectedValues, COGNITO_SUB_COLUMN, stringValue(row.get(COGNITO_SUB_COLUMN)));
      protect(
          protectedValues, COGNITO_USERNAME_COLUMN, stringValue(row.get(COGNITO_USERNAME_COLUMN)));
      protect(protectedValues, "cognito_groups_hash", stringValue(row.get("cognito_groups_hash")));

      String email = cryptoService.decrypt(stringValue(row.get(EMAIL_COLUMN)));
      String cognitoSub = cryptoService.decrypt(stringValue(row.get(COGNITO_SUB_COLUMN)));
      String cognitoUsername = cryptoService.decrypt(stringValue(row.get(COGNITO_USERNAME_COLUMN)));
      protectedValues.put("email_bidx", blindIndexService.email(email));
      protectedValues.put("cognito_sub_bidx", blindIndexService.cognitoSub(cognitoSub));
      protectedValues.put(
          "cognito_username_bidx", blindIndexService.cognitoUsername(cognitoUsername));
      updateRow("usuarios", "id", row.get("id"), protectedValues);
    }
  }

  private void protectItemDocuments() {
    String sql = "select id, cnpj_cpf from itens";
    for (Map<String, Object> row : jdbcTemplate.queryForList(sql)) {
      String storedDocument = stringValue(row.get("cnpj_cpf"));
      Map<String, String> protectedValues = new LinkedHashMap<>();
      protect(protectedValues, "cnpj_cpf", storedDocument);
      protectedValues.put(
          "cnpj_cpf_bidx", blindIndexService.document(cryptoService.decrypt(storedDocument)));
      updateRow("itens", "id", row.get("id"), protectedValues);
    }
  }

  private void protectTable(TableColumns table) {
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
        updateRow(table.tableName(), table.idColumn(), row.get(table.idColumn()), protectedValues);
      }
    }
  }

  private void protect(Map<String, String> target, String column, String value) {
    if (value != null && !cryptoService.isEncrypted(value)) {
      target.put(column, cryptoService.encrypt(value));
    }
  }

  private void updateRow(
      String tableName, String idColumn, Object id, Map<String, String> protectedValues) {
    if (protectedValues.isEmpty()) {
      return;
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
        "update " + tableName + " set " + assignments + " where " + idColumn + " = ?", parameters);
  }

  private String stringValue(Object value) {
    return value == null ? null : value.toString();
  }

  private record TableColumns(String tableName, String idColumn, List<String> columns) {}
}
