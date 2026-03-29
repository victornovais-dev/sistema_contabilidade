package com.sistema_contabilidade.item.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.ItemArquivo;
import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.item.repository.ItemArquivoRepository;
import com.sistema_contabilidade.item.repository.ItemRepository;
import com.sistema_contabilidade.item.service.ItemArquivoStorageService;
import com.sistema_contabilidade.rbac.model.Role;
import com.sistema_contabilidade.security.service.CustomUserDetailsService;
import com.sistema_contabilidade.security.service.JwtService;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ItemController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ItemController WebMvc tests")
class ItemControllerWebMvcTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ItemRepository itemRepository;
  @MockitoBean private ItemArquivoRepository itemArquivoRepository;
  @MockitoBean private ItemArquivoStorageService itemArquivoStorageService;
  @MockitoBean private UsuarioRepository usuarioRepository;
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
    item.setDescricao("ALUGUEL");
    item.setRazaoSocialNome("EMPRESA TESTE");
    item.setCnpjCpf("12.345.678/0001-99");
    item.setObservacao("Observacao de teste");
    ItemArquivo arquivoLista = new ItemArquivo();
    arquivoLista.setCaminhoArquivoPdf("uploads/itens/item-lista.pdf");
    arquivoLista.setItem(item);
    item.getArquivos().add(arquivoLista);
    when(itemRepository.findAll()).thenReturn(List.of(item));

    mockMvc
        .perform(get("/api/v1/itens").with(authComRoles("admin@email.com", "ADMIN")))
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
    item.setDescricao("SERVICOS");
    item.setRazaoSocialNome("FORNECEDOR TESTE");
    item.setCnpjCpf("123.456.789-00");
    item.setObservacao("Observacao do teste");
    ItemArquivo arquivoCriado = new ItemArquivo();
    arquivoCriado.setCaminhoArquivoPdf("uploads/itens/item-criado.pdf");
    arquivoCriado.setItem(item);
    item.getArquivos().add(arquivoCriado);
    when(itemArquivoStorageService.salvarPdfs(
            org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList()))
        .thenReturn(List.of("uploads/itens/item-criado.pdf"));
    when(itemRepository.save(org.mockito.ArgumentMatchers.any(Item.class))).thenReturn(item);
    when(usuarioRepository.findByEmail("operador@email.com"))
        .thenReturn(Optional.of(usuarioComRoles("OPERADOR", "OPERATOR")));

    mockMvc
        .perform(
            post("/api/v1/itens")
                .with(authComRoles("operador@email.com", "OPERATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "valor":120.50,
                      "data":"2026-03-15",
                      "horarioCriacao":"2026-03-15T18:00:00",
                      "arquivosPdf":["cGRm","cGRmMg=="],
                      "nomesArquivos":["documento-1.pdf","documento-2.pdf"],
                      "tipo":"DESPESA",
                      "descricao":"SERVICOS",
                      "razaoSocialNome":"FORNECEDOR TESTE",
                      "cnpjCpf":"123.456.789-00",
                      "observacao":"Observacao do teste"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.valor").value(120.50))
        .andExpect(jsonPath("$.caminhoArquivoPdf").value("uploads/itens/item-criado.pdf"))
        .andExpect(jsonPath("$.tipo").value("DESPESA"))
        .andExpect(jsonPath("$.descricao").value("SERVICOS"))
        .andExpect(jsonPath("$.razaoSocialNome").value("FORNECEDOR TESTE"))
        .andExpect(jsonPath("$.cnpjCpf").value("123.456.789-00"))
        .andExpect(jsonPath("$.observacao").value("Observacao do teste"))
        .andExpect(jsonPath("$.arquivosPdf[0]").value("uploads/itens/item-criado.pdf"));
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
    item.setDescricao("ALUGUEL");
    item.setRazaoSocialNome("EMPRESA TESTE");
    item.setCnpjCpf("12.345.678/0001-99");
    item.setObservacao("Observacao de teste");
    ItemArquivo arquivoBusca = new ItemArquivo();
    arquivoBusca.setCaminhoArquivoPdf("uploads/itens/item-busca.pdf");
    arquivoBusca.setItem(item);
    item.getArquivos().add(arquivoBusca);
    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.of(item));

    mockMvc
        .perform(get("/api/v1/itens/{id}", id).with(authComRoles("admin@email.com", "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tipo").value("RECEITA"));
  }

  @Test
  @DisplayName("Deve retornar 404 ao buscar item inexistente por id")
  void buscarPorIdDeveRetornarNotFound() throws Exception {
    UUID id = UUID.fromString("44444444-4444-4444-4444-444444444444");
    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/api/v1/itens/{id}", id).with(authComRoles("admin@email.com", "ADMIN")))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Deve retornar 403 ao buscar item sem role em comum com criador")
  void buscarPorIdDeveRetornarForbiddenSemIntersecaoDeRoles() throws Exception {
    UUID id = UUID.fromString("99999999-1111-2222-3333-444444444444");
    Item item = new Item();
    item.setId(id);
    item.setTipo(TipoItem.RECEITA);
    item.setCriadoPor(usuarioComRoles("criador", "SUPPORT"));

    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.of(item));
    when(usuarioRepository.findByEmail("operador@email.com"))
        .thenReturn(Optional.of(usuarioComRoles("operador", "OPERATOR")));

    mockMvc
        .perform(get("/api/v1/itens/{id}", id).with(authComRoles("operador@email.com", "OPERATOR")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Deve baixar arquivo PDF do item")
  void baixarArquivoDeveRetornarPdf() throws Exception {
    UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");
    Item item = new Item();
    item.setId(id);
    item.setValor(new BigDecimal("99.99"));
    item.setData(LocalDate.of(2026, 3, 16));
    item.setHorarioCriacao(LocalDateTime.of(2026, 3, 16, 10, 0, 0));
    item.setCaminhoArquivoPdf("uploads/itens/comprovante-1.pdf");
    item.setTipo(TipoItem.DESPESA);
    item.setDescricao("IMPOSTOS");
    item.setRazaoSocialNome("EMPRESA TESTE");
    item.setCnpjCpf("12.345.678/0001-99");
    item.setObservacao("Observacao de teste");
    ItemArquivo arquivoDownload = new ItemArquivo();
    arquivoDownload.setCaminhoArquivoPdf("uploads/itens/comprovante-1.pdf");
    arquivoDownload.setItem(item);
    item.getArquivos().add(arquivoDownload);
    byte[] conteudoPdf = "pdf-teste".getBytes();

    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.of(item));
    when(itemArquivoStorageService.carregarPdf("uploads/itens/comprovante-1.pdf"))
        .thenReturn(conteudoPdf);

    mockMvc
        .perform(
            get("/api/v1/itens/{id}/arquivo", id).with(authComRoles("admin@email.com", "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
        .andExpect(
            header().string("Content-Disposition", "attachment; filename=\"comprovante-1.pdf\""))
        .andExpect(content().bytes(conteudoPdf));
  }

  @Test
  @DisplayName("Deve retornar 404 no download quando item nao existe")
  void baixarArquivoDeveRetornarNotFoundQuandoItemNaoExiste() throws Exception {
    UUID id = UUID.fromString("33333333-3333-3333-3333-333333333333");
    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.empty());

    mockMvc
        .perform(
            get("/api/v1/itens/{id}/arquivo", id).with(authComRoles("admin@email.com", "ADMIN")))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Deve atualizar item")
  void atualizarDeveRetornarOk() throws Exception {
    UUID id = UUID.fromString("55555555-5555-5555-5555-555555555555");
    Item item = new Item();
    item.setId(id);
    item.setValor(new BigDecimal("10.00"));
    item.setData(LocalDate.of(2026, 3, 16));
    item.setHorarioCriacao(LocalDateTime.of(2026, 3, 16, 11, 0, 0));
    item.setCaminhoArquivoPdf("uploads/itens/antigo.pdf");
    item.setTipo(TipoItem.RECEITA);
    item.setDescricao("ALUGUEL");
    item.setRazaoSocialNome("EMPRESA TESTE");
    item.setCnpjCpf("12.345.678/0001-99");
    item.setObservacao("Observacao antiga");
    ItemArquivo arquivoAntigo = new ItemArquivo();
    arquivoAntigo.setCaminhoArquivoPdf("uploads/itens/antigo.pdf");
    arquivoAntigo.setItem(item);
    item.getArquivos().add(arquivoAntigo);
    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.of(item));
    when(itemArquivoStorageService.salvarPdfs(
            org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList()))
        .thenReturn(List.of("uploads/itens/novo.pdf"));
    when(itemRepository.save(org.mockito.ArgumentMatchers.any(Item.class))).thenReturn(item);

    mockMvc
        .perform(
            put("/api/v1/itens/{id}", id)
                .with(authComRoles("admin@email.com", "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "valor":250.10,
                      "data":"2026-03-16",
                      "horarioCriacao":"2026-03-16T11:10:00",
                      "arquivosPdf":["cGRm"],
                      "nomesArquivos":["novo.pdf"],
                      "tipo":"DESPESA",
                      "descricao":"OUTROS",
                      "razaoSocialNome":"EMPRESA TESTE",
                      "cnpjCpf":"12.345.678/0001-99",
                      "observacao":"Observacao nova"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.caminhoArquivoPdf").value("uploads/itens/novo.pdf"));
  }

  @Test
  @DisplayName("Deve retornar 404 ao atualizar item inexistente")
  void atualizarDeveRetornarNotFound() throws Exception {
    UUID id = UUID.fromString("66666666-6666-6666-6666-666666666666");
    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.empty());

    mockMvc
        .perform(
            put("/api/v1/itens/{id}", id)
                .with(authComRoles("admin@email.com", "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "valor":250.10,
                      "data":"2026-03-16",
                      "horarioCriacao":"2026-03-16T11:10:00",
                      "arquivosPdf":["cGRm"],
                      "nomesArquivos":["novo.pdf"],
                      "tipo":"DESPESA",
                      "descricao":"OUTROS",
                      "razaoSocialNome":"EMPRESA TESTE",
                      "cnpjCpf":"12.345.678/0001-99",
                      "observacao":"Observacao nova"
                    }
                    """))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Deve deletar item")
  void deletarDeveRetornarNoContent() throws Exception {
    UUID id = UUID.fromString("77777777-7777-7777-7777-777777777777");
    Item item = new Item();
    item.setId(id);
    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.of(item));

    mockMvc
        .perform(delete("/api/v1/itens/{id}", id).with(authComRoles("admin@email.com", "ADMIN")))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("Deve retornar 404 ao deletar item inexistente")
  void deletarDeveRetornarNotFound() throws Exception {
    UUID id = UUID.fromString("88888888-8888-8888-8888-888888888888");
    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.empty());

    mockMvc
        .perform(delete("/api/v1/itens/{id}", id).with(authComRoles("admin@email.com", "ADMIN")))
        .andExpect(status().isNotFound());
  }

  private RequestPostProcessor authComRoles(String email, String... roles) {
    return request -> {
      List<SimpleGrantedAuthority> authorities = new ArrayList<>();
      for (String role : roles) {
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
      }
      request.setUserPrincipal(
          new UsernamePasswordAuthenticationToken(email, "senha", authorities));
      return request;
    };
  }

  private Usuario usuarioComRoles(String nome, String... roleNomes) {
    Usuario usuario = new Usuario();
    usuario.setNome(nome);
    usuario.setEmail(nome.toLowerCase() + "@email.com");
    for (String roleNome : roleNomes) {
      Role role = new Role();
      role.setNome(roleNome);
      usuario.getRoles().add(role);
    }
    return usuario;
  }

  @Test
  @DisplayName("Deve listar arquivos do item")
  void listarArquivosDeveRetornarOk() throws Exception {
    UUID id = UUID.fromString("99999999-2222-3333-4444-555555555555");
    Item item = new Item();
    item.setId(id);
    item.setTipo(TipoItem.RECEITA);
    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.of(item));

    ItemArquivo arquivo = new ItemArquivo();
    arquivo.setId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    arquivo.setCaminhoArquivoPdf("uploads/itens/extra.pdf");
    arquivo.setItem(item);
    item.getArquivos().add(arquivo);

    mockMvc
        .perform(
            get("/api/v1/itens/{id}/arquivos", id).with(authComRoles("admin@email.com", "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].caminhoArquivoPdf").value("uploads/itens/extra.pdf"));
  }

  @Test
  @DisplayName("Deve adicionar arquivos ao item")
  void adicionarArquivosDeveRetornarOk() throws Exception {
    UUID id = UUID.fromString("99999999-aaaa-bbbb-cccc-111111111111");
    Item item = new Item();
    item.setId(id);
    item.setTipo(TipoItem.RECEITA);
    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.of(item));
    when(itemArquivoStorageService.salvarPdfs(
            org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList()))
        .thenReturn(List.of("uploads/itens/novo-1.pdf", "uploads/itens/novo-2.pdf"));
    when(itemRepository.save(org.mockito.ArgumentMatchers.any(Item.class))).thenReturn(item);

    mockMvc
        .perform(
            post("/api/v1/itens/{id}/arquivos", id)
                .with(authComRoles("admin@email.com", "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "arquivosPdf":["cGRm","cGRm"],
                      "nomesArquivos":["extra-1.pdf","extra-2.pdf"]
                    }
                    """))
        .andExpect(status().isOk());
  }
}
