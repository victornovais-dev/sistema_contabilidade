package com.sistema_contabilidade.item.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("ItemRazaoSocialSearchDatabaseSupport unit tests")
class ItemRazaoSocialSearchDatabaseSupportTest {

  @Mock private DataSource dataSource;
  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private Connection connection;
  @Mock private DatabaseMetaData databaseMetaData;

  @Test
  @DisplayName("Deve reconhecer banco MySQL e indice FULLTEXT existente")
  void deveReconhecerBancoMysqlEIndiceFullTextExistente() throws Exception {
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.getMetaData()).thenReturn(databaseMetaData);
    when(databaseMetaData.getDatabaseProductName()).thenReturn("MySQL");
    when(jdbcTemplate.queryForObject(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.eq(Integer.class),
            org.mockito.ArgumentMatchers.eq(
                ItemRazaoSocialSearchDatabaseSupport.FULLTEXT_INDEX_NAME)))
        .thenReturn(1);

    ItemRazaoSocialSearchDatabaseSupport support =
        new ItemRazaoSocialSearchDatabaseSupport(dataSource, jdbcTemplate);

    assertTrue(support.supportsFullTextSearch());
    assertTrue(support.supportsFullTextSearch());
  }

  @Test
  @DisplayName("Nao deve tentar criar indice quando banco nao for da familia MySQL")
  void naoDeveTentarCriarIndiceQuandoBancoNaoForDaFamiliaMysql() throws Exception {
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.getMetaData()).thenReturn(databaseMetaData);
    when(databaseMetaData.getDatabaseProductName()).thenReturn("PostgreSQL");

    ItemRazaoSocialSearchDatabaseSupport support =
        new ItemRazaoSocialSearchDatabaseSupport(dataSource, jdbcTemplate);

    assertFalse(support.supportsFullTextSearch());
    support.ensureFullTextIndex();
    verify(jdbcTemplate, never()).execute(org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  @DisplayName("Deve criar indice FULLTEXT quando banco suportar e indice ainda nao existir")
  void deveCriarIndiceFullTextQuandoBancoSuportarEIndiceAindaNaoExistir() throws Exception {
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.getMetaData()).thenReturn(databaseMetaData);
    when(databaseMetaData.getDatabaseProductName()).thenReturn("MariaDB");
    when(jdbcTemplate.queryForObject(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.eq(Integer.class),
            org.mockito.ArgumentMatchers.eq(
                ItemRazaoSocialSearchDatabaseSupport.FULLTEXT_INDEX_NAME)))
        .thenReturn(0);

    ItemRazaoSocialSearchDatabaseSupport support =
        new ItemRazaoSocialSearchDatabaseSupport(dataSource, jdbcTemplate);

    assertFalse(support.supportsFullTextSearch());
    support.ensureFullTextIndex();

    verify(jdbcTemplate).execute(org.mockito.ArgumentMatchers.contains("create fulltext index"));
  }

  @Test
  @DisplayName("Deve degradar para false quando nao conseguir ler metadados do banco")
  void deveDegradarParaFalseQuandoNaoConseguirLerMetadadosDoBanco() throws Exception {
    when(dataSource.getConnection()).thenThrow(new SQLException("offline"));

    ItemRazaoSocialSearchDatabaseSupport support =
        new ItemRazaoSocialSearchDatabaseSupport(dataSource, jdbcTemplate);

    assertFalse(support.supportsFullTextSearch());
    assertEquals(false, support.supportsFullTextSearch());
  }
}
