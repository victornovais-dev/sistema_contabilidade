package com.sistema_contabilidade.relatorio.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Component
public class ThymeleafTemplateRenderer {

  private final TemplateEngine templateEngine;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "TemplateEngine is a Spring-managed shared bean used read-only here.")
  public ThymeleafTemplateRenderer(TemplateEngine templateEngine) {
    this.templateEngine = templateEngine;
  }

  public String render(String templateName, String variableName, Object data) {
    Context context = new Context();
    context.setVariable(variableName, data);
    return templateEngine.process(templateName, context);
  }

  public String render(
      String templateName, String variableName, Object data, Map<String, Object> extraVariables) {
    Context context = new Context();
    context.setVariable(variableName, data);
    if (extraVariables != null && !extraVariables.isEmpty()) {
      extraVariables.forEach(context::setVariable);
    }
    return templateEngine.process(templateName, context);
  }
}
