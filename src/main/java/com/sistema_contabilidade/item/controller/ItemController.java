package com.sistema_contabilidade.item.controller;

import com.sistema_contabilidade.item.dto.ItemResponse;
import com.sistema_contabilidade.item.dto.ItemUpsertRequest;
import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.repository.ItemRepository;
import com.sistema_contabilidade.item.service.ItemArquivoStorageService;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import jakarta.validation.Valid;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
  private final ItemArquivoStorageService itemArquivoStorageService;
  private final UsuarioRepository usuarioRepository;

  @PostMapping
  public ResponseEntity<ItemResponse> criar(
      Authentication authentication, @Valid @RequestBody ItemUpsertRequest request) {
    Usuario usuarioAutenticado = buscarUsuarioAutenticado(authentication);
    Item item = new Item();
    item.setValor(request.valor());
    item.setData(request.data());
    item.setHorarioCriacao(request.horarioCriacao());
    item.setCaminhoArquivoPdf(itemArquivoStorageService.salvarPdf(request.arquivoPdf()));
    item.setTipo(request.tipo());
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

  @PutMapping(ID_PATH)
  public ResponseEntity<ItemResponse> atualizar(
      Authentication authentication,
      @PathVariable("id") UUID id,
      @Valid @RequestBody ItemUpsertRequest request) {
    Item item = buscarItemAutorizadoPorId(id, authentication);
    item.setValor(request.valor());
    item.setData(request.data());
    item.setHorarioCriacao(request.horarioCriacao());
    item.setCaminhoArquivoPdf(itemArquivoStorageService.salvarPdf(request.arquivoPdf()));
    item.setTipo(request.tipo());

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
}
