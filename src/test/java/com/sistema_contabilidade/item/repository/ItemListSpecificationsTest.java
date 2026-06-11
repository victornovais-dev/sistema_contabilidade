package com.sistema_contabilidade.item.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.rbac.model.Role;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Sort;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@DisplayName("ItemListSpecifications DataJpa tests")
class ItemListSpecificationsTest {

  @Autowired private ItemRepository itemRepository;
  @Autowired private UsuarioRepository usuarioRepository;
  @Autowired private RoleRepository roleRepository;

  @Test
  @DisplayName("Deve retornar nulo quando nenhum filtro for informado")
  void deveRetornarNuloQuandoNenhumFiltroForInformado() {
    assertNull(ItemListSpecifications.forList(null, null, null, null, null, null));
  }

  @Test
  @DisplayName("Deve ignorar filtro de role quando apenas valores invalidos forem informados")
  void deveIgnorarFiltroDeRoleQuandoApenasValoresInvalidosForemInformados() {
    Set<String> roleNomes = new HashSet<>();
    roleNomes.add("   ");
    roleNomes.add(null);

    assertNull(ItemListSpecifications.forList(roleNomes, null, null, null, null, null));
  }

  @Test
  @DisplayName("Deve aplicar todos os filtros da listagem com normalizacao")
  void deveAplicarTodosOsFiltrosDaListagemComNormalizacao() {
    Usuario criador = criarUsuarioComRole("filtros@email.com", "FINANCEIRO");

    Item elegivel =
        novoItem(criador, "FINANCEIRO", TipoItem.DESPESA, "SERVICOS", "Fornecedor Alpha");
    elegivel.setData(LocalDate.of(2026, 4, 10));
    elegivel.setHorarioCriacao(LocalDateTime.of(2026, 4, 10, 12, 0));

    Item descricaoDiferente =
        novoItem(criador, "FINANCEIRO", TipoItem.DESPESA, "OUTROS", "Fornecedor Alpha");
    descricaoDiferente.setData(LocalDate.of(2026, 4, 10));

    Item roleDiferente =
        novoItem(criador, "OPERADOR", TipoItem.DESPESA, "SERVICOS", "Fornecedor Alpha");
    roleDiferente.setData(LocalDate.of(2026, 4, 10));

    Item tipoDiferente =
        novoItem(criador, "FINANCEIRO", TipoItem.RECEITA, "SERVICOS", "Fornecedor Alpha");
    tipoDiferente.setData(LocalDate.of(2026, 4, 10));

    Item dataDiferente =
        novoItem(criador, "FINANCEIRO", TipoItem.DESPESA, "SERVICOS", "Fornecedor Alpha");
    dataDiferente.setData(LocalDate.of(2026, 5, 1));

    Item razaoDiferente =
        novoItem(criador, "FINANCEIRO", TipoItem.DESPESA, "SERVICOS", "Fornecedor Beta");
    razaoDiferente.setData(LocalDate.of(2026, 4, 10));

    itemRepository.saveAll(
        List.of(
            elegivel,
            descricaoDiferente,
            roleDiferente,
            tipoDiferente,
            dataDiferente,
            razaoDiferente));

    Set<String> roleNomes = new HashSet<>();
    roleNomes.add(" financeiro ");
    roleNomes.add(" ");
    roleNomes.add(null);

    List<Item> itens =
        itemRepository.findAll(
            ItemListSpecifications.forList(
                roleNomes,
                TipoItem.DESPESA,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                " servicos ",
                " fornecedor alpha "),
            Sort.by(Sort.Order.desc("horarioCriacao"), Sort.Order.desc("id")));

    assertEquals(1, itens.size());
    assertEquals(elegivel.getId(), itens.getFirst().getId());
  }

  @Test
  @DisplayName("Deve normalizar acentos e pontuacao no filtro de razao social")
  void deveNormalizarAcentosEPontuacaoNoFiltroDeRazaoSocial() {
    Usuario criador = criarUsuarioComRole("curinga@email.com", "FINANCEIRO");

    Item comLiteral =
        novoItem(criador, "FINANCEIRO", TipoItem.DESPESA, "SERVICOS", "Fornecedor_100%\\Acao Ltda");
    Item semLiteral =
        novoItem(criador, "FINANCEIRO", TipoItem.DESPESA, "SERVICOS", "Fornecedor Beta Ltda");

    itemRepository.saveAll(List.of(comLiteral, semLiteral));

    List<Item> itens =
        itemRepository.findAll(
            ItemListSpecifications.forList(
                Set.of("FINANCEIRO"), null, null, null, null, " fornecedor 100 acao "));

    assertEquals(1, itens.size());
    assertEquals(comLiteral.getId(), itens.getFirst().getId());
  }

  private Usuario criarUsuarioComRole(String email, String roleNome) {
    Usuario usuario = new Usuario();
    usuario.setNome(email);
    usuario.setEmail(email);
    usuario.setSenha("123456");
    Role role =
        roleRepository
            .findByNome(roleNome)
            .orElseGet(
                () -> {
                  Role novaRole = new Role();
                  novaRole.setNome(roleNome);
                  return roleRepository.save(novaRole);
                });
    usuario.getRoles().add(role);
    return usuarioRepository.save(usuario);
  }

  private Item novoItem(
      Usuario criadoPor, String roleNome, TipoItem tipo, String descricao, String razaoSocialNome) {
    Item item = new Item();
    item.setValor(new BigDecimal("50.00"));
    item.setData(LocalDate.of(2026, 4, 10));
    item.setHorarioCriacao(LocalDateTime.of(2026, 4, 10, 9, 0));
    item.setCaminhoArquivoPdf("uploads/itens/teste.pdf");
    item.setTipo(tipo);
    item.setDescricao(descricao);
    item.setRazaoSocialNome(razaoSocialNome);
    item.setCnpjCpf("12.345.678/0001-99");
    item.setObservacao("Observacao");
    item.setRoleNome(roleNome);
    item.setCriadoPor(criadoPor);
    return item;
  }
}
