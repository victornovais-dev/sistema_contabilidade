package com.sistema_contabilidade.item.repository;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.TipoItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@DisplayName("ItemRepository DataJpa tests")
class ItemRepositoryTest {

  @Autowired private ItemRepository itemRepository;

  @Test
  @DisplayName("Deve salvar e buscar item por id")
  void deveSalvarEBuscarItemPorId() {
    Item item = new Item();
    item.setValor(new BigDecimal("88.30"));
    item.setData(LocalDate.of(2026, 3, 15));
    item.setHorarioCriacao(LocalDateTime.of(2026, 3, 15, 14, 10, 0));
    item.setArquivoPdf("comprovante".getBytes());
    item.setTipo(TipoItem.DESPESA);

    Item salvo = itemRepository.save(item);
    Optional<Item> encontrado = itemRepository.findById(salvo.getId());

    assertTrue(encontrado.isPresent());
    assertTrue(itemRepository.existsById(salvo.getId()));
  }
}
