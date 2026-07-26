package com.sistema_contabilidade.database.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sistema_contabilidade.database.routing.ReadWriteRoutingDataSource;
import com.sistema_contabilidade.database.routing.ReaderFallbackDataSource;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

@DisplayName("ReadWriteDataSourceConfig unit tests")
class ReadWriteDataSourceConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
          .withUserConfiguration(ReadWriteDataSourceConfig.class)
          .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new);

  @Test
  @DisplayName("Deve manter configuracao desativada por default")
  void deveManterConfiguracaoDesativadaPorDefault() {
    contextRunner.run(context -> assertTrue(context.getBeansOfType(DataSource.class).isEmpty()));
  }

  @Test
  @DisplayName("Deve ativar e vincular propriedades dos dois pools")
  void deveAtivarEVincularPropriedadesDosDoisPools() {
    contextRunner
        .withPropertyValues(
            "app.database.routing.enabled=true",
            "app.database.writer.url=jdbc:mysql://writer.example/database",
            "app.database.writer.username=user",
            "app.database.writer.password=password",
            "app.database.writer.driver-class-name=com.mysql.cj.jdbc.Driver",
            "app.database.writer.configuration.maximum-pool-size=12",
            "app.database.reader.url=jdbc:mysql://reader.example/database",
            "app.database.reader.username=user",
            "app.database.reader.password=password",
            "app.database.reader.driver-class-name=com.mysql.cj.jdbc.Driver",
            "app.database.reader.configuration.maximum-pool-size=24")
        .run(
            context -> {
              assertTrue(context.isRunning());
              HikariDataSource writer = context.getBean("writerDataSource", HikariDataSource.class);
              HikariDataSource reader =
                  context.getBean("rawReaderDataSource", HikariDataSource.class);
              assertEquals(12, writer.getMaximumPoolSize());
              assertEquals(24, reader.getMaximumPoolSize());
              assertTrue(reader.isReadOnly());
              assertInstanceOf(
                  LazyConnectionDataSourceProxy.class, context.getBean(DataSource.class));
            });
  }

  @Test
  @DisplayName("Deve montar pools, fallback, routing e proxy lazy")
  void deveMontarPoolsFallbackRoutingEProxyLazy() {
    ReadWriteDataSourceConfig config = new ReadWriteDataSourceConfig();
    DataSourceProperties writerProperties = properties("jdbc:mysql://writer.example/database");
    DataSourceProperties readerProperties = properties("jdbc:mysql://reader.example/database");
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    try (HikariDataSource writer = config.writerDataSource(writerProperties);
        HikariDataSource reader = config.rawReaderDataSource(readerProperties)) {
      ReaderFallbackDataSource fallback =
          config.readerFallbackDataSource(reader, writer, meterRegistry);
      ReadWriteRoutingDataSource routing =
          config.routingDataSource(writer, fallback, meterRegistry);
      DataSource primary = config.dataSource(routing);

      assertEquals("jdbc:mysql://writer.example/database", writer.getJdbcUrl());
      assertEquals("jdbc:mysql://reader.example/database", reader.getJdbcUrl());
      assertTrue(reader.isReadOnly());
      LazyConnectionDataSourceProxy lazy =
          assertInstanceOf(LazyConnectionDataSourceProxy.class, primary);
      assertSame(routing, lazy.getTargetDataSource());
    } finally {
      meterRegistry.close();
    }
  }

  private DataSourceProperties properties(String url) {
    DataSourceProperties properties = new DataSourceProperties();
    properties.setUrl(url);
    properties.setUsername("user");
    properties.setPassword("password");
    properties.setDriverClassName("com.mysql.cj.jdbc.Driver");
    return properties;
  }
}
