package com.sistema_contabilidade.item.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("ItemArquivoStorageService unit tests")
class ItemArquivoStorageServiceTest {

  @TempDir Path tempDir;

  @Test
  @DisplayName("Deve criar pasta e salvar arquivo PDF retornando caminho")
  void deveCriarPastaESalvarArquivoPdfRetornandoCaminho() throws IOException {
    String pastaArquivos = tempDir.resolve("uploads").resolve("itens").toString();
    ItemArquivoStorageService service = new ItemArquivoStorageService(pastaArquivos);
    byte[] conteudo = "pdf-test".getBytes();

    String caminhoSalvo = service.salvarPdf(conteudo);
    Path arquivoSalvo = Path.of(caminhoSalvo);

    assertTrue(Files.exists(arquivoSalvo));
    assertArrayEquals(conteudo, Files.readAllBytes(arquivoSalvo));
  }
}
