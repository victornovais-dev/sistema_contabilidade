package com.sistema_contabilidade.database.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sistema_contabilidade.auth.model.SessaoUsuario;
import com.sistema_contabilidade.duvida.model.Duvida;
import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.ItemArquivo;
import com.sistema_contabilidade.item.model.ItemDescricao;
import com.sistema_contabilidade.item.model.ItemParcelaPagamento;
import com.sistema_contabilidade.item.model.ItemParcelaPagamentoArquivo;
import com.sistema_contabilidade.item.model.ItemTipoDocumento;
import com.sistema_contabilidade.notificacao.model.Notificacao;
import com.sistema_contabilidade.rbac.model.Permissao;
import com.sistema_contabilidade.rbac.model.Role;
import com.sistema_contabilidade.usuario.model.Usuario;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Month;
import java.util.HashSet;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class NotificationReadModelMigrationTest {

  private static final String RECEITA_COM_DUPLICATAS = "00000000-0000-0000-0000-000000000001";
  private static final String RECEITA_SEM_NOTIFICACAO = "00000000-0000-0000-0000-000000000002";
  private static final String DESPESA = "00000000-0000-0000-0000-000000000003";
  private static final String ITEM_ORFAO = "00000000-0000-0000-0000-000000000004";
  private static final String RECEITA_CANDIDATO = "00000000-0000-0000-0000-000000000005";
  private static final String USUARIO_CANDIDATO = "10000000-0000-0000-0000-000000000005";
  private static final String ROLE_CANDIDATO = "20000000-0000-0000-0000-000000000005";
  private static final String ROLE_CANDIDATO_ESPECIFICA = "30000000-0000-0000-0000-000000000005";

  @Container
  private static final MySQLContainer MYSQL =
      new MySQLContainer("mysql:8.4")
          .withDatabaseName("sistema_contabilidade")
          .withUsername("test")
          .withPassword("test");

  @Test
  void shouldCreateBaselineAndMigrateNotificationReadModel() throws SQLException {
    migrateToVersionOne();
    assertBaselineTables();
    seedLegacyNotificationData();

    Flyway flyway = configureFlyway().load();
    flyway.migrate();

    assertThat(flyway.info().current().getVersion()).isEqualTo(MigrationVersion.fromVersion("6"));
    assertPublicQuestionTableExists();
    assertNotificationDataWasReconciled();
    assertNotificationConstraintsAreEnforced();
    assertHibernateMappingsMatchMigratedSchema();
  }

  private void migrateToVersionOne() {
    configureFlyway().target(MigrationVersion.fromVersion("1")).load().migrate();
  }

  private org.flywaydb.core.api.configuration.FluentConfiguration configureFlyway() {
    return Flyway.configure()
        .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
        .locations("classpath:db/migration")
        .cleanDisabled(true);
  }

  private void assertBaselineTables() throws SQLException {
    Set<String> tables = new HashSet<>();
    try (Connection connection = connection();
        PreparedStatement statement =
            connection.prepareStatement(
                "select table_name from information_schema.tables "
                    + "where table_schema = database() and table_type = 'BASE TABLE'");
        ResultSet resultSet = statement.executeQuery()) {
      while (resultSet.next()) {
        tables.add(resultSet.getString(1));
      }
    }

    assertThat(tables)
        .contains(
            "flyway_schema_history",
            "permissoes",
            "roles",
            "usuarios",
            "role_permissoes",
            "usuario_roles",
            "sessoes_usuario",
            "item_descricoes",
            "item_tipos_documento",
            "itens",
            "itens_arquivos",
            "itens_parcelas_pagamento",
            "itens_parcelas_pagamento_arquivos",
            "notificacoes");
  }

  private void assertPublicQuestionTableExists() throws SQLException {
    assertThat(
            queryCount(
                "select count(*) from information_schema.tables "
                    + "where table_schema = database() and table_name = 'duvidas_publicas'"))
        .isEqualTo(1);
    assertThat(
            queryCount(
                "select count(*) from information_schema.columns "
                    + "where table_schema = database() and table_name = 'duvidas_publicas' "
                    + "and column_name = 'status' and column_default = 'PENDENTE'"))
        .isEqualTo(1);
  }

  private void seedLegacyNotificationData() throws SQLException {
    execute(
        """
        insert into roles (id, nome)
        values
          (uuid_to_bin('%s'), 'CANDIDATO'),
          (uuid_to_bin('%s'), 'JOAO DA SILVA')
        """
            .formatted(ROLE_CANDIDATO, ROLE_CANDIDATO_ESPECIFICA),
        """
        insert into usuarios (id, email, nome, senha)
        values (uuid_to_bin('%s'), 'joao@email.com', 'Joao da Silva', 'senha')
        """
            .formatted(USUARIO_CANDIDATO),
        """
        insert into usuario_roles (usuario_id, role_id)
        values
          (uuid_to_bin('%s'), uuid_to_bin('%s')),
          (uuid_to_bin('%s'), uuid_to_bin('%s'))
        """
            .formatted(
                USUARIO_CANDIDATO, ROLE_CANDIDATO, USUARIO_CANDIDATO, ROLE_CANDIDATO_ESPECIFICA),
        """
        insert into itens
          (id, valor, data, horario_criacao, descricao, razao_social, role_nome, verificado, tipo)
        values
          (uuid_to_bin('%s'), 100.00, '2026-07-01', '2026-07-01 10:00:00',
           'Receita atualizada', 'Empresa Atualizada', ' contabil ', b'1', 'RECEITA'),
          (uuid_to_bin('%s'), 200.00, '2026-07-02', '2026-07-02 11:00:00',
           'Receita ausente', 'Empresa Nova', 'SUPPORT', b'0', 'RECEITA'),
          (uuid_to_bin('%s'), 300.00, '2026-07-03', '2026-07-03 12:00:00',
           'Despesa', 'Fornecedor', 'CONTABIL', b'0', 'DESPESA')
        """
            .formatted(RECEITA_COM_DUPLICATAS, RECEITA_SEM_NOTIFICACAO, DESPESA),
        """
        insert into itens
          (id, valor, data, horario_criacao, descricao, razao_social, role_nome, verificado, tipo,
           criado_por_id)
        values
          (uuid_to_bin('%s'), 400.00, '2026-07-04', '2026-07-04 13:00:00',
           'Receita de candidato', 'Empresa Candidata', 'CANDIDATO', b'0', 'RECEITA',
           uuid_to_bin('%s'))
        """
            .formatted(RECEITA_CANDIDATO, USUARIO_CANDIDATO),
        """
        insert into notificacoes
          (id, item_id, role_nome, descricao, razao_social_nome, valor, criado_em, limpa)
        values
          (uuid_to_bin('10000000-0000-0000-0000-000000000001'), uuid_to_bin('%s'),
           'ANTIGA', 'Desatualizada', 'Empresa Antiga', 1.00, '2025-01-01 00:00:00', b'0'),
          (uuid_to_bin('20000000-0000-0000-0000-000000000001'), uuid_to_bin('%s'),
           'ANTIGA', 'Duplicada', 'Empresa Antiga', 2.00, '2025-01-02 00:00:00', b'1'),
          (uuid_to_bin('30000000-0000-0000-0000-000000000001'), uuid_to_bin('%s'),
           'CONTABIL', 'Despesa inválida', 'Fornecedor', 300.00, '2026-07-03 12:00:00', b'0'),
          (uuid_to_bin('40000000-0000-0000-0000-000000000001'), uuid_to_bin('%s'),
           'CONTABIL', 'Órfã', 'Empresa removida', 400.00, '2026-07-04 12:00:00', b'0')
        """
            .formatted(RECEITA_COM_DUPLICATAS, RECEITA_COM_DUPLICATAS, DESPESA, ITEM_ORFAO));
  }

  private void assertNotificationDataWasReconciled() throws SQLException {
    assertThat(queryCount("select count(*) from notificacoes")).isEqualTo(3);

    try (Connection connection = connection();
        PreparedStatement statement =
            connection.prepareStatement(
                """
                select role_nome, descricao, razao_social_nome, valor, criado_em, limpa + 0
                from notificacoes
                where item_id = uuid_to_bin(?)
                """)) {
      statement.setString(1, RECEITA_COM_DUPLICATAS);
      try (ResultSet resultSet = statement.executeQuery()) {
        assertThat(resultSet.next()).isTrue();
        assertThat(resultSet.getString(1)).isEqualTo("CONTABIL");
        assertThat(resultSet.getString(2)).isEqualTo("Receita atualizada");
        assertThat(resultSet.getString(3)).isEqualTo("Empresa Atualizada");
        assertThat(resultSet.getBigDecimal(4)).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(resultSet.getTimestamp(5).toLocalDateTime())
            .isEqualTo(java.time.LocalDateTime.of(2026, Month.JULY, 1, 10, 0));
        assertThat(resultSet.getInt(6)).isEqualTo(1);
      }
    }

    assertThat(
            queryCount(
                "select count(*) from notificacoes where item_id = uuid_to_bin(?) and limpa = b'0'",
                RECEITA_SEM_NOTIFICACAO))
        .isEqualTo(1);
    assertThat(
            queryCount(
                "select count(*) from notificacoes where item_id in (uuid_to_bin(?), uuid_to_bin(?))",
                DESPESA,
                ITEM_ORFAO))
        .isZero();
    assertThat(
            queryCount(
                "select count(*) from itens where id = uuid_to_bin(?) and role_nome = 'JOAO DA SILVA'",
                RECEITA_CANDIDATO))
        .isEqualTo(1);
    assertThat(
            queryCount(
                """
                select count(*) from notificacoes
                where item_id = uuid_to_bin(?) and role_nome = 'JOAO DA SILVA'
                """,
                RECEITA_CANDIDATO))
        .isEqualTo(1);
  }

  private void assertNotificationConstraintsAreEnforced() {
    assertThatThrownBy(
            () ->
                execute(
                    """
                    insert into notificacoes
                      (id, item_id, role_nome, valor, criado_em, limpa)
                    values
                      (uuid_to_bin('50000000-0000-0000-0000-000000000001'), uuid_to_bin('%s'),
                       'CONTABIL', 100.00, '2026-07-01 10:00:00', b'0')
                    """
                        .formatted(RECEITA_COM_DUPLICATAS)))
        .isInstanceOf(SQLException.class);

    assertThatThrownBy(
            () ->
                execute(
                    """
                    insert into notificacoes
                      (id, item_id, role_nome, valor, criado_em, limpa)
                    values
                      (uuid_to_bin('60000000-0000-0000-0000-000000000001'), uuid_to_bin('%s'),
                       'CONTABIL', 100.00, '2026-07-01 10:00:00', b'0')
                    """
                        .formatted(ITEM_ORFAO)))
        .isInstanceOf(SQLException.class);
  }

  private void assertHibernateMappingsMatchMigratedSchema() {
    Configuration configuration =
        new Configuration()
            .addAnnotatedClass(SessaoUsuario.class)
            .addAnnotatedClass(Usuario.class)
            .addAnnotatedClass(Role.class)
            .addAnnotatedClass(Permissao.class)
            .addAnnotatedClass(Item.class)
            .addAnnotatedClass(ItemArquivo.class)
            .addAnnotatedClass(ItemDescricao.class)
            .addAnnotatedClass(ItemParcelaPagamento.class)
            .addAnnotatedClass(ItemParcelaPagamentoArquivo.class)
            .addAnnotatedClass(ItemTipoDocumento.class)
            .addAnnotatedClass(Notificacao.class)
            .addAnnotatedClass(Duvida.class)
            .setProperty("jakarta.persistence.jdbc.url", MYSQL.getJdbcUrl())
            .setProperty("jakarta.persistence.jdbc.user", MYSQL.getUsername())
            .setProperty("jakarta.persistence.jdbc.password", MYSQL.getPassword())
            .setProperty("jakarta.persistence.jdbc.driver", "com.mysql.cj.jdbc.Driver")
            .setProperty("hibernate.hbm2ddl.auto", "validate")
            .setProperty("hibernate.show_sql", "false");

    try (SessionFactory ignored = configuration.buildSessionFactory()) {
      assertThat(ignored.isOpen()).isTrue();
    }
  }

  private int queryCount(String sql, String... parameters) throws SQLException {
    try (Connection connection = connection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int index = 0; index < parameters.length; index++) {
        statement.setString(index + 1, parameters[index]);
      }
      try (ResultSet resultSet = statement.executeQuery()) {
        assertThat(resultSet.next()).isTrue();
        return resultSet.getInt(1);
      }
    }
  }

  private void execute(String... statements) throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      for (String sql : statements) {
        statement.executeUpdate(sql);
      }
    }
  }

  private Connection connection() throws SQLException {
    return DriverManager.getConnection(
        MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
  }
}
