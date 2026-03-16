package com.sistema_contabilidade.item.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.rbac.model.Role;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
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

    Item itemOperator = novoItem(TipoItem.RECEITA, "uploads/itens/op.pdf", criadorOperator);
    Item itemSupport = novoItem(TipoItem.DESPESA, "uploads/itens/sup.pdf", criadorSupport);
    itemRepository.save(itemOperator);
    itemRepository.save(itemSupport);

    List<Item> visiveis = itemRepository.findAllVisiveisPorRoleNomes(Set.of("OPERATOR"));

    assertEquals(1, visiveis.size());
    assertEquals("uploads/itens/op.pdf", visiveis.get(0).getCaminhoArquivoPdf());
  }

  private Usuario criarUsuarioComRole(String email, String roleNome) {
    Role role =
        roleRepository
            .findByNome(roleNome)
            .orElseGet(() -> roleRepository.save(novaRole(roleNome)));
    Usuario usuario = new Usuario();
    usuario.setNome(email);
    usuario.setEmail(email);
    usuario.setSenha("123456");
    usuario.getRoles().add(role);
    return usuarioRepository.save(usuario);
  }

  private Role novaRole(String nome) {
    Role role = new Role();
    role.setNome(nome);
    return role;
  }

  private Item novoItem(TipoItem tipoItem, String caminhoPdf, Usuario criadoPor) {
    Item item = new Item();
    item.setValor(new BigDecimal("50.00"));
    item.setData(LocalDate.of(2026, 3, 15));
    item.setHorarioCriacao(LocalDateTime.of(2026, 3, 15, 14, 10, 0));
    item.setCaminhoArquivoPdf(caminhoPdf);
    item.setTipo(tipoItem);
    item.setCriadoPor(criadoPor);
    return item;
  }
}
