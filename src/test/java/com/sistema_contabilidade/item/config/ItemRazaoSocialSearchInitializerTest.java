package com.sistema_contabilidade.item.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.repository.ItemRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("ItemRazaoSocialSearchInitializer unit tests")
class ItemRazaoSocialSearchInitializerTest {

  @Mock private ItemRepository itemRepository;
  @Mock private PlatformTransactionManager transactionManager;
  @Mock private ItemRazaoSocialSearchDatabaseSupport databaseSupport;

  @Test
  @DisplayName("Deve inicializar version nula antes do flush no backfill")
  void deveInicializarVersionNulaAntesDoFlushNoBackfill() {
    UUID itemId = UUID.fromString("abababab-abab-abab-abab-abababababab");
    Item item = new Item();
    item.setId(itemId);
    item.setRazaoSocialNome("Hotel XPTO");
    item.setRazaoSocialBusca(null);
    item.setVersion(null);

    when(transactionManager.getTransaction(any(TransactionDefinition.class)))
        .thenReturn(new SimpleTransactionStatus());
    when(itemRepository.findIdsWithMissingRazaoSocialBusca(PageRequest.of(0, 250)))
        .thenReturn(List.of(itemId))
        .thenReturn(List.of());
    when(itemRepository.findAllById(List.of(itemId))).thenReturn(List.of(item));
    when(itemRepository.initializeVersionIfNull(itemId)).thenReturn(1);
    when(itemRepository.findVersionById(itemId)).thenReturn(Optional.of(0L));

    ItemRazaoSocialSearchInitializer initializer =
        new ItemRazaoSocialSearchInitializer(itemRepository, transactionManager, databaseSupport);

    initializer.run();

    ArgumentCaptor<List<Item>> itemsCaptor = ArgumentCaptor.forClass(List.class);
    verify(itemRepository).saveAll(itemsCaptor.capture());
    Item salvo = itemsCaptor.getValue().getFirst();
    assertEquals(0L, salvo.getVersion());
    assertEquals("HOTEL XPTO", salvo.getRazaoSocialBusca());
    verify(itemRepository).initializeVersionIfNull(itemId);
    verify(itemRepository).flush();
    verify(databaseSupport).ensureFullTextIndex();
  }

  @Test
  @DisplayName("Nao deve tocar version quando item ja estiver consistente")
  void naoDeveTocarVersionQuandoItemJaEstiverConsistente() {
    UUID itemId = UUID.fromString("cdcdcdcd-cdcd-cdcd-cdcd-cdcdcdcdcdcd");
    Item item = new Item();
    item.setId(itemId);
    item.setRazaoSocialNome("Fornecedor ABC");
    item.setVersion(7L);

    when(transactionManager.getTransaction(any(TransactionDefinition.class)))
        .thenReturn(new SimpleTransactionStatus());
    when(itemRepository.findIdsWithMissingRazaoSocialBusca(PageRequest.of(0, 250)))
        .thenReturn(List.of(itemId))
        .thenReturn(List.of());
    when(itemRepository.findAllById(List.of(itemId))).thenReturn(List.of(item));

    ItemRazaoSocialSearchInitializer initializer =
        new ItemRazaoSocialSearchInitializer(itemRepository, transactionManager, databaseSupport);

    initializer.run();

    verify(itemRepository, never()).initializeVersionIfNull(itemId);
    verify(itemRepository, never()).findVersionById(itemId);
    verify(itemRepository).flush();
    verify(databaseSupport).ensureFullTextIndex();
  }
}
