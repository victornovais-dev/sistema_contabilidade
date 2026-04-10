package com.sistema_contabilidade.item.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sistema_contabilidade.home.dto.HomeLatestLaunchResponse;
import com.sistema_contabilidade.home.dto.HomeMonthlyBalanceRow;
import com.sistema_contabilidade.home.dto.HomeTypeTotalRow;
import com.sistema_contabilidade.item.dto.ItemListResponse;
import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.ItemArquivo;
import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.rbac.model.Role;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
import com.sistema_contabilidade.relatorio.dto.RelatorioItemDto;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
  @Autowired private UsuarioRepository usuarioRepository;
  @Autowired private RoleRepository roleRepository;

  @Test
  @DisplayName("Deve salvar e buscar item por id")
  void deveSalvarEBuscarItemPorId() {
    Item item = new Item();
    item.setValor(new BigDecimal("88.30"));
    item.setData(LocalDate.of(2026, 3, 15));
    item.setHorarioCriacao(LocalDateTime.of(2026, 3, 15, 14, 10, 0));
    item.setCaminhoArquivoPdf("uploads/itens/comprovante.pdf");
    item.setTipo(TipoItem.DESPESA);
    item.setDescricao("IMPOSTOS");
    item.setRazaoSocialNome("EMPRESA TESTE");
    item.setCnpjCpf("12.345.678/0001-99");
    item.setObservacao("Observacao de teste");
    item.setCriadoPor(criarUsuarioComRole("ana@email.com", "OPERATOR"));

    Item salvo = itemRepository.save(item);
    Optional<Item> encontrado = itemRepository.findById(salvo.getId());

    assertTrue(encontrado.isPresent());
    assertTrue(itemRepository.existsById(salvo.getId()));
  }

  @Test
  @DisplayName("Deve listar itens visiveis por intersecao de roles")
  void deveListarItensVisiveisPorIntersecaoDeRoles() {
    Usuario criadorOperator = criarUsuarioComRole("op@email.com", "OPERATOR");
    Usuario criadorSupport = criarUsuarioComRole("sup@email.com", "SUPPORT");

    Item itemOperator =
        novoItem(TipoItem.RECEITA, "uploads/itens/op.pdf", criadorOperator, "OPERATOR");
    Item itemSupport =
        novoItem(TipoItem.DESPESA, "uploads/itens/sup.pdf", criadorSupport, "SUPPORT");
    itemRepository.save(itemOperator);
    itemRepository.save(itemSupport);

    List<Item> visiveis = itemRepository.findAllVisiveisPorRoleNomes(Set.of("OPERATOR"));

    assertEquals(1, visiveis.size());
    assertEquals("uploads/itens/op.pdf", visiveis.get(0).getCaminhoArquivoPdf());
  }

  @Test
  @DisplayName("Deve filtrar itens pela role gravada no comprovante")
  void deveFiltrarItensPelaRoleGravadaNoComprovante() {
    Usuario criadorComDuasRoles = criarUsuarioComRoles("multi@email.com", "FINANCEIRO", "OPERADOR");

    Item itemFinanceiro =
        novoItem(
            TipoItem.RECEITA, "uploads/itens/financeiro.pdf", criadorComDuasRoles, "FINANCEIRO");
    Item itemOperador =
        novoItem(TipoItem.DESPESA, "uploads/itens/operador.pdf", criadorComDuasRoles, "OPERADOR");
    itemRepository.save(itemFinanceiro);
    itemRepository.save(itemOperador);

    List<Item> visiveis = itemRepository.findAllVisiveisPorRoleNome("FINANCEIRO");

    assertEquals(1, visiveis.size());
    assertEquals("uploads/itens/financeiro.pdf", visiveis.getFirst().getCaminhoArquivoPdf());
  }

  @Test
  @DisplayName("Nao deve incluir item sem role em filtro por role especifica")
  void naoDeveIncluirItemSemRoleEmFiltroPorRoleEspecifica() {
    Usuario criadorComDuasRoles = criarUsuarioComRoles("amb@email.com", "FINANCEIRO", "OPERADOR");

    Item itemSemRole =
        novoItem(TipoItem.RECEITA, "uploads/itens/ambiguo.pdf", criadorComDuasRoles, null);
    itemRepository.save(itemSemRole);

    List<Item> visiveis = itemRepository.findAllVisiveisPorRoleNome("FINANCEIRO");

    assertTrue(visiveis.isEmpty());
  }

  @Test
  @DisplayName("Deve listar resumo sem duplicar item com multiplos arquivos")
  void deveListarResumoSemDuplicarItemComMultiplosArquivos() {
    Usuario criador = criarUsuarioComRole("resumo@email.com", "FINANCEIRO");

    Item item = novoItem(TipoItem.DESPESA, "uploads/itens/resumo.pdf", criador, "FINANCEIRO");
    item.getArquivos().add(novoArquivo(item, "uploads/itens/resumo-1.pdf"));
    item.getArquivos().add(novoArquivo(item, "uploads/itens/resumo-2.pdf"));
    itemRepository.save(item);

    List<ItemListResponse> resumos =
        itemRepository.findResumoVisiveisPorRoleNomeOrderByHorarioCriacaoDesc("FINANCEIRO");

    assertEquals(1, resumos.size());
    assertEquals(item.getId(), resumos.getFirst().id());
    assertEquals("uploads/itens/resumo.pdf", resumos.getFirst().caminhoArquivoPdf());
    assertTrue(resumos.getFirst().temArquivos());
  }

  @Test
  @DisplayName("Deve buscar item por id sem duplicar arquivo quando criador possui multiplas roles")
  void deveBuscarItemPorIdSemDuplicarArquivoQuandoCriadorPossuiMultiplasRoles() {
    Usuario criador = criarUsuarioComRoles("arquivos@email.com", "FINANCEIRO", "OPERADOR", "ADMIN");

    Item item = novoItem(TipoItem.RECEITA, "uploads/itens/principal.pdf", criador, "FINANCEIRO");
    item.getArquivos().add(novoArquivo(item, "uploads/itens/unico.pdf"));
    Item salvo = itemRepository.save(item);

    Item encontrado = itemRepository.findByIdComCriadorERoles(salvo.getId()).orElseThrow();

    assertEquals(1, encontrado.getArquivos().size());
    assertEquals(
        "uploads/itens/unico.pdf", encontrado.getArquivos().getFirst().getCaminhoArquivoPdf());
  }

  @Test
  @DisplayName("Deve agregar totais por tipo para dashboard da home")
  void deveAgregarTotaisPorTipoParaDashboardDaHome() {
    Usuario criador = criarUsuarioComRole("dashboard@email.com", "FINANCEIRO");
    itemRepository.save(
        novoItem(TipoItem.RECEITA, "uploads/itens/receita.pdf", criador, "FINANCEIRO"));
    itemRepository.save(
        novoItem(TipoItem.DESPESA, "uploads/itens/despesa.pdf", criador, "FINANCEIRO"));

    List<HomeTypeTotalRow> totais = itemRepository.findTypeTotalsByRoleNome("FINANCEIRO");

    assertEquals(2, totais.size());
    assertTrue(totais.stream().anyMatch(row -> row.tipo() == TipoItem.RECEITA));
    assertTrue(totais.stream().anyMatch(row -> row.tipo() == TipoItem.DESPESA));
  }

  @Test
  @DisplayName("Deve retornar ultimos lancamentos ordenados por horario de criacao")
  void deveRetornarUltimosLancamentosOrdenadosPorHorarioCriacao() {
    Usuario criador = criarUsuarioComRole("latest@email.com", "FINANCEIRO");
    Item antigo = novoItem(TipoItem.DESPESA, "uploads/itens/antigo.pdf", criador, "FINANCEIRO");
    antigo.setHorarioCriacao(LocalDateTime.of(2026, 4, 7, 9, 0));
    antigo.setRazaoSocialNome("ANTIGO");
    Item recente = novoItem(TipoItem.RECEITA, "uploads/itens/recente.pdf", criador, "FINANCEIRO");
    recente.setHorarioCriacao(LocalDateTime.of(2026, 4, 8, 9, 0));
    recente.setRazaoSocialNome("RECENTE");
    itemRepository.save(antigo);
    itemRepository.save(recente);

    List<HomeLatestLaunchResponse> latest =
        itemRepository.findLatestLaunchesByRoleNome(
            "FINANCEIRO", org.springframework.data.domain.PageRequest.of(0, 4));

    assertEquals(2, latest.size());
    assertEquals("RECENTE", latest.getFirst().razaoSocialNome());
  }

  @Test
  @DisplayName("Deve agregar linhas mensais para grafico da home")
  void deveAgregarLinhasMensaisParaGraficoDaHome() {
    Usuario criador = criarUsuarioComRole("grafico@email.com", "FINANCEIRO");
    Item item = novoItem(TipoItem.RECEITA, "uploads/itens/grafico.pdf", criador, "FINANCEIRO");
    item.setData(LocalDate.of(2026, 4, 8));
    itemRepository.save(item);

    List<HomeMonthlyBalanceRow> rows =
        itemRepository.findMonthlyBalanceRowsSinceByRoleNome(
            "FINANCEIRO", LocalDate.of(2026, 1, 1));

    assertEquals(1, rows.size());
    assertEquals(2026, rows.getFirst().year());
    assertEquals(4, rows.getFirst().month());
    assertEquals(TipoItem.RECEITA, rows.getFirst().tipo());
  }

  @Test
  @DisplayName("Deve retornar itens de relatorio filtrados por role e ordenados no banco")
  void deveRetornarItensDeRelatorioFiltradosPorRoleEOrdenadosNoBanco() {
    Usuario criador = criarUsuarioComRole("relatorio@email.com", "FINANCEIRO");

    Item antigo =
        novoItem(TipoItem.DESPESA, "uploads/itens/relatorio-antigo.pdf", criador, "FINANCEIRO");
    antigo.setData(LocalDate.of(2026, 4, 7));
    antigo.setHorarioCriacao(LocalDateTime.of(2026, 4, 7, 9, 0));
    antigo.setDescricao("IMPOSTOS");

    Item recente =
        novoItem(TipoItem.RECEITA, "uploads/itens/relatorio-recente.pdf", criador, "FINANCEIRO");
    recente.setData(LocalDate.of(2026, 4, 8));
    recente.setHorarioCriacao(LocalDateTime.of(2026, 4, 8, 11, 30));
    recente.setDescricao("SERVICOS");

    itemRepository.save(antigo);
    itemRepository.save(recente);

    List<RelatorioItemDto> itens =
        itemRepository.findRelatorioItensByRoleNomeOrderByDataDescHorarioCriacaoDesc("FINANCEIRO");

    assertEquals(2, itens.size());
    assertEquals(TipoItem.RECEITA, itens.getFirst().tipo());
    assertEquals("SERVICOS", itens.getFirst().descricao());
    assertEquals(TipoItem.DESPESA, itens.get(1).tipo());
  }

  @Test
  @DisplayName("Deve retornar itens de relatorio visiveis por conjunto de roles")
  void deveRetornarItensDeRelatorioVisiveisPorConjuntoDeRoles() {
    Usuario criadorFinanceiro = criarUsuarioComRole("financeiro-rel@email.com", "FINANCEIRO");
    Usuario criadorOperador = criarUsuarioComRole("operador-rel@email.com", "OPERADOR");

    itemRepository.save(
        novoItem(TipoItem.RECEITA, "uploads/itens/fin-rel.pdf", criadorFinanceiro, "FINANCEIRO"));
    itemRepository.save(
        novoItem(TipoItem.DESPESA, "uploads/itens/op-rel.pdf", criadorOperador, "OPERADOR"));

    List<RelatorioItemDto> itens =
        itemRepository.findRelatorioItensByRoleNomesOrderByDataDescHorarioCriacaoDesc(
            Set.of("FINANCEIRO"));

    assertEquals(1, itens.size());
    assertEquals(TipoItem.RECEITA, itens.getFirst().tipo());
  }

  private Usuario criarUsuarioComRole(String email, String roleNome) {
    return criarUsuarioComRoles(email, roleNome);
  }

  private Usuario criarUsuarioComRoles(String email, String... roleNomes) {
    Usuario usuario = new Usuario();
    usuario.setNome(email);
    usuario.setEmail(email);
    usuario.setSenha("123456");
    for (String roleNome : roleNomes) {
      Role role =
          roleRepository
              .findByNome(roleNome)
              .orElseGet(() -> roleRepository.save(novaRole(roleNome)));
      usuario.getRoles().add(role);
    }
    return usuarioRepository.save(usuario);
  }

  private Role novaRole(String nome) {
    Role role = new Role();
    role.setNome(nome);
    return role;
  }

  private Item novoItem(TipoItem tipoItem, String caminhoPdf, Usuario criadoPor, String roleNome) {
    Item item = new Item();
    item.setValor(new BigDecimal("50.00"));
    item.setData(LocalDate.of(2026, 3, 15));
    item.setHorarioCriacao(LocalDateTime.of(2026, 3, 15, 14, 10, 0));
    item.setCaminhoArquivoPdf(caminhoPdf);
    item.setTipo(tipoItem);
    item.setDescricao("SERVICOS");
    item.setRazaoSocialNome("EMPRESA TESTE");
    item.setCnpjCpf("12.345.678/0001-99");
    item.setObservacao("Observacao de teste");
    item.setRoleNome(roleNome);
    item.setCriadoPor(criadoPor);
    return item;
  }

  private ItemArquivo novoArquivo(Item item, String caminhoPdf) {
    ItemArquivo arquivo = new ItemArquivo();
    arquivo.setItem(item);
    arquivo.setCaminhoArquivoPdf(caminhoPdf);
    return arquivo;
  }
}
