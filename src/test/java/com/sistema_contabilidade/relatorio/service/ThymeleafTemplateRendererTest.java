package com.sistema_contabilidade.relatorio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@DisplayName("ThymeleafTemplateRenderer unit tests")
class ThymeleafTemplateRendererTest {

  @Test
  @DisplayName("Deve renderizar template com variavel informada")
  void deveRenderizarTemplateComVariavelInformada() {
    TemplateEngine templateEngine = Mockito.mock(TemplateEngine.class);
    ThymeleafTemplateRenderer renderer = new ThymeleafTemplateRenderer(templateEngine);
    Map<String, String> data = Map.of("empresa", "Sistema Contabilidade");
    ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);

    when(templateEngine.process(eq("relatorio-financeiro"), contextCaptor.capture()))
        .thenReturn("<html>ok</html>");

    String html = renderer.render("relatorio-financeiro", "report", data);

    assertEquals("<html>ok</html>", html);
    assertEquals(data, contextCaptor.getValue().getVariable("report"));
    verify(templateEngine).process(eq("relatorio-financeiro"), Mockito.any(Context.class));
  }
}
