package com.sistema_contabilidade.rbac.service;

import com.sistema_contabilidade.common.mapper.RbacMapper;
import com.sistema_contabilidade.rbac.dto.PermissaoDto;
import com.sistema_contabilidade.rbac.dto.RoleDto;
import com.sistema_contabilidade.rbac.dto.UsuarioComRolesDto;
import com.sistema_contabilidade.rbac.model.Permissao;
import com.sistema_contabilidade.rbac.model.Role;
import com.sistema_contabilidade.rbac.repository.PermissaoRepository;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
import com.sistema_contabilidade.security.service.CustomUserDetailsService;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class RoleService {

  private final RoleRepository roleRepository;
  private final PermissaoRepository permissaoRepository;
  private final UsuarioRepository usuarioRepository;
  private final RbacMapper rbacMapper;
  private final CustomUserDetailsService customUserDetailsService;

  @Transactional
  public RoleDto criarRole(String nome) {
    String roleNomePadrao = normalizarRoleNome(nome);
    roleRepository
        .findByNome(roleNomePadrao)
        .ifPresent(
            role -> {
              throw new ResponseStatusException(HttpStatus.CONFLICT, "Role ja existe");
            });
    Role role = new Role();
    role.setNome(roleNomePadrao);
    Role roleSalva = roleRepository.save(role);
    return rbacMapper.toRoleDto(roleSalva);
  }

  @Transactional
  public PermissaoDto criarPermissao(String nome) {
    permissaoRepository
        .findByNome(nome)
        .ifPresent(
            permissao -> {
              throw new ResponseStatusException(HttpStatus.CONFLICT, "Permissao ja existe");
            });
    Permissao permissao = new Permissao();
    permissao.setNome(nome);
    Permissao permissaoSalva = permissaoRepository.save(permissao);
    return rbacMapper.toPermissaoDto(permissaoSalva);
  }

  @Transactional
  public RoleDto adicionarPermissaoNaRole(String roleNome, String permissaoNome) {
    String roleNomePadrao = normalizarRoleNome(roleNome);
    Role role =
        roleRepository
            .findByNome(roleNomePadrao)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role nao encontrada"));
    Permissao permissao =
        permissaoRepository
            .findByNome(permissaoNome)
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Permissao nao encontrada"));

    role.getPermissoes().add(permissao);
    Role roleSalva = roleRepository.save(role);
    customUserDetailsService.limparCacheUserDetails();
    return rbacMapper.toRoleDto(roleSalva);
  }

  @Transactional
  public UsuarioComRolesDto atribuirRoleAoUsuario(UUID usuarioId, String roleNome) {
    String roleNomePadrao = normalizarRoleNome(roleNome);
    Usuario usuario =
        usuarioRepository
            .findById(usuarioId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario nao encontrado"));
    Role role =
        roleRepository
            .findByNome(roleNomePadrao)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role nao encontrada"));

    usuario.getRoles().add(role);
    Usuario usuarioSalvo = usuarioRepository.save(usuario);
    customUserDetailsService.atualizarCacheUsuario(usuarioSalvo.getId(), usuarioSalvo.getEmail());
    return rbacMapper.toUsuarioComRolesDto(usuarioSalvo);
  }

  @Transactional
  public List<RoleDto> listarRoles() {
    return roleRepository.findAll().stream()
        .map(rbacMapper::toRoleDto)
        .sorted(Comparator.comparing(RoleDto::getNome, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  @Transactional
  public List<PermissaoDto> listarPermissoes() {
    return permissaoRepository.findAll().stream()
        .map(rbacMapper::toPermissaoDto)
        .sorted(Comparator.comparing(PermissaoDto::getNome, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  private String normalizarRoleNome(String nome) {
    if (nome == null || nome.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome da role e obrigatorio");
    }
    return nome.trim().replace('_', ' ').replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
  }
}
