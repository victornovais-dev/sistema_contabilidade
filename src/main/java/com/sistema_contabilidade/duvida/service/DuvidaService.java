package com.sistema_contabilidade.duvida.service;

import com.sistema_contabilidade.duvida.dto.DuvidaCreateRequest;
import com.sistema_contabilidade.duvida.dto.DuvidaCreateResponse;
import com.sistema_contabilidade.duvida.dto.DuvidaListResponse;
import com.sistema_contabilidade.duvida.dto.DuvidaResponse;
import com.sistema_contabilidade.duvida.model.Duvida;
import com.sistema_contabilidade.duvida.model.DuvidaStatus;
import com.sistema_contabilidade.duvida.repository.DuvidaRepository;
import com.sistema_contabilidade.security.validation.InputSanitizer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class DuvidaService {

  private final DuvidaRepository duvidaRepository;
  private final InputSanitizer inputSanitizer;

  @Transactional
  public DuvidaCreateResponse registrar(DuvidaCreateRequest request) {
    Duvida duvida = new Duvida();
    duvida.setNome(inputSanitizer.sanitizeInlineText(request.nome(), "nome", 120));
    duvida.setEmail(inputSanitizer.sanitizeEmail(request.email(), "email"));
    duvida.setMensagem(inputSanitizer.sanitizeMultilineText(request.duvida(), "duvida", 1200));
    duvida.setRecebidaEm(LocalDateTime.now(ZoneId.systemDefault()));
    duvida.setStatus(DuvidaStatus.PENDENTE);

    Duvida salva = duvidaRepository.save(duvida);
    return new DuvidaCreateResponse(salva.getId(), salva.getRecebidaEm());
  }

  @Transactional(readOnly = true)
  public DuvidaListResponse listar(String termo, DuvidaStatus status, int pagina, int tamanho) {
    int paginaSegura = Math.max(pagina, 0);
    int tamanhoSeguro = Math.clamp(tamanho, 1, 50);
    String termoSeguro = normalizarTermo(termo);
    PageRequest pageable =
        PageRequest.of(paginaSegura, tamanhoSeguro, Sort.by(Sort.Direction.DESC, "recebidaEm"));
    Page<Duvida> resultado = buscar(termoSeguro, status, pageable);
    List<DuvidaResponse> duvidas = resultado.getContent().stream().map(this::toResponse).toList();
    return new DuvidaListResponse(
        duvidas,
        resultado.getNumber(),
        resultado.getSize(),
        resultado.getTotalElements(),
        resultado.getTotalPages());
  }

  @Transactional
  public DuvidaResponse atualizarStatus(UUID protocolo, DuvidaStatus status) {
    Duvida duvida =
        duvidaRepository
            .findById(protocolo)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Duvida nao encontrada"));
    duvida.setStatus(status);
    return toResponse(duvidaRepository.save(duvida));
  }

  private Page<Duvida> buscar(String termo, DuvidaStatus status, PageRequest pageable) {
    try {
      UUID protocolo = UUID.fromString(termo);
      List<Duvida> resultado =
          pageable.getPageNumber() == 0
              ? duvidaRepository
                  .findById(protocolo)
                  .filter(duvida -> status == null || status == duvida.getStatus())
                  .stream()
                  .toList()
              : List.of();
      return new PageImpl<>(resultado, pageable, resultado.size());
    } catch (IllegalArgumentException _) {
      return duvidaRepository.buscar(termo, status, pageable);
    }
  }

  private String normalizarTermo(String termo) {
    if (termo == null) {
      return "";
    }
    String normalizado = termo.strip().toLowerCase(Locale.ROOT);
    return normalizado.substring(0, Math.min(normalizado.length(), 120));
  }

  private DuvidaResponse toResponse(Duvida duvida) {
    return new DuvidaResponse(
        duvida.getId(),
        duvida.getNome(),
        duvida.getEmail(),
        duvida.getMensagem(),
        duvida.getRecebidaEm(),
        duvida.getStatus());
  }
}
