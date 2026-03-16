package com.sistema_contabilidade.item.controller;

import com.sistema_contabilidade.item.dto.ItemCreateRequest;
import com.sistema_contabilidade.item.dto.ItemResponse;
import com.sistema_contabilidade.item.dto.ItemUpdateRequest;
import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.repository.ItemRepository;
import com.sistema_contabilidade.item.service.ItemArquivoStorageService;
import jakarta.validation.Valid;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

  private final ItemRepository itemRepository;
  private final ItemArquivoStorageService itemArquivoStorageService;

  @PostMapping
  public ResponseEntity<ItemResponse> criar(@Valid @RequestBody ItemCreateRequest request) {
    Item item = new Item();
    item.setValor(request.valor());
    item.setData(request.data());
    item.setHorarioCriacao(request.horarioCriacao());
    item.setCaminhoArquivoPdf(itemArquivoStorageService.salvarPdf(request.arquivoPdf()));
    item.setTipo(request.tipo());

    Item salvo = itemRepository.save(item);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path(ID_PATH)
            .buildAndExpand(salvo.getId())
            .toUri();
    return ResponseEntity.created(location).body(ItemResponse.from(salvo));
  }

  @GetMapping
  public ResponseEntity<List<ItemResponse>> listarTodos() {
    List<ItemResponse> response =
        itemRepository.findAll().stream().map(ItemResponse::from).toList();
    return ResponseEntity.ok(response);
  }

  @GetMapping(ID_PATH)
  public ResponseEntity<ItemResponse> buscarPorId(@PathVariable("id") UUID id) {
    Item item =
        itemRepository
            .findById(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, ITEM_NAO_ENCONTRADO));
    return ResponseEntity.ok(ItemResponse.from(item));
  }

  @GetMapping(ARQUIVO_PATH)
  public ResponseEntity<InputStreamResource> baixarArquivo(@PathVariable("id") UUID id) {
    Item item =
        itemRepository
            .findById(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, ITEM_NAO_ENCONTRADO));

    byte[] arquivoPdf = itemArquivoStorageService.carregarPdf(item.getCaminhoArquivoPdf());
    String nomeArquivo = Path.of(item.getCaminhoArquivoPdf()).getFileName().toString();

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeArquivo + "\"")
        .body(new InputStreamResource(new ByteArrayInputStream(arquivoPdf)));
  }

  @PutMapping(ID_PATH)
  public ResponseEntity<ItemResponse> atualizar(
      @PathVariable("id") UUID id, @Valid @RequestBody ItemUpdateRequest request) {
    Item item =
        itemRepository
            .findById(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, ITEM_NAO_ENCONTRADO));
    item.setValor(request.valor());
    item.setData(request.data());
    item.setHorarioCriacao(request.horarioCriacao());
    item.setCaminhoArquivoPdf(itemArquivoStorageService.salvarPdf(request.arquivoPdf()));
    item.setTipo(request.tipo());

    Item salvo = itemRepository.save(item);
    return ResponseEntity.ok(ItemResponse.from(salvo));
  }

  @DeleteMapping(ID_PATH)
  public ResponseEntity<Void> deletar(@PathVariable("id") UUID id) {
    Item item =
        itemRepository
            .findById(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, ITEM_NAO_ENCONTRADO));
    itemRepository.delete(item);
    return ResponseEntity.noContent().build();
  }
}
