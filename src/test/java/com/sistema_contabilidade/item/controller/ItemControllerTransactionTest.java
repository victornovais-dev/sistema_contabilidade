package com.sistema_contabilidade.item.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sistema_contabilidade.item.dto.ItemArquivosUploadRequest;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;

@DisplayName("ItemController transaction tests")
class ItemControllerTransactionTest {

  private static final AnnotationTransactionAttributeSource TRANSACTION_ATTRIBUTES =
      new AnnotationTransactionAttributeSource();

  @ParameterizedTest(name = "{0} deve permitir roteamento para reader")
  @MethodSource("readOnlyAttachmentEndpoints")
  @DisplayName("Consultas de arquivos devem ser read-only")
  void consultasDeArquivosDevemSerReadOnly(String methodName, Class<?>[] parameterTypes)
      throws NoSuchMethodException {
    TransactionAttribute attribute = transactionAttribute(methodName, parameterTypes);

    assertNotNull(attribute);
    assertTrue(attribute.isReadOnly());
  }

  @Test
  @DisplayName("Upload de arquivos deve usar transacao writer")
  void uploadDeArquivosDeveUsarTransacaoWriter() throws NoSuchMethodException {
    TransactionAttribute attribute =
        transactionAttribute(
            "adicionarArquivos", Authentication.class, UUID.class, ItemArquivosUploadRequest.class);

    assertNotNull(attribute);
    assertFalse(attribute.isReadOnly());
  }

  private static Stream<Arguments> readOnlyAttachmentEndpoints() {
    return Stream.of(
        Arguments.of("baixarArquivo", new Class<?>[] {Authentication.class, UUID.class}),
        Arguments.of("listarArquivos", new Class<?>[] {Authentication.class, UUID.class}),
        Arguments.of(
            "baixarArquivoPorId", new Class<?>[] {Authentication.class, UUID.class, UUID.class}),
        Arguments.of("baixarTodosArquivos", new Class<?>[] {Authentication.class, UUID.class}));
  }

  private TransactionAttribute transactionAttribute(String methodName, Class<?>... parameterTypes)
      throws NoSuchMethodException {
    Method method = ItemController.class.getMethod(methodName, parameterTypes);
    return TRANSACTION_ATTRIBUTES.getTransactionAttribute(method, ItemController.class);
  }
}
