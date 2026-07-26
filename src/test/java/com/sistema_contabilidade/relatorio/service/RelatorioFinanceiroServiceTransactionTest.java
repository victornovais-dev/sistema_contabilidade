package com.sistema_contabilidade.relatorio.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sistema_contabilidade.relatorio.dto.RelatorioFinanceiroPdfData;
import com.sistema_contabilidade.relatorio.dto.RelatorioFinanceiroResponse;
import java.lang.reflect.Method;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;

@DisplayName("RelatorioFinanceiroService transaction tests")
class RelatorioFinanceiroServiceTransactionTest {

  private static final AnnotationTransactionAttributeSource TRANSACTION_ATTRIBUTES =
      new AnnotationTransactionAttributeSource();

  @ParameterizedTest(name = "{0} deve permitir roteamento para reader")
  @MethodSource("readOnlyReportMethods")
  @DisplayName("Consultas do relatorio devem ser read-only")
  void consultasDoRelatorioDevemSerReadOnly(String methodName, Class<?>[] parameterTypes)
      throws NoSuchMethodException {
    TransactionAttribute attribute = transactionAttribute(methodName, parameterTypes);

    assertNotNull(attribute);
    assertTrue(attribute.isReadOnly());
  }

  @Test
  @DisplayName("Renderizacao Playwright deve executar fora de transacao")
  void renderizacaoPlaywrightDeveExecutarForaDeTransacao() throws NoSuchMethodException {
    TransactionAttribute attribute =
        transactionAttribute("gerarPdf", RelatorioFinanceiroPdfData.class);

    assertNull(attribute);
  }

  private static Stream<Arguments> readOnlyReportMethods() {
    return Stream.of(
        Arguments.of("gerar", new Class<?>[] {Authentication.class}),
        Arguments.of("gerar", new Class<?>[] {Authentication.class, String.class}),
        Arguments.of("gerarResumo", new Class<?>[] {Authentication.class}),
        Arguments.of("gerarResumo", new Class<?>[] {Authentication.class, String.class}),
        Arguments.of("listarRolesDisponiveis", new Class<?>[] {Authentication.class}),
        Arguments.of(
            "prepararDadosPdf",
            new Class<?>[] {Authentication.class, RelatorioFinanceiroResponse.class}));
  }

  private TransactionAttribute transactionAttribute(String methodName, Class<?>... parameterTypes)
      throws NoSuchMethodException {
    Method method = RelatorioFinanceiroService.class.getMethod(methodName, parameterTypes);
    return TRANSACTION_ATTRIBUTES.getTransactionAttribute(method, RelatorioFinanceiroService.class);
  }
}
