package com.sistema_contabilidade.item.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Locale;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ItemRazaoSocialSearchDatabaseSupport {

  public static final String FULLTEXT_INDEX_NAME = "ft_itens_razao_social_busca";

  private final DataSource dataSource;
  private final JdbcTemplate jdbcTemplate;

  private volatile Boolean mysqlCompatible;
  private volatile Boolean fullTextIndexAvailable;

  public boolean supportsFullTextSearch() {
    if (!isMysqlCompatible()) {
      return false;
    }

    Boolean cachedAvailability = fullTextIndexAvailable;
    if (cachedAvailability != null) {
      return cachedAvailability;
    }

    boolean available = hasFullTextIndex();
    fullTextIndexAvailable = available;
    return available;
  }

  public void ensureFullTextIndex() {
    if (!isMysqlCompatible() || supportsFullTextSearch()) {
      return;
    }

    try {
      jdbcTemplate.execute(
          "create fulltext index " + FULLTEXT_INDEX_NAME + " on itens (razao_social_busca)");
      fullTextIndexAvailable = true;
      log.info("Indice FULLTEXT {} criado para itens.razao_social_busca.", FULLTEXT_INDEX_NAME);
    } catch (RuntimeException exception) {
      fullTextIndexAvailable = false;
      log.warn(
          "Nao foi possivel criar o indice FULLTEXT {} para itens.razao_social_busca.",
          FULLTEXT_INDEX_NAME,
          exception);
    }
  }

  private boolean isMysqlCompatible() {
    Boolean cachedCompatibility = mysqlCompatible;
    if (cachedCompatibility != null) {
      return cachedCompatibility;
    }

    try (Connection connection = dataSource.getConnection()) {
      DatabaseMetaData metadata = connection.getMetaData();
      String productName = metadata.getDatabaseProductName();
      boolean compatible =
          productName != null
              && productName.toLowerCase(Locale.ROOT).matches(".*(mysql|mariadb).*");
      mysqlCompatible = compatible;
      return compatible;
    } catch (SQLException exception) {
      log.warn("Nao foi possivel determinar a compatibilidade FULLTEXT do banco.", exception);
      mysqlCompatible = false;
      return false;
    }
  }

  private boolean hasFullTextIndex() {
    try {
      Integer count =
          jdbcTemplate.queryForObject(
              """
              select count(*)
              from information_schema.statistics
              where table_schema = database()
                and table_name = 'itens'
                and index_name = ?
              """,
              Integer.class,
              FULLTEXT_INDEX_NAME);
      return count != null && count > 0;
    } catch (RuntimeException exception) {
      log.warn(
          "Nao foi possivel verificar a existencia do indice FULLTEXT {}.",
          FULLTEXT_INDEX_NAME,
          exception);
      return false;
    }
  }
}
