package com.sistema_contabilidade.database.config;

import com.sistema_contabilidade.database.routing.ReadWriteRoutingDataSource;
import com.sistema_contabilidade.database.routing.ReaderFallbackDataSource;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.database.routing.enabled", havingValue = "true")
public class ReadWriteDataSourceConfig {

  @Bean
  @Primary
  @ConfigurationProperties("app.database.writer")
  DataSourceProperties writerDataSourceProperties() {
    return new DataSourceProperties();
  }

  @Bean
  @ConfigurationProperties("app.database.reader")
  DataSourceProperties readerDataSourceProperties() {
    return new DataSourceProperties();
  }

  @Bean
  @ConfigurationProperties("app.database.writer.configuration")
  HikariDataSource writerDataSource(
      @Qualifier("writerDataSourceProperties") DataSourceProperties properties) {
    return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
  }

  @Bean
  @ConfigurationProperties("app.database.reader.configuration")
  HikariDataSource rawReaderDataSource(
      @Qualifier("readerDataSourceProperties") DataSourceProperties properties) {
    HikariDataSource dataSource =
        properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    dataSource.setReadOnly(true);
    return dataSource;
  }

  @Bean
  ReaderFallbackDataSource readerFallbackDataSource(
      @Qualifier("rawReaderDataSource") DataSource readerDataSource,
      @Qualifier("writerDataSource") DataSource writerDataSource,
      MeterRegistry meterRegistry) {
    return new ReaderFallbackDataSource(readerDataSource, writerDataSource, meterRegistry);
  }

  @Bean
  ReadWriteRoutingDataSource routingDataSource(
      @Qualifier("writerDataSource") DataSource writerDataSource,
      ReaderFallbackDataSource readerFallbackDataSource,
      MeterRegistry meterRegistry) {
    return new ReadWriteRoutingDataSource(
        writerDataSource, readerFallbackDataSource, meterRegistry);
  }

  @Bean
  @Primary
  DataSource dataSource(ReadWriteRoutingDataSource routingDataSource) {
    return new LazyConnectionDataSourceProxy(routingDataSource);
  }
}
