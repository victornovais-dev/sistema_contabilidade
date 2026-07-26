package com.sistema_contabilidade.notificacao.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;

@DisplayName("NotificacaoService transaction tests")
class NotificacaoServiceTransactionTest {

  private static final AnnotationTransactionAttributeSource TRANSACTION_ATTRIBUTES =
      new AnnotationTransactionAttributeSource();

  @ParameterizedTest(name = "{0} deve permitir roteamento para reader")
  @MethodSource("readOnlyNotificationMethods")
  @DisplayName("Consultas de notificacao devem ser read-only")
  void consultasDeNotificacaoDevemSerReadOnly(String methodName, Class<?>[] parameterTypes)
      throws NoSuchMethodException {
    TransactionAttribute attribute = transactionAttribute(methodName, parameterTypes);

    assertNotNull(attribute);
    assertTrue(attribute.isReadOnly());
  }

  private static Stream<Arguments> readOnlyNotificationMethods() {
    return Stream.of(
        Arguments.of("listar", new Class<?>[] {Authentication.class, String.class}),
        Arguments.of("listarRolesDisponiveis", new Class<?>[] {Authentication.class}));
  }

  private TransactionAttribute transactionAttribute(String methodName, Class<?>... parameterTypes)
      throws NoSuchMethodException {
    Method method = NotificacaoService.class.getMethod(methodName, parameterTypes);
    return TRANSACTION_ATTRIBUTES.getTransactionAttribute(method, NotificacaoService.class);
  }
}
