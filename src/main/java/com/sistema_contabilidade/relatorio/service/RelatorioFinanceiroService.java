package com.sistema_contabilidade.relatorio.service;

import com.sistema_contabilidade.common.util.CandidateRoleUtils;
import com.sistema_contabilidade.item.repository.ItemRepository;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
import com.sistema_contabilidade.relatorio.dto.RelatorioFinanceiroResponse;
import com.sistema_contabilidade.relatorio.dto.RelatorioFinanceiroResumoResponse;
import com.sistema_contabilidade.relatorio.dto.RelatorioItemDto;
import com.sistema_contabilidade.relatorio.dto.RelatorioResumoItemRow;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class RelatorioFinanceiroService {

  private static final String ADMIN_ROLE = "ADMIN";

  private final ItemRepository itemRepository;
  private final RoleRepository roleRepository;
  private final UsuarioRepository usuarioRepository;
  private final PlaywrightPdfService playwrightPdfService;
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

  public List<String> listarRolesDisponiveis(Authentication authentication) {
    Set<String> roleNomesAutenticado = extrairRoleNomes(authentication);
    if (roleNomesAutenticado.contains(ADMIN_ROLE)) {
      return CandidateRoleUtils.filterCandidateRoles(roleRepository.findAllRoleNamesOrdered());
    }
    return CandidateRoleUtils.filterCandidateRoles(roleNomesAutenticado);
  }

  public byte[] gerarPdf(Authentication authentication, RelatorioFinanceiroResponse relatorio) {
    return playwrightPdfService.generateFinancialReportPdf(
        pdfDataFactory.create(
            extrairNomeResponsavel(authentication), relatorio, LocalDateTime.now()));
  }

  private RelatorioFinanceiroResumoResponse gerarResumoInterno(
      Authentication authentication, String roleFiltro) {
    RelatorioScope scope = resolveScope(authentication, roleFiltro);
    return RelatorioFinanceiroConsolidador.buildSummaryResponse(buscarResumoItens(scope));
  }

  private RelatorioFinanceiroResponse gerarRelatorio(
      Authentication authentication, String roleFiltro) {
    RelatorioScope scope = resolveScope(authentication, roleFiltro);
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
        .map(usuario -> usuario.getNome())
        .filter(nome -> nome != null && !nome.isBlank())
        .orElse(authentication.getName());
  }

  private RelatorioScope resolveScope(Authentication authentication, String roleFiltro) {
    String roleFiltroNormalizada = normalizarRole(roleFiltro);
    Set<String> roleNomesAutenticado = extrairRoleNomes(authentication);
    boolean isAdmin = roleNomesAutenticado.contains(ADMIN_ROLE);
    if (roleFiltroNormalizada != null
        && !isAdmin
        && !roleNomesAutenticado.contains(roleFiltroNormalizada)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "A role selecionada nao pertence ao usuario autenticado.");
    }
    return new RelatorioScope(roleNomesAutenticado, roleFiltroNormalizada);
  }

  private List<RelatorioItemDto> buscarItensVisiveis(RelatorioScope scope) {
    if (scope.roleFiltroNormalizada() != null) {
      return itemRepository.findRelatorioItensByRoleNomeOrderByDataDescHorarioCriacaoDesc(
          scope.roleFiltroNormalizada());
    }
    if (scope.roleNomesAutenticado().contains(ADMIN_ROLE)) {
      return itemRepository.findAllRelatorioItensOrderByDataDescHorarioCriacaoDesc();
    }
    if (scope.roleNomesAutenticado().isEmpty()) {
      return List.of();
    }
    return itemRepository.findRelatorioItensByRoleNomesOrderByDataDescHorarioCriacaoDesc(
        scope.roleNomesAutenticado());
  }

  private List<RelatorioResumoItemRow> buscarResumoItens(RelatorioScope scope) {
    if (scope.roleFiltroNormalizada() != null) {
      return itemRepository.findRelatorioResumoItensByRoleNome(scope.roleFiltroNormalizada());
    }
    if (scope.roleNomesAutenticado().contains(ADMIN_ROLE)) {
      return itemRepository.findAllRelatorioResumoItens();
    }
    if (scope.roleNomesAutenticado().isEmpty()) {
      return List.of();
    }
    return itemRepository.findRelatorioResumoItensByRoleNomes(scope.roleNomesAutenticado());
  }

  private Set<String> extrairRoleNomes(Authentication authentication) {
    if (authentication == null) {
      return Set.of();
    }
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .filter(authority -> authority != null && authority.startsWith("ROLE_"))
        .map(authority -> authority.substring("ROLE_".length()))
        .collect(Collectors.toSet());
  }

  private String normalizarRole(String role) {
    if (role == null || role.isBlank()) {
      return null;
    }
    return role.trim().toUpperCase(Locale.ROOT);
  }

  private record RelatorioScope(Set<String> roleNomesAutenticado, String roleFiltroNormalizada) {}
}
