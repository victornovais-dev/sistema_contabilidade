package com.sistema_contabilidade.monitoring.health;

import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.jdbc.health.DataSourceHealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AlbHealthConfiguration {

  @Bean
  HealthIndicator writerHealthIndicator(
      @Qualifier("writerDataSource") ObjectProvider<DataSource> writerDataSourceProvider,
      DataSource dataSource) {
    DataSource writerDataSource = selectWriterDataSource(writerDataSourceProvider, dataSource);
    return new DataSourceHealthIndicator(writerDataSource);
  }

  static DataSource selectWriterDataSource(
      ObjectProvider<DataSource> writerDataSourceProvider, DataSource fallbackDataSource) {
    DataSource writerDataSource = writerDataSourceProvider.getIfAvailable();
    return writerDataSource != null ? writerDataSource : fallbackDataSource;
  }
}
