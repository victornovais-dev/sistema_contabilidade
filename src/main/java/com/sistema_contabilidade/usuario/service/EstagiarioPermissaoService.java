package com.sistema_contabilidade.usuario.service;

import com.sistema_contabilidade.common.util.CandidateRoleUtils;
import com.sistema_contabilidade.rbac.dto.RoleResumoDto;
import com.sistema_contabilidade.rbac.dto.UsuarioComRolesDto;
import com.sistema_contabilidade.rbac.service.CandidateRoleCatalogService;
import com.sistema_contabilidade.usuario.dto.EstagiarioPermissoesUpdateRequest;
import com.sistema_contabilidade.usuario.dto.UsuarioUpdateByEmailRequest;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class EstagiarioPermissaoService {

  private static final String ESTAGIARIO_ROLE = "ESTAGIARIO";

  private final UsuarioService usuarioService;
  private final CandidateRoleCatalogService candidateRoleCatalogService;

  public UsuarioComRolesDto buscarPorEmail(String email) {
    UsuarioComRolesDto usuario = usuarioService.findComRolesByEmail(email);
    validarUsuarioEstagiario(usuario);
    return usuario;
  }

  public Set<String> listarRolesDeCampanhaDisponiveis() {
    return new LinkedHashSet<>(candidateRoleCatalogService.listAvailableRolesForAdmin());
  }

  public UsuarioComRolesDto atualizarPermissoes(EstagiarioPermissoesUpdateRequest request) {
    UsuarioComRolesDto usuario = buscarPorEmail(request.email());
    Set<String> rolesDeCampanha = validarRolesDeCampanha(request.roles());
    Set<String> rolesDesejadas = new LinkedHashSet<>();
    rolesDesejadas.add(ESTAGIARIO_ROLE);
    rolesDesejadas.addAll(rolesDeCampanha);

    return usuarioService.updateByEmail(
        new UsuarioUpdateByEmailRequest(usuario.getEmail(), null, rolesDesejadas));
  }

  private Set<String> validarRolesDeCampanha(Set<String> rolesSolicitadas) {
    Map<String, String> rolesDisponiveisPorNomeNormalizado = new LinkedHashMap<>();
    candidateRoleCatalogService
        .listAvailableRolesForAdmin()
        .forEach(
            role ->
                rolesDisponiveisPorNomeNormalizado.putIfAbsent(normalizarRole(role), role.trim()));

    Set<String> rolesValidadas = new LinkedHashSet<>();
    for (String role : rolesSolicitadas) {
      String roleNormalizada = normalizarRole(role);
      String roleDisponivel = rolesDisponiveisPorNomeNormalizado.get(roleNormalizada);
      if (roleDisponivel == null) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Apenas roles de campanha podem ser atribuidas ao estagiario: " + roleNormalizada);
      }
      rolesValidadas.add(roleDisponivel);
    }
    return rolesValidadas;
  }

  private void validarUsuarioEstagiario(UsuarioComRolesDto usuario) {
    Set<String> rolesTecnicas =
        usuario.getRoles().stream()
            .map(RoleResumoDto::getNome)
            .map(EstagiarioPermissaoService::normalizarRole)
            .filter(role -> !CandidateRoleUtils.isCandidateRole(role))
            .collect(java.util.stream.Collectors.toSet());

    if (!rolesTecnicas.equals(Set.of(ESTAGIARIO_ROLE))) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Usuario informado nao possui somente a role ESTAGIARIO");
    }
  }

  private static String normalizarRole(String role) {
    return role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
  }
}
