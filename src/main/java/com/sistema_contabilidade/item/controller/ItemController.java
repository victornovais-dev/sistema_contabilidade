package com.sistema_contabilidade.item.controller;

import com.sistema_contabilidade.item.dto.ItemArquivoResponse;
import com.sistema_contabilidade.item.dto.ItemArquivosUploadRequest;
import com.sistema_contabilidade.item.dto.ItemResponse;
import com.sistema_contabilidade.item.dto.ItemUpsertRequest;
import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.ItemArquivo;
import com.sistema_contabilidade.item.repository.ItemArquivoRepository;
import com.sistema_contabilidade.item.repository.ItemRepository;
import com.sistema_contabilidade.item.service.ItemArquivoStorageService;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import jakarta.validation.Valid;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/itens")
@Validated
@RequiredArgsConstructor
public class ItemController {

  private static final String ID_PATH = "/{id}";
  private static final String ARQUIVO_PATH = ID_PATH + "/arquivo";
  private static final String ITEM_NAO_ENCONTRADO = "Item nao encontrado";
  private static final String ACESSO_NEGADO_ITEM = "Acesso negado ao item";
  private static final String ARQUIVO_ITEM_NAO_ENCONTRADO = "Arquivo do item nao encontrado";
  private static final String NOME_ARQUIVO_INVALIDO = "Nome do arquivo invalido";

  private final ItemRepository itemRepository;
  private final ItemArquivoRepository itemArquivoRepository;
  private final ItemArquivoStorageService itemArquivoStorageService;
  private final UsuarioRepository usuarioRepository;

  @PostMapping
  public ResponseEntity<ItemResponse> criar(
      Authentication authentication, @Valid @RequestBody ItemUpsertRequest request) {
    Usuario usuarioAutenticado = buscarUsuarioAutenticado(authentication);
    Item item = new Item();
    applyRequest(item, request);
    item.setCriadoPor(usuarioAutenticado);

    Item salvo = itemRepository.save(item);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path(ID_PATH)
            .buildAndExpand(salvo.getId())
            .toUri();
    return ResponseEntity.created(location).body(ItemResponse.from(salvo));
  }

  @GetMapping
  public ResponseEntity<List<ItemResponse>> listarTodos(Authentication authentication) {
    List<Item> itensVisiveis = buscarItensVisiveis(authentication);
    List<ItemResponse> response = itensVisiveis.stream().map(ItemResponse::from).toList();
    return ResponseEntity.ok(response);
  }

  @GetMapping(ID_PATH)
  public ResponseEntity<ItemResponse> buscarPorId(
      Authentication authentication, @PathVariable("id") UUID id) {
    Item item = buscarItemAutorizadoPorId(id, authentication);
    return ResponseEntity.ok(ItemResponse.from(item));
  }

  @GetMapping(ARQUIVO_PATH)
  public ResponseEntity<InputStreamResource> baixarArquivo(
      Authentication authentication, @PathVariable("id") UUID id) {
    Item item = buscarItemAutorizadoPorId(id, authentication);
    String caminhoArquivoPdf = item.getCaminhoArquivoPdf();
    if (caminhoArquivoPdf == null || caminhoArquivoPdf.isBlank()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ARQUIVO_ITEM_NAO_ENCONTRADO);
    }

    byte[] arquivoPdf = itemArquivoStorageService.carregarPdf(caminhoArquivoPdf);
    Path nomeArquivoPath = Path.of(caminhoArquivoPdf).getFileName();
    if (nomeArquivoPath == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, NOME_ARQUIVO_INVALIDO);
    }
    String nomeArquivo = nomeArquivoPath.toString();

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeArquivo + "\"")
        .body(new InputStreamResource(new ByteArrayInputStream(arquivoPdf)));
  }

  @GetMapping(ID_PATH + "/arquivos")
  public ResponseEntity<List<ItemArquivoResponse>> listarArquivos(
      Authentication authentication, @PathVariable("id") UUID id) {
    Item item = buscarItemAutorizadoPorId(id, authentication);
    List<ItemArquivoResponse> response =
        item.getArquivos().stream().map(ItemArquivoResponse::from).toList();
    return ResponseEntity.ok(response);
  }

  @GetMapping(ID_PATH + "/arquivos/{arquivoId}")
  public ResponseEntity<InputStreamResource> baixarArquivoPorId(
      Authentication authentication,
      @PathVariable("id") UUID id,
      @PathVariable("arquivoId") UUID arquivoId) {
    Item item = buscarItemAutorizadoPorId(id, authentication);
    ItemArquivo arquivo =
        itemArquivoRepository
            .findById(arquivoId)
            .filter(
                found -> found.getItem() != null && item.getId().equals(found.getItem().getId()))
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, ARQUIVO_ITEM_NAO_ENCONTRADO));
    byte[] arquivoPdf = itemArquivoStorageService.carregarPdf(arquivo.getCaminhoArquivoPdf());
    Path nomeArquivoPath = Path.of(arquivo.getCaminhoArquivoPdf()).getFileName();
    if (nomeArquivoPath == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, NOME_ARQUIVO_INVALIDO);
    }
    String nomeArquivo = nomeArquivoPath.toString();

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeArquivo + "\"")
        .body(new InputStreamResource(new ByteArrayInputStream(arquivoPdf)));
  }

  @GetMapping(ID_PATH + "/arquivos/download")
  public ResponseEntity<InputStreamResource> baixarTodosArquivos(
      Authentication authentication, @PathVariable("id") UUID id) {
    Item item = buscarItemAutorizadoPorId(id, authentication);
    if (item.getArquivos().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ARQUIVO_ITEM_NAO_ENCONTRADO);
    }
    List<ItemArquivo> arquivos = new ArrayList<>(item.getArquivos());
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(outputStream)) {
      for (ItemArquivo arquivo : arquivos) {
        String caminho = arquivo.getCaminhoArquivoPdf();
        if (caminho == null || caminho.isBlank()) {
          continue;
        }
        byte[] conteudo = itemArquivoStorageService.carregarPdf(caminho);
        Path nomeArquivoPath = Path.of(caminho).getFileName();
        String nomeArquivo = nomeArquivoPath == null ? "arquivo.pdf" : nomeArquivoPath.toString();
        zip.putNextEntry(new ZipEntry(nomeArquivo));
        zip.write(conteudo);
        zip.closeEntry();
      }
      String observacao = item.getObservacao();
      String observacaoTexto =
          observacao == null || observacao.isBlank() ? "Sem observacao registrada." : observacao;
      byte[] observacaoBytes = observacaoTexto.getBytes(java.nio.charset.StandardCharsets.UTF_8);
      zip.putNextEntry(new ZipEntry("observacao.txt"));
      zip.write(observacaoBytes);
      zip.closeEntry();
    } catch (Exception ex) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao gerar arquivo ZIP", ex);
    }

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"comprovantes-" + id + ".zip\"")
        .body(new InputStreamResource(new ByteArrayInputStream(outputStream.toByteArray())));
  }

  @PostMapping(ID_PATH + "/arquivos")
  public ResponseEntity<List<ItemArquivoResponse>> adicionarArquivos(
      Authentication authentication,
      @PathVariable("id") UUID id,
      @Valid @RequestBody ItemArquivosUploadRequest request) {
    Item item = buscarItemAutorizadoPorId(id, authentication);
    List<byte[]> arquivosPdf = request.arquivosPdf();
    if (arquivosPdf == null || arquivosPdf.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie ao menos um PDF.");
    }
    List<String> caminhos =
        itemArquivoStorageService.salvarPdfs(arquivosPdf, request.nomesArquivos());
    for (String caminho : caminhos) {
      ItemArquivo arquivo = new ItemArquivo();
      arquivo.setCaminhoArquivoPdf(caminho);
      arquivo.setItem(item);
      item.getArquivos().add(arquivo);
    }
    Item salvo = itemRepository.save(item);
    List<ItemArquivoResponse> response =
        salvo.getArquivos().stream().map(ItemArquivoResponse::from).toList();
    return ResponseEntity.ok(response);
  }

  @PutMapping(ID_PATH)
  public ResponseEntity<ItemResponse> atualizar(
      Authentication authentication,
      @PathVariable("id") UUID id,
      @Valid @RequestBody ItemUpsertRequest request) {
    Item item = buscarItemAutorizadoPorId(id, authentication);
    applyRequest(item, request);

    Item salvo = itemRepository.save(item);
    return ResponseEntity.ok(ItemResponse.from(salvo));
  }

  @DeleteMapping(ID_PATH)
  public ResponseEntity<Void> deletar(Authentication authentication, @PathVariable("id") UUID id) {
    Item item = buscarItemAutorizadoPorId(id, authentication);
    itemRepository.delete(item);
    return ResponseEntity.noContent().build();
  }

  private Item buscarItemAutorizadoPorId(UUID id, Authentication authentication) {
    Item item =
        itemRepository
            .findByIdComCriadorERoles(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, ITEM_NAO_ENCONTRADO));
    if (!isAdmin(authentication) && !temAcessoPorRole(authentication, item)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, ACESSO_NEGADO_ITEM);
    }
    return item;
  }

  private List<Item> buscarItensVisiveis(Authentication authentication) {
    if (isAdmin(authentication)) {
      return itemRepository.findAll();
    }
    Usuario usuarioAutenticado = buscarUsuarioAutenticado(authentication);
    Set<String> roleNomesUsuario = extrairRoleNomes(usuarioAutenticado);
    if (roleNomesUsuario.isEmpty()) {
      return List.of();
    }
    return itemRepository.findAllVisiveisPorRoleNomes(roleNomesUsuario);
  }

  private boolean temAcessoPorRole(Authentication authentication, Item item) {
    Usuario usuarioAutenticado = buscarUsuarioAutenticado(authentication);
    Set<String> roleNomesUsuario = extrairRoleNomes(usuarioAutenticado);
    if (roleNomesUsuario.isEmpty() || item.getCriadoPor() == null) {
      return false;
    }
    Set<String> roleNomesCriador = extrairRoleNomes(item.getCriadoPor());
    return roleNomesCriador.stream().anyMatch(roleNomesUsuario::contains);
  }

  private boolean isAdmin(Authentication authentication) {
    if (authentication == null) {
      return false;
    }
    return authentication.getAuthorities().stream()
        .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
  }

  private Usuario buscarUsuarioAutenticado(Authentication authentication) {
    if (authentication == null || authentication.getName() == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario nao autenticado");
    }
    return usuarioRepository
        .findByEmail(authentication.getName())
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario nao encontrado"));
  }

  private Set<String> extrairRoleNomes(Usuario usuario) {
    return usuario.getRoles().stream().map(role -> role.getNome()).collect(Collectors.toSet());
  }

  private void applyRequest(Item item, ItemUpsertRequest request) {
    item.setValor(request.valor());
    item.setData(request.data());
    item.setHorarioCriacao(request.horarioCriacao());
    item.setTipo(request.tipo());
    item.setDescricao(request.descricao());
    item.setRazaoSocialNome(request.razaoSocialNome());
    item.setCnpjCpf(request.cnpjCpf());
    item.setObservacao(request.observacao());

    item.getArquivos().clear();
    var caminhos =
        itemArquivoStorageService.salvarPdfs(request.arquivosPdf(), request.nomesArquivos());
    for (String caminho : caminhos) {
      ItemArquivo arquivo = new ItemArquivo();
      arquivo.setCaminhoArquivoPdf(caminho);
      arquivo.setItem(item);
      item.getArquivos().add(arquivo);
    }
    item.setCaminhoArquivoPdf(caminhos.isEmpty() ? null : caminhos.getFirst());
  }
}
