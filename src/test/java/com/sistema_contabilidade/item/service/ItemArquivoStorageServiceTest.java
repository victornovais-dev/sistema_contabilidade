package com.sistema_contabilidade.item.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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

  @Test
  @DisplayName("Deve salvar multiplos PDFs retornando caminhos")
  void deveSalvarMultiplosPdfsRetornandoCaminhos() throws IOException {
    String pastaArquivos = tempDir.resolve("uploads").resolve("itens").toString();
    ItemArquivoStorageService service = new ItemArquivoStorageService(pastaArquivos);
    byte[] conteudoA = "pdf-test-a".getBytes();
    byte[] conteudoB = "pdf-test-b".getBytes();

    List<String> caminhos = service.salvarPdfs(List.of(conteudoA, conteudoB));

    assertEquals(2, caminhos.size());
    assertArrayEquals(conteudoA, Files.readAllBytes(Path.of(caminhos.get(0))));
    assertArrayEquals(conteudoB, Files.readAllBytes(Path.of(caminhos.get(1))));
  }

  @Test
  @DisplayName("Deve retornar lista vazia ao salvar multiplos PDFs nulos ou vazios")
  void deveRetornarListaVaziaAoSalvarMultiplosPdfsNulosOuVazios() {
    String pastaArquivos = tempDir.resolve("uploads").resolve("itens").toString();
    ItemArquivoStorageService service = new ItemArquivoStorageService(pastaArquivos);

    assertTrue(service.salvarPdfs(null).isEmpty());
    assertTrue(service.salvarPdfs(List.of()).isEmpty());
  }

  @Test
  @DisplayName("Deve sanitizar nome do arquivo ao salvar PDF")
  void deveSanitizarNomeDoArquivoAoSalvarPdf() {
    String pastaArquivos = tempDir.resolve("uploads").resolve("itens").toString();
    ItemArquivoStorageService service = new ItemArquivoStorageService(pastaArquivos);
    byte[] conteudo = "pdf-test".getBytes();

    String caminhoSalvo = service.salvarPdf(conteudo, " relatorio-ca 2026 ");
    Path arquivoSalvo = Path.of(caminhoSalvo);

    assertTrue(Files.exists(arquivoSalvo));
    assertEquals("relatorio_ca_2026.pdf", arquivoSalvo.getFileName().toString());
  }

  @Test
  @DisplayName("Deve aplicar suffix quando arquivo ja existe")
  void deveAplicarSuffixQuandoArquivoJaExiste() {
    String pastaArquivos = tempDir.resolve("uploads").resolve("itens").toString();
    ItemArquivoStorageService service = new ItemArquivoStorageService(pastaArquivos);
    byte[] conteudo = "pdf-test".getBytes();

    String primeiro = service.salvarPdf(conteudo, "duplicado.pdf");
    String segundo = service.salvarPdf(conteudo, "duplicado.pdf");

    assertNotEquals(primeiro, segundo);
    assertTrue(Path.of(segundo).getFileName().toString().startsWith("duplicado_"));
    assertTrue(Path.of(segundo).getFileName().toString().endsWith(".pdf"));
  }

  @Test
  @DisplayName("Deve remover diretorios do nome informado")
  void deveRemoverDiretoriosDoNomeInformado() {
    String pastaArquivos = tempDir.resolve("uploads").resolve("itens").toString();
    ItemArquivoStorageService service = new ItemArquivoStorageService(pastaArquivos);
    byte[] conteudo = "pdf-test".getBytes();

    String caminhoSalvo = service.salvarPdf(conteudo, "pasta/subpasta/arquivo-final.pdf");

    assertEquals("arquivo_final.pdf", Path.of(caminhoSalvo).getFileName().toString());
  }

  @Test
  @DisplayName("Deve adicionar extensao pdf quando ausente")
  void deveAdicionarExtensaoPdfQuandoAusente() {
    String pastaArquivos = tempDir.resolve("uploads").resolve("itens").toString();
    ItemArquivoStorageService service = new ItemArquivoStorageService(pastaArquivos);
    byte[] conteudo = "pdf-test".getBytes();

    String caminhoSalvo = service.salvarPdf(conteudo, "arquivo");

    assertEquals("arquivo.pdf", Path.of(caminhoSalvo).getFileName().toString());
  }

  @Test
  @DisplayName("Deve truncar nome de arquivo muito longo")
  void deveTruncarNomeDeArquivoMuitoLongo() {
    String pastaArquivos = tempDir.resolve("uploads").resolve("itens").toString();
    ItemArquivoStorageService service = new ItemArquivoStorageService(pastaArquivos);
    byte[] conteudo = "pdf-test".getBytes();
    String nomeLongo = "a".repeat(140);

    String caminhoSalvo = service.salvarPdf(conteudo, nomeLongo);

    assertTrue(Path.of(caminhoSalvo).getFileName().toString().length() <= 120);
  }

  @Test
  @DisplayName("Deve carregar PDF salvo")
  void deveCarregarPdfSalvo() {
    String pastaArquivos = tempDir.resolve("uploads").resolve("itens").toString();
    ItemArquivoStorageService service = new ItemArquivoStorageService(pastaArquivos);
    byte[] conteudo = "pdf-test".getBytes();
    String caminhoSalvo = service.salvarPdf(conteudo, "carregar.pdf");

    byte[] carregado = service.carregarPdf(caminhoSalvo);

    assertArrayEquals(conteudo, carregado);
  }

  @Test
  @DisplayName("Deve falhar ao carregar arquivo fora do diretorio base")
  void deveFalharAoCarregarArquivoForaDoDiretorioBase() {
    String pastaArquivos = tempDir.resolve("uploads").resolve("itens").toString();
    ItemArquivoStorageService service = new ItemArquivoStorageService(pastaArquivos);
    Path caminhoFora = tempDir.resolve("fora.pdf");
    String caminhoForaTexto = caminhoFora.toString();

    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> service.carregarPdf(caminhoForaTexto));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  @DisplayName("Deve retornar 404 para caminho vazio")
  void deveRetornar404ParaCaminhoVazio() {
    String pastaArquivos = tempDir.resolve("uploads").resolve("itens").toString();
    ItemArquivoStorageService service = new ItemArquivoStorageService(pastaArquivos);

    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> service.carregarPdf("  "));

    assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
  }

  @Test
  @DisplayName("Deve retornar 404 para arquivo inexistente dentro do diretorio base")
  void deveRetornar404ParaArquivoInexistenteDentroDoDiretorioBase() {
    String pastaArquivos = tempDir.resolve("uploads").resolve("itens").toString();
    ItemArquivoStorageService service = new ItemArquivoStorageService(pastaArquivos);
    String caminhoInexistente = tempDir.resolve("uploads").resolve("itens").resolve("nao-existe.pdf").toString();

    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> service.carregarPdf(caminhoInexistente));

    assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
  }

  @Test
  @DisplayName("Deve deletar PDF salvo")
  void deveDeletarPdfSalvo() {
    String pastaArquivos = tempDir.resolve("uploads").resolve("itens").toString();
    ItemArquivoStorageService service = new ItemArquivoStorageService(pastaArquivos);
    byte[] conteudo = "pdf-test".getBytes();
    String caminhoSalvo = service.salvarPdf(conteudo, "deletar.pdf");
    Path arquivo = Path.of(caminhoSalvo);
    assertTrue(Files.exists(arquivo));

    service.deletarPdf(caminhoSalvo);

    assertTrue(!Files.exists(arquivo));
  }

  @Test
  @DisplayName("Deve falhar ao deletar arquivo fora do diretorio base")
  void deveFalharAoDeletarArquivoForaDoDiretorioBase() {
    String pastaArquivos = tempDir.resolve("uploads").resolve("itens").toString();
    ItemArquivoStorageService service = new ItemArquivoStorageService(pastaArquivos);
    Path caminhoFora = tempDir.resolve("fora.pdf");
    String caminhoForaTexto = caminhoFora.toString();

    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> service.deletarPdf(caminhoForaTexto));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  @DisplayName("Deve ignorar delecao quando caminho for vazio")
  void deveIgnorarDelecaoQuandoCaminhoForVazio() {
    String pastaArquivos = tempDir.resolve("uploads").resolve("itens").toString();
    ItemArquivoStorageService service = new ItemArquivoStorageService(pastaArquivos);

    assertDoesNotThrow(() -> service.deletarPdf(" "));
  }
}
