package com.sistema_contabilidade.relatorio.service;

import com.sistema_contabilidade.item.repository.ItemRepository;
import com.sistema_contabilidade.rbac.service.CampaignScope;
import com.sistema_contabilidade.rbac.service.CampaignScopeResolver;
import com.sistema_contabilidade.relatorio.dto.RelatorioContaPagamentoRow;
import com.sistema_contabilidade.relatorio.dto.RelatorioFinanceiroPdfData;
import com.sistema_contabilidade.relatorio.dto.RelatorioFinanceiroResponse;
import com.sistema_contabilidade.relatorio.dto.RelatorioFinanceiroResumoResponse;
import com.sistema_contabilidade.relatorio.dto.RelatorioItemDto;
import com.sistema_contabilidade.relatorio.dto.RelatorioResumoCategoriaRow;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class RelatorioFinanceiroService {

  private final ItemRepository itemRepository;
  private final CampaignScopeResolver campaignScopeResolver;
  private final UsuarioRepository usuarioRepository;
  private final PlaywrightPdfService playwrightPdfService;
  private final RelatorioResumoCacheService relatorioResumoCacheService;
  private final RelatorioFinanceiroPdfDataFactory pdfDataFactory =
      new RelatorioFinanceiroPdfDataFactory();

  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  public RelatorioFinanceiroResponse gerar(Authentication authentication) {
    return gerarRelatorio(authentication, null);
  }

  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  public RelatorioFinanceiroResponse gerar(Authentication authentication, String roleFiltro) {
    return gerarRelatorio(authentication, roleFiltro);
  }

  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  public RelatorioFinanceiroResumoResponse gerarResumo(Authentication authentication) {
    return gerarResumoInterno(authentication, null);
  }

  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  public RelatorioFinanceiroResumoResponse gerarResumo(
      Authentication authentication, String roleFiltro) {
    return gerarResumoInterno(authentication, roleFiltro);
  }

  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  public List<String> listarRolesDisponiveis(Authentication authentication) {
    return campaignScopeResolver.listAvailableCampaigns(authentication);
  }

  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  public RelatorioFinanceiroPdfData prepararDadosPdf(
      Authentication authentication, RelatorioFinanceiroResponse relatorio) {
    return pdfDataFactory.create(
        extrairNomeResponsavel(authentication),
        relatorio,
        LocalDateTime.now(ZoneId.systemDefault()));
  }

  public byte[] gerarPdf(RelatorioFinanceiroPdfData dadosPdf) {
    return playwrightPdfService.generateFinancialReportPdf(dadosPdf);
  }

  private RelatorioFinanceiroResumoResponse gerarResumoInterno(
      Authentication authentication, String roleFiltro) {
    CampaignScope scope = campaignScopeResolver.resolve(authentication, roleFiltro);
    String normalizedFilters = "role=" + Objects.toString(scope.roleFilter(), "ALL");
    return relatorioResumoCacheService.getOrCompute(
        scope, normalizedFilters, () -> gerarResumoPorAgregacoes(scope));
  }

  private RelatorioFinanceiroResumoResponse gerarResumoPorAgregacoes(CampaignScope scope) {
    List<RelatorioContaPagamentoRow> contasPagasPorConta = buscarContasPagasPorConta(scope);
    return RelatorioFinanceiroConsolidador.buildSummaryResponse(
        buscarResumoItens(scope), calcularContasPagas(contasPagasPorConta), contasPagasPorConta);
  }

  private RelatorioFinanceiroResponse gerarRelatorio(
      Authentication authentication, String roleFiltro) {
    CampaignScope scope = campaignScopeResolver.resolve(authentication, roleFiltro);
    return RelatorioFinanceiroConsolidador.buildDetailedResponse(buscarItensVisiveis(scope));
  }

  private String extrairNomeResponsavel(Authentication authentication) {
    if (authentication == null
        || authentication.getName() == null
        || authentication.getName().isBlank()) {
      return "Usuario autenticado";
    }
    return usuarioRepository
        .findByEmail(authentication.getName())
        .map(Usuario::getNome)
        .filter(nome -> nome != null && !nome.isBlank())
        .orElse(authentication.getName());
  }

  private List<RelatorioItemDto> buscarItensVisiveis(CampaignScope scope) {
    if (scope.roleFilter() != null) {
      return itemRepository.findRelatorioItensByRoleNomeOrderByDataDescHorarioCriacaoDesc(
          scope.roleFilter());
    }
    if (scope.allCampaigns()) {
      return itemRepository.findAllRelatorioItensOrderByDataDescHorarioCriacaoDesc();
    }
    if (scope.effectiveCampaignNames().isEmpty()) {
      return List.of();
    }
    return itemRepository.findRelatorioItensByRoleNomesOrderByDataDescHorarioCriacaoDesc(
        scope.effectiveCampaignNames());
  }

  private List<RelatorioResumoCategoriaRow> buscarResumoItens(CampaignScope scope) {
    if (scope.roleFilter() != null) {
      return itemRepository.findRelatorioResumoCategoriasByRoleNome(scope.roleFilter());
    }
    if (scope.allCampaigns()) {
      return itemRepository.findAllRelatorioResumoCategorias();
    }
    if (scope.effectiveCampaignNames().isEmpty()) {
      return List.of();
    }
    return itemRepository.findRelatorioResumoCategoriasByRoleNomes(scope.effectiveCampaignNames());
  }

  private BigDecimal calcularContasPagas(List<RelatorioContaPagamentoRow> contasPagasPorConta) {
    if (contasPagasPorConta == null) {
      return BigDecimal.ZERO;
    }
    return contasPagasPorConta.stream()
        .filter(Objects::nonNull)
        .map(RelatorioContaPagamentoRow::totalPago)
        .filter(Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private List<RelatorioContaPagamentoRow> buscarContasPagasPorConta(CampaignScope scope) {
    if (scope.roleFilter() != null) {
      return itemRepository.findContasPagasPorContaByRoleNome(scope.roleFilter());
    }
    if (scope.allCampaigns()) {
      return itemRepository.findContasPagasPorConta();
    }
    if (scope.effectiveCampaignNames().isEmpty()) {
      return List.of();
    }
    return itemRepository.findContasPagasPorContaByRoleNomes(scope.effectiveCampaignNames());
  }
}
