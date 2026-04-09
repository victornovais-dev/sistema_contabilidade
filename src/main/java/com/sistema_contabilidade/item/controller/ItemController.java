package com.sistema_contabilidade.item.controller;

import com.sistema_contabilidade.item.dto.ItemArquivoResponse;
import com.sistema_contabilidade.item.dto.ItemArquivosUploadRequest;
import com.sistema_contabilidade.item.dto.ItemListResponse;
import com.sistema_contabilidade.item.dto.ItemObservacaoUpdateRequest;
import com.sistema_contabilidade.item.dto.ItemResponse;
import com.sistema_contabilidade.item.dto.ItemUpsertRequest;
import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.ItemArquivo;
import com.sistema_contabilidade.item.repository.ItemArquivoRepository;
import com.sistema_contabilidade.item.repository.ItemRepository;
import com.sistema_contabilidade.item.service.ItemAccessUtils;
import com.sistema_contabilidade.item.service.ItemArquivoStorageService;
import com.sistema_contabilidade.item.service.ItemListService;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/itens")
@Validated
@RequiredArgsConstructor
public class ItemController {

  private static final int SINGLE_ROLE_COUNT = 1;
  private static final String ID_PATH = "/{id}";
  private static final String ARQUIVO_PATH = ID_PATH + "/arquivo";
  private static final String ITEM_NAO_ENCONTRADO = "Item nao encontrado";
  private static final String ACESSO_NEGADO_ITEM = "Acesso negado ao item";
  private static final String ARQUIVO_ITEM_NAO_ENCONTRADO = "Arquivo do item nao encontrado";
  private static final String NOME_ARQUIVO_INVALIDO = "Nome do arquivo invalido";

  private final ItemRepository itemRepository;
  private final ItemArquivoRepository itemArquivoRepository;
  private final ItemArquivoStorageService itemArquivoStorageService;
  private final ItemListService itemListService;
  private final UsuarioRepository usuarioRepository;

  @PostMapping
  public ResponseEntity<ItemResponse> criar(
      Authentication authentication, @Valid @RequestBody ItemUpsertRequest request) {
    Usuario usuarioAutenticado =
        ItemAccessUtils.buscarUsuarioAutenticado(authentication, usuarioRepository);
    Item item = new Item();
    applyRequest(item, request);
    item.setCriadoPor(usuarioAutenticado);
    item.setRoleNome(resolverRoleNomeItem(usuarioAutenticado, request.role(), null));

    Item salvo = itemRepository.save(item);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path(ID_PATH)
            .buildAndExpand(salvo.getId())
            .toUri();
    return ResponseEntity.created(location).body(ItemResponse.from(salvo));
  }

  @GetMapping
  public ResponseEntity<List<ItemListResponse>> listarTodos(
      Authentication authentication, @RequestParam(name = "role", required = false) String role) {
    return ResponseEntity.ok(itemListService.listarItens(authentication, role));
  }

  @GetMapping("/roles")
  public ResponseEntity<List<String>> listarRolesDisponiveis(Authentication authentication) {
    return ResponseEntity.ok(itemListService.listarRolesDisponiveis(authentication));
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

  @DeleteMapping(ID_PATH + "/arquivos/{arquivoId}")
  @Transactional
  public ResponseEntity<Void> deletarArquivo(
      Authentication authentication,
      @PathVariable("id") UUID id,
      @PathVariable("arquivoId") UUID arquivoId) {
    Item item = buscarItemAutorizadoPorId(id, authentication);
    ItemArquivo arquivo =
        item.getArquivos().stream()
            .filter(entry -> arquivoId.equals(entry.getId()))
            .findFirst()
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, ARQUIVO_ITEM_NAO_ENCONTRADO));

    itemArquivoStorageService.deletarPdf(arquivo.getCaminhoArquivoPdf());
    item.getArquivos().removeIf(entry -> arquivoId.equals(entry.getId()));

    if (item.getCaminhoArquivoPdf() != null
        && item.getCaminhoArquivoPdf().equals(arquivo.getCaminhoArquivoPdf())) {
      String novoCaminho =
          item.getArquivos().isEmpty()
              ? null
              : item.getArquivos().getFirst().getCaminhoArquivoPdf();
      item.setCaminhoArquivoPdf(novoCaminho);
    }

    itemRepository.save(item);
    return ResponseEntity.noContent().build();
  }

  @PutMapping(ID_PATH)
  public ResponseEntity<ItemResponse> atualizar(
      Authentication authentication,
      @PathVariable("id") UUID id,
      @Valid @RequestBody ItemUpsertRequest request) {
    Item item = buscarItemAutorizadoPorId(id, authentication);
    Usuario usuarioAutenticado =
        ItemAccessUtils.buscarUsuarioAutenticado(authentication, usuarioRepository);
    applyRequest(item, request);
    item.setRoleNome(resolverRoleNomeItem(usuarioAutenticado, request.role(), item.getRoleNome()));

    Item salvo = itemRepository.save(item);
    return ResponseEntity.ok(ItemResponse.from(salvo));
  }

  @PatchMapping(ID_PATH + "/observacao")
  public ResponseEntity<ItemResponse> atualizarObservacao(
      Authentication authentication,
      @PathVariable("id") UUID id,
      @Valid @RequestBody ItemObservacaoUpdateRequest request) {
    Item item = buscarItemAutorizadoPorId(id, authentication);
    String observacao = request.observacao();
    item.setObservacao(observacao == null ? null : observacao.trim());
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

  private boolean temAcessoPorRole(Authentication authentication, Item item) {
    Set<String> roleNomesUsuario =
        ItemAccessUtils.extrairRoleNomes(
            ItemAccessUtils.buscarUsuarioAutenticado(authentication, usuarioRepository));
    if (roleNomesUsuario.isEmpty()) {
      return false;
    }
    String roleNomeItem = ItemAccessUtils.normalizarRole(item.getRoleNome());
    return roleNomeItem != null && roleNomesUsuario.contains(roleNomeItem);
  }

  private boolean isAdmin(Authentication authentication) {
    return ItemAccessUtils.isAdmin(authentication);
  }

  private void validarRoleFiltro(String roleFiltro, Set<String> roleNomesUsuario) {
    ItemAccessUtils.validarRoleFiltro(roleFiltro, roleNomesUsuario);
  }

  private String resolverRoleNomeItem(
      Usuario usuarioAutenticado, String roleRequest, String roleAtualItem) {
    Set<String> roleNomesUsuario = ItemAccessUtils.extrairRoleNomes(usuarioAutenticado);
    if (roleNomesUsuario.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "Usuario autenticado nao possui role para vincular ao comprovante.");
    }

    String roleRequestNormalizada = ItemAccessUtils.normalizarRole(roleRequest);
    if (roleRequestNormalizada != null) {
      validarRoleFiltro(roleRequestNormalizada, roleNomesUsuario);
      return roleRequestNormalizada;
    }

    String roleAtualNormalizada = ItemAccessUtils.normalizarRole(roleAtualItem);
    if (roleAtualNormalizada != null) {
      return roleAtualNormalizada;
    }

    if (roleNomesUsuario.size() == SINGLE_ROLE_COUNT) {
      return roleNomesUsuario.iterator().next();
    }

    throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST, "Selecione a role responsavel por este comprovante.");
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
