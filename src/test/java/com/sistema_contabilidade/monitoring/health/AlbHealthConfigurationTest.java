package com.sistema_contabilidade.monitoring.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

@DisplayName("ALB health configuration unit tests")
class AlbHealthConfigurationTest {

  @Test
  @DisplayName("Deve preferir datasource writer explicito")
  void devePreferirDatasourceWriterExplicito() {
    ObjectProvider<DataSource> writerProvider = provider();
    DataSource writerDataSource = mock(DataSource.class);
    DataSource fallbackDataSource = mock(DataSource.class);
    when(writerProvider.getIfAvailable()).thenReturn(writerDataSource);

    DataSource selected =
        AlbHealthConfiguration.selectWriterDataSource(writerProvider, fallbackDataSource);

    assertThat(selected).isSameAs(writerDataSource);
  }

  @Test
  @DisplayName("Deve usar datasource primario quando routing esta desabilitado")
  void deveUsarDatasourcePrimarioQuandoRoutingEstaDesabilitado() {
    ObjectProvider<DataSource> writerProvider = provider();
    DataSource fallbackDataSource = mock(DataSource.class);

    DataSource selected =
        AlbHealthConfiguration.selectWriterDataSource(writerProvider, fallbackDataSource);

    assertThat(selected).isSameAs(fallbackDataSource);
  }

  @SuppressWarnings("unchecked")
  private ObjectProvider<DataSource> provider() {
    return mock(ObjectProvider.class);
  }
}
