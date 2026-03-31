package com.sistema_contabilidade.relatorio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.item.repository.ItemRepository;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@DisplayName("RelatorioFinanceiroService unit tests")
class RelatorioFinanceiroServiceTest {

  @Test
  @DisplayName("Deve incluir descricao do item no DTO do relatorio")
  void deveIncluirDescricaoNoDto() {
    ItemRepository itemRepository = Mockito.mock(ItemRepository.class);
    RoleRepository roleRepository = Mockito.mock(RoleRepository.class);
    RelatorioFinanceiroService service =
        new RelatorioFinanceiroService(itemRepository, roleRepository);

    Item item = new Item();
    item.setId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    item.setTipo(TipoItem.RECEITA);
    item.setValor(new BigDecimal("10.00"));
    item.setData(LocalDate.of(2026, 3, 31));
    item.setHorarioCriacao(LocalDateTime.of(2026, 3, 31, 10, 0));
    item.setDescricao("ENERGIA");

    when(itemRepository.findAll()).thenReturn(List.of(item));

    var auth =
        new UsernamePasswordAuthenticationToken(
            "admin@email.com", "senha", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

    var response = service.gerar(auth, null);
    assertEquals(1, response.receitas().size());
    assertEquals("ENERGIA", response.receitas().getFirst().descricao());
  }
}
