package com.sistema_contabilidade.relatorio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.item.repository.ItemRepository;
import com.sistema_contabilidade.rbac.model.Role;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
import com.sistema_contabilidade.relatorio.dto.RelatorioFinanceiroResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@DisplayName("RelatorioFinanceiroService unit tests")
class RelatorioFinanceiroServiceTest {

  @Mock private ItemRepository itemRepository;
  @Mock private RoleRepository roleRepository;

  @InjectMocks private RelatorioFinanceiroService relatorioFinanceiroService;

  @Test
  @DisplayName("Deve gerar relatorio com visao completa para admin e orcamento de 20 milhoes")
  void deveGerarRelatorioParaAdmin() {
    ReflectionTestUtils.setField(
        relatorioFinanceiroService, "roleBudgetConfig", "ADMIN:20000000,OPERATOR:5000000");
    ReflectionTestUtils.setField(
        relatorioFinanceiroService, "defaultBudget", new BigDecimal("1000000"));

    Item receita = criarItem(TipoItem.RECEITA, "1000000.00", "2026-03-10T09:00:00");
    Item despesa = criarItem(TipoItem.DESPESA, "2000000.00", "2026-03-11T10:00:00");
    when(itemRepository.findAll()).thenReturn(List.of(receita, despesa));

    var auth =
        new UsernamePasswordAuthenticationToken(
            "admin@email.com", "senha", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

    RelatorioFinanceiroResponse response = relatorioFinanceiroService.gerar(auth);

    assertEquals(new BigDecimal("20000000"), response.orcamento());
    assertEquals(new BigDecimal("1000000.00"), response.totalReceitas());
    assertEquals(new BigDecimal("2000000.00"), response.totalDespesas());
    assertEquals(new BigDecimal("-1000000.00"), response.saldoFinal());
    assertEquals(new BigDecimal("10.00"), response.utilizadoPercentual());
    assertEquals(1, response.receitas().size());
    assertEquals(1, response.despesas().size());
    verify(itemRepository).findAll();
    verify(itemRepository, never()).findAllVisiveisPorRoleNomes(anySet());
  }

  @Test
  @DisplayName("Deve gerar relatorio com itens visiveis por role para usuario nao admin")
  void deveGerarRelatorioParaNaoAdmin() {
    ReflectionTestUtils.setField(
        relatorioFinanceiroService, "roleBudgetConfig", "ADMIN:20000000,OPERATOR:5000000");
    ReflectionTestUtils.setField(relatorioFinanceiroService, "defaultBudget", BigDecimal.ZERO);

    Item receita = criarItem(TipoItem.RECEITA, "500.00", "2026-03-12T08:30:00");
    when(itemRepository.findAllVisiveisPorRoleNomes(Set.of("OPERATOR")))
        .thenReturn(List.of(receita));

    var auth =
        new UsernamePasswordAuthenticationToken(
            "operador@email.com", "senha", List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));

    RelatorioFinanceiroResponse response = relatorioFinanceiroService.gerar(auth);

    assertEquals(new BigDecimal("5000000"), response.orcamento());
    assertEquals(new BigDecimal("500.00"), response.totalReceitas());
    assertEquals(BigDecimal.ZERO, response.totalDespesas());
    assertEquals(new BigDecimal("500.00"), response.saldoFinal());
    assertEquals(BigDecimal.ZERO.setScale(2), response.utilizadoPercentual());
    verify(itemRepository).findAllVisiveisPorRoleNomes(Set.of("OPERATOR"));
    verify(itemRepository, never()).findAll();
  }

  @Test
  @DisplayName("Deve permitir admin filtrar relatorio por role informada")
  void deveGerarRelatorioFiltradoPorRoleQuandoAdmin() {
    ReflectionTestUtils.setField(
        relatorioFinanceiroService, "roleBudgetConfig", "ADMIN:20000000,OPERATOR:5000000");
    ReflectionTestUtils.setField(relatorioFinanceiroService, "defaultBudget", BigDecimal.ZERO);

    Item despesa = criarItem(TipoItem.DESPESA, "1200.00", "2026-03-12T08:30:00");
    when(itemRepository.findAllVisiveisPorRoleNomes(Set.of("OPERATOR")))
        .thenReturn(List.of(despesa));

    var auth =
        new UsernamePasswordAuthenticationToken(
            "admin@email.com", "senha", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

    RelatorioFinanceiroResponse response = relatorioFinanceiroService.gerar(auth, "operator");

    assertEquals(new BigDecimal("5000000"), response.orcamento());
    assertEquals(new BigDecimal("1200.00"), response.totalDespesas());
    assertEquals(new BigDecimal("0.02"), response.utilizadoPercentual());
    verify(itemRepository).findAllVisiveisPorRoleNomes(Set.of("OPERATOR"));
    verify(itemRepository, never()).findAll();
  }

  @Test
  @DisplayName("Deve bloquear filtro por role quando usuario nao e admin")
  void deveBloquearFiltroPorRoleParaNaoAdmin() {
    var auth =
        new UsernamePasswordAuthenticationToken(
            "operador@email.com", "senha", List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));

    ResponseStatusException exception =
        org.junit.jupiter.api.Assertions.assertThrows(
            ResponseStatusException.class, () -> relatorioFinanceiroService.gerar(auth, "SUPPORT"));

    assertEquals(403, exception.getStatusCode().value());
  }

  @Test
  @DisplayName("Deve listar roles em ordem alfabetica para admin")
  void deveListarRolesParaAdmin() {
    Role support = new Role();
    support.setNome("SUPPORT");
    Role admin = new Role();
    admin.setNome("ADMIN");
    when(roleRepository.findAll()).thenReturn(List.of(support, admin));

    var auth =
        new UsernamePasswordAuthenticationToken(
            "admin@email.com", "senha", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

    List<String> roles = relatorioFinanceiroService.listarRolesDisponiveis(auth);

    assertEquals(List.of("ADMIN", "SUPPORT"), roles);
  }

  @Test
  @DisplayName("Deve gerar bytes validos de PDF")
  void deveGerarPdfValido() {
    RelatorioFinanceiroResponse response =
        new RelatorioFinanceiroResponse(
            new BigDecimal("20000000"),
            new BigDecimal("1000.00"),
            new BigDecimal("250.00"),
            new BigDecimal("750.00"),
            new BigDecimal("98.75"),
            List.of(),
            List.of());

    byte[] pdf = relatorioFinanceiroService.gerarPdf(response);

    assertTrue(pdf.length > 100);
    String signature = new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII);
    assertEquals("%PDF", signature);
  }

  private Item criarItem(TipoItem tipo, String valor, String horarioCriacaoIso) {
    Item item = new Item();
    item.setId(UUID.randomUUID());
    item.setTipo(tipo);
    item.setValor(new BigDecimal(valor));
    item.setData(LocalDate.parse(horarioCriacaoIso.substring(0, 10)));
    item.setHorarioCriacao(LocalDateTime.parse(horarioCriacaoIso));
    return item;
  }
}
