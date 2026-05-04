package com.sistema_contabilidade.item.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.item.repository.ItemRepository;
import com.sistema_contabilidade.notificacao.service.NotificacaoService;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("Item legacy verification integration tests")
class ItemVerificacaoLegacyIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ItemRepository itemRepository;
  @Autowired private DataSource dataSource;

  @MockitoBean private NotificacaoService notificacaoService;

  @Test
  @DisplayName("Deve marcar item legado como verificado na primeira tentativa")
  void deveMarcarItemLegadoComoVerificadoNaPrimeiraTentativa() throws Exception {
    UUID itemId = UUID.fromString("11111111-2222-3333-4444-555555555551");

    try {
      inserirItemLegado(itemId, false);

      MvcResult result =
          mockMvc
              .perform(
                  patch("/api/v1/itens/{id}/verificacao", itemId)
                      .principal(adminAuthentication())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
                          {"verificado":true}
                          """))
              .andReturn();

      assertEquals(200, result.getResponse().getStatus());
      assertTrue(result.getResponse().getContentAsString().contains("\"verificado\":true"));

      Item atualizado = itemRepository.findById(itemId).orElseThrow();
      assertTrue(atualizado.isVerificado());
      assertEquals(1L, atualizado.getVersion());
    } finally {
      deletarItem(itemId);
    }
  }

  @Test
  @DisplayName("Deve desmarcar item legado verificado na primeira tentativa")
  void deveDesmarcarItemLegadoVerificadoNaPrimeiraTentativa() throws Exception {
    UUID itemId = UUID.fromString("11111111-2222-3333-4444-555555555552");

    try {
      inserirItemLegado(itemId, true);

      MvcResult result =
          mockMvc
              .perform(
                  patch("/api/v1/itens/{id}/verificacao", itemId)
                      .principal(adminAuthentication())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
                          {"verificado":false}
                          """))
              .andReturn();

      assertEquals(200, result.getResponse().getStatus());
      assertTrue(result.getResponse().getContentAsString().contains("\"verificado\":false"));

      Item atualizado = itemRepository.findById(itemId).orElseThrow();
      assertFalse(atualizado.isVerificado());
      assertEquals(1L, atualizado.getVersion());
    } finally {
      deletarItem(itemId);
    }
  }

  private UsernamePasswordAuthenticationToken adminAuthentication() {
    return new UsernamePasswordAuthenticationToken(
        "admin@email.com", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
  }

  private void inserirItemLegado(UUID itemId, boolean verificado) throws Exception {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                """
                insert into itens (
                  id,
                  version,
                  valor,
                  data,
                  horario_criacao,
                  caminho_arquivo_pdf,
                  descricao,
                  tipo_documento,
                  numero_documento,
                  razao_social,
                  cnpj_cpf,
                  observacao,
                  verificado,
                  role_nome,
                  tipo,
                  criado_por_id
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
      statement.setObject(1, itemId);
      statement.setObject(2, null);
      statement.setBigDecimal(3, new BigDecimal("100.00"));
      statement.setObject(4, LocalDate.of(2026, 4, 28));
      statement.setObject(5, LocalDateTime.of(2026, 4, 28, 0, 54));
      statement.setString(6, "uploads/itens/legado-toggle.pdf");
      statement.setString(7, "Hospedagem");
      statement.setString(8, null);
      statement.setString(9, null);
      statement.setString(10, "HOTEL XPTO");
      statement.setString(11, "21.039.861/0298-36");
      statement.setString(12, null);
      statement.setBoolean(13, verificado);
      statement.setString(14, "ANDRE DO PRADO");
      statement.setString(15, TipoItem.DESPESA.name());
      statement.setObject(16, null);
      statement.executeUpdate();
    }
  }

  private void deletarItem(UUID itemId) throws Exception {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement =
            connection.prepareStatement("delete from itens where id = ?")) {
      statement.setObject(1, itemId);
      statement.executeUpdate();
    }
  }
}
