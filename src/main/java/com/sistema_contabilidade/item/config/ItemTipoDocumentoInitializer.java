package com.sistema_contabilidade.item.config;

import com.sistema_contabilidade.item.model.ItemTipoDocumento;
import com.sistema_contabilidade.item.repository.ItemTipoDocumentoRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(16)
@RequiredArgsConstructor
public class ItemTipoDocumentoInitializer implements CommandLineRunner {

  private final ItemTipoDocumentoRepository itemTipoDocumentoRepository;

  @Override
  @Transactional
  public void run(String... args) {
    for (ItemTipoDocumentoCatalog.ItemTipoDocumentoSeed seed :
        ItemTipoDocumentoCatalog.defaultDocumentTypes()) {
      ItemTipoDocumento itemTipoDocumento =
          itemTipoDocumentoRepository
              .findByNomeIgnoreCase(seed.nome())
              .orElseGet(ItemTipoDocumento::new);
      itemTipoDocumento.setNome(seed.nome());
      itemTipoDocumento.setOrdem(seed.ordem());
      itemTipoDocumentoRepository.save(itemTipoDocumento);
    }
  }
}
