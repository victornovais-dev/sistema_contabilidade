package com.sistema_contabilidade.item.config;

import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.repository.ItemRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class ItemRazaoSocialSearchInitializer implements CommandLineRunner {

  private static final int BATCH_SIZE = 250;

  private final ItemRepository itemRepository;
  private final PlatformTransactionManager transactionManager;
  private final ItemRazaoSocialSearchDatabaseSupport databaseSupport;

  @Override
  public void run(String... args) {
    int totalAtualizado = backfillMissingSearchValues();
    if (totalAtualizado > 0) {
      log.info(
          "Backfill de razao_social_busca concluido com {} registros atualizados.",
          totalAtualizado);
    }
    databaseSupport.ensureFullTextIndex();
  }

  private int backfillMissingSearchValues() {
    int totalAtualizado = 0;
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

    while (true) {
      List<UUID> ids =
          itemRepository.findIdsWithMissingRazaoSocialBusca(PageRequest.of(0, BATCH_SIZE));
      if (ids.isEmpty()) {
        return totalAtualizado;
      }

      Integer atualizadosNoLote =
          transactionTemplate.execute(
              status -> {
                List<Item> itens = itemRepository.findAllById(ids);
                itens.forEach(this::prepareItemForBackfill);
                itemRepository.saveAll(itens);
                itemRepository.flush();
                return itens.size();
              });
      totalAtualizado += atualizadosNoLote == null ? 0 : atualizadosNoLote;

      if (ids.size() < BATCH_SIZE) {
        return totalAtualizado;
      }
    }
  }

  private void prepareItemForBackfill(Item item) {
    initializeVersionIfNull(item);
    item.synchronizeSearchFields();
  }

  private void initializeVersionIfNull(Item item) {
    if (item == null || item.getId() == null || item.getVersion() != null) {
      return;
    }

    itemRepository.initializeVersionIfNull(item.getId());
    Long versionAtual = itemRepository.findVersionById(item.getId()).orElse(0L);
    item.setVersion(versionAtual);
  }
}
