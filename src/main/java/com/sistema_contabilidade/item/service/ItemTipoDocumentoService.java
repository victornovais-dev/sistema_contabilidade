package com.sistema_contabilidade.item.service;

import static com.sistema_contabilidade.item.config.ItemTipoDocumentoCatalog.ITEM_TIPOS_DOCUMENTO_CACHE;

import com.sistema_contabilidade.item.config.ItemTipoDocumentoCatalog;
import com.sistema_contabilidade.item.repository.ItemTipoDocumentoRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ItemTipoDocumentoService {

  private final ItemTipoDocumentoRepository itemTipoDocumentoRepository;

  @Cacheable(ITEM_TIPOS_DOCUMENTO_CACHE)
  @Transactional(readOnly = true)
  public List<String> listarTiposDocumento() {
    try {
      List<String> tiposDocumento =
          itemTipoDocumentoRepository.findAllByOrderByOrdemAscNomeAsc().stream()
              .map(itemTipoDocumento -> itemTipoDocumento.getNome())
              .toList();
      if (!tiposDocumento.isEmpty()) {
        return tiposDocumento;
      }
    } catch (RuntimeException exception) {
      if (log.isWarnEnabled()) {
        log.warn(
            "Falha ao carregar tipos de documento do banco. Usando catalogo padrao.", exception);
      }
    }

    return ItemTipoDocumentoCatalog.defaultDocumentTypes().stream()
        .map(seed -> seed.nome())
        .toList();
  }
}
