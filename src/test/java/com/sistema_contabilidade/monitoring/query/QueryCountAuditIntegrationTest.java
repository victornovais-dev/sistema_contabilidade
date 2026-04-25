package com.sistema_contabilidade.monitoring.query;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.ItemArquivo;
import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.item.repository.ItemRepository;
import com.sistema_contabilidade.item.service.ArquivoStorageService;
import com.sistema_contabilidade.rbac.model.Role;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
import com.sistema_contabilidade.security.service.JwtService;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "app.query-monitor.threshold=15")
@AutoConfigureMockMvc
@DisplayName("Query count audit integration tests")
class QueryCountAuditIntegrationTest {

  private static final int QUERY_THRESHOLD = 15;

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private ItemRepository itemRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private UsuarioRepository usuarioRepository;

  @MockitoBean private ArquivoStorageService arquivoStorageService;

  @Test
  @DisplayName("GET /api/v1/itens deve permanecer dentro do limite de consultas")
  void listarItensDevePermanecerDentroDoLimiteDeConsultas() throws Exception {
    String email = "query-audit-" + UUID.randomUUID() + "@email.com";
    Usuario usuario = criarUsuarioAdmin(email);
    itemRepository.save(novoItem(usuario));
    String token = gerarTokenAdmin(email);

    assertGetEndpointDentroDoLimite(token, "/api/v1/itens");
  }

  @Test
  @DisplayName("GET /api/v1/itens/{id} deve permanecer dentro do limite de consultas")
  void buscarItemPorIdDevePermanecerDentroDoLimiteDeConsultas() throws Exception {
    String email = "query-audit-id-" + UUID.randomUUID() + "@email.com";
    Usuario usuario = criarUsuarioAdmin(email);
    Item item = itemRepository.save(novoItem(usuario));
    String token = gerarTokenAdmin(email);

    assertGetEndpointDentroDoLimite(token, "/api/v1/itens/{id}", item.getId());
  }

  @Test
  @DisplayName("GET /api/v1/itens/{id}/arquivos deve permanecer dentro do limite de consultas")
  void listarArquivosDoItemDevePermanecerDentroDoLimiteDeConsultas() throws Exception {
    String email = "query-audit-arquivos-" + UUID.randomUUID() + "@email.com";
    Usuario usuario = criarUsuarioAdmin(email);
    Item item = itemRepository.save(novoItemComArquivo(usuario, "uploads/itens/lista.pdf"));
    String token = gerarTokenAdmin(email);

    assertGetEndpointDentroDoLimite(token, "/api/v1/itens/{id}/arquivos", item.getId());
  }

  @Test
  @DisplayName(
      "GET /api/v1/itens/{id}/arquivos/download deve permanecer dentro do limite de consultas")
  void baixarArquivosDoItemDevePermanecerDentroDoLimiteDeConsultas() throws Exception {
    String caminhoArquivo = "uploads/itens/download-query-audit.pdf";
    String email = "query-audit-download-" + UUID.randomUUID() + "@email.com";
    Usuario usuario = criarUsuarioAdmin(email);
    Item item = itemRepository.save(novoItemComArquivo(usuario, caminhoArquivo));
    String token = gerarTokenAdmin(email);

    when(arquivoStorageService.carregarPdf(caminhoArquivo))
        .thenReturn("%PDF-1.4 query audit".getBytes(StandardCharsets.UTF_8));
    when(arquivoStorageService.resolverNomeArquivo(caminhoArquivo))
        .thenReturn("download-query-audit.pdf");

    assertGetEndpointDentroDoLimite(token, "/api/v1/itens/{id}/arquivos/download", item.getId());
  }

  @Test
  @DisplayName("GET /api/v1/relatorios/financeiro deve permanecer dentro do limite de consultas")
  void relatorioFinanceiroDevePermanecerDentroDoLimiteDeConsultas() throws Exception {
    String email = "query-audit-relatorio-" + UUID.randomUUID() + "@email.com";
    Usuario usuario = criarUsuarioAdmin(email);
    itemRepository.save(novoItem(usuario, TipoItem.RECEITA, "SERVICOS", new BigDecimal("350.00")));
    itemRepository.save(novoItem(usuario, TipoItem.DESPESA, "ALUGUEL", new BigDecimal("125.00")));
    String token = gerarTokenAdmin(email);

    assertGetEndpointDentroDoLimite(token, "/api/v1/relatorios/financeiro");
  }

  private int assertGetEndpointDentroDoLimite(String token, String endpoint, Object... uriVariables)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(get(endpoint, uriVariables).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(header().exists(QueryCountFilter.QUERY_COUNT_HEADER))
            .andReturn();

    String queryCountHeader = result.getResponse().getHeader(QueryCountFilter.QUERY_COUNT_HEADER);
    assertNotNull(queryCountHeader);
    int queryCount = Integer.parseInt(queryCountHeader);
    assertTrue(queryCount > 0, "GET " + endpoint + " deveria registrar ao menos uma consulta SQL");
    assertTrue(
        queryCount <= QUERY_THRESHOLD,
        "GET "
            + endpoint
            + " executou "
            + queryCount
            + " consultas SQL; revise possivel N+1 se passar de "
            + QUERY_THRESHOLD);
    return queryCount;
  }

  private Usuario criarUsuarioAdmin(String email) {
    Role adminRole =
        roleRepository
            .findByNome("ADMIN")
            .orElseGet(
                () -> {
                  Role role = new Role();
                  role.setNome("ADMIN");
                  return roleRepository.save(role);
                });

    Usuario usuario = new Usuario();
    usuario.setNome("Query Audit User");
    usuario.setEmail(email);
    usuario.setSenha("123456");
    usuario.getRoles().add(adminRole);
    return usuarioRepository.save(usuario);
  }

  private String gerarTokenAdmin(String email) {
    return jwtService.generateToken(
        User.withUsername(email).password("123456").roles("ADMIN").build());
  }

  private Item novoItem(Usuario usuario) {
    return novoItem(usuario, TipoItem.DESPESA, "SERVICOS", new BigDecimal("125.00"));
  }

  private Item novoItem(Usuario usuario, TipoItem tipo, String descricao, BigDecimal valor) {
    Item item = new Item();
    item.setValor(valor);
    item.setData(LocalDate.of(2026, 4, 22));
    item.setHorarioCriacao(LocalDateTime.of(2026, 4, 22, 10, 0));
    item.setCaminhoArquivoPdf("uploads/itens/query-audit.pdf");
    item.setTipo(tipo);
    item.setDescricao(descricao);
    item.setTipoDocumento("Nota fiscal");
    item.setRazaoSocialNome("EMPRESA QUERY AUDIT");
    item.setCnpjCpf("12.345.678/0001-99");
    item.setRoleNome("ADMIN");
    item.setCriadoPor(usuario);
    return item;
  }

  private Item novoItemComArquivo(Usuario usuario, String caminhoArquivo) {
    Item item = novoItem(usuario);
    item.setCaminhoArquivoPdf(caminhoArquivo);

    ItemArquivo arquivo = new ItemArquivo();
    arquivo.setCaminhoArquivoPdf(caminhoArquivo);
    arquivo.setItem(item);
    item.getArquivos().add(arquivo);

    return item;
  }
}
