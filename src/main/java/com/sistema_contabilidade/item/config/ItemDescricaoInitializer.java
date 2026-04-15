package com.sistema_contabilidade.item.config;

import com.sistema_contabilidade.item.model.ItemDescricao;
import com.sistema_contabilidade.item.repository.ItemDescricaoRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(15)
@RequiredArgsConstructor
public class ItemDescricaoInitializer implements CommandLineRunner {

  private final ItemDescricaoRepository itemDescricaoRepository;

  @Override
  @Transactional
  public void run(String... args) {
    for (ItemDescricaoCatalog.ItemDescricaoSeed seed : ItemDescricaoCatalog.defaultDescriptions()) {
      ItemDescricao itemDescricao =
          itemDescricaoRepository
              .findByTipoAndNomeIgnoreCase(seed.tipo(), seed.nome())
              .orElseGet(ItemDescricao::new);
      itemDescricao.setTipo(seed.tipo());
      itemDescricao.setNome(seed.nome());
      itemDescricao.setOrdem(seed.ordem());
      itemDescricaoRepository.save(itemDescricao);
    }
  }
}
