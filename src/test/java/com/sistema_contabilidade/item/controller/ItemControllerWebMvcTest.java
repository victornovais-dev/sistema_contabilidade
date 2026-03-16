package com.sistema_contabilidade.item.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.item.repository.ItemRepository;
import com.sistema_contabilidade.item.service.ItemArquivoStorageService;
import com.sistema_contabilidade.security.service.CustomUserDetailsService;
import com.sistema_contabilidade.security.service.JwtService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ItemController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ItemController WebMvc tests")
class ItemControllerWebMvcTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ItemRepository itemRepository;
  @MockitoBean private ItemArquivoStorageService itemArquivoStorageService;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private CustomUserDetailsService customUserDetailsService;

  @Test
  @DisplayName("Deve retornar lista de itens")
  void listarTodosDeveRetornarOk() throws Exception {
    Item item = new Item();
    item.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    item.setValor(new BigDecimal("10.00"));
    item.setData(LocalDate.of(2026, 3, 15));
    item.setHorarioCriacao(LocalDateTime.of(2026, 3, 15, 12, 0, 0));
    item.setCaminhoArquivoPdf("uploads/itens/item-lista.pdf");
    item.setTipo(TipoItem.RECEITA);
    when(itemRepository.findAll()).thenReturn(List.of(item));

    mockMvc
        .perform(get("/api/v1/itens"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].valor").value(10.00))
        .andExpect(jsonPath("$[0].tipo").value("RECEITA"));
  }

  @Test
  @DisplayName("Deve criar item")
  void criarDeveRetornarCreated() throws Exception {
    Item item = new Item();
    item.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    item.setValor(new BigDecimal("120.50"));
    item.setData(LocalDate.of(2026, 3, 15));
    item.setHorarioCriacao(LocalDateTime.of(2026, 3, 15, 18, 0, 0));
    item.setCaminhoArquivoPdf("uploads/itens/item-criado.pdf");
    item.setTipo(TipoItem.DESPESA);
    when(itemArquivoStorageService.salvarPdf(org.mockito.ArgumentMatchers.any(byte[].class)))
        .thenReturn("uploads/itens/item-criado.pdf");
    when(itemRepository.save(org.mockito.ArgumentMatchers.any(Item.class))).thenReturn(item);

    mockMvc
        .perform(
            post("/api/v1/itens")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "valor":120.50,
                      "data":"2026-03-15",
                      "horarioCriacao":"2026-03-15T18:00:00",
                      "arquivoPdf":"cGRm",
                      "tipo":"DESPESA"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.valor").value(120.50))
        .andExpect(jsonPath("$.caminhoArquivoPdf").value("uploads/itens/item-criado.pdf"))
        .andExpect(jsonPath("$.tipo").value("DESPESA"));
  }

  @Test
  @DisplayName("Deve buscar item por id")
  void buscarPorIdDeveRetornarOk() throws Exception {
    UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
    Item item = new Item();
    item.setId(id);
    item.setValor(new BigDecimal("30.00"));
    item.setData(LocalDate.of(2026, 3, 15));
    item.setHorarioCriacao(LocalDateTime.of(2026, 3, 15, 8, 30, 0));
    item.setCaminhoArquivoPdf("uploads/itens/item-busca.pdf");
    item.setTipo(TipoItem.RECEITA);
    when(itemRepository.findById(id)).thenReturn(Optional.of(item));

    mockMvc
        .perform(get("/api/v1/itens/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tipo").value("RECEITA"));
  }
}
