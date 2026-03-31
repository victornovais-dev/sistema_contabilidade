package com.sistema_contabilidade;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

@DisplayName("SistemaContabilidadeApplication tests")
class SistemaContabilidadeApplicationTest {

  @Test
  @DisplayName("Deve iniciar aplicacao sem erros")
  void deveIniciarAplicacaoSemErros() {
    try (ConfigurableApplicationContext context =
        new SpringApplicationBuilder(SistemaContabilidadeApplication.class)
            .run("--spring.main.banner-mode=off", "--server.port=0")) {
      assertNotNull(context);
    }
  }
}
