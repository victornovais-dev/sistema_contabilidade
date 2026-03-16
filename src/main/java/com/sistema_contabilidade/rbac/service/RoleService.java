package com.sistema_contabilidade.rbac.service;

import com.sistema_contabilidade.common.mapper.RbacMapper;
import com.sistema_contabilidade.rbac.dto.PermissaoDto;
import com.sistema_contabilidade.rbac.dto.RoleDto;
import com.sistema_contabilidade.rbac.dto.UsuarioComRolesDto;
import com.sistema_contabilidade.rbac.model.Permissao;
import com.sistema_contabilidade.rbac.model.Role;
import com.sistema_contabilidade.rbac.model.RoleNivel;
import com.sistema_contabilidade.rbac.repository.PermissaoRepository;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
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

  @Transactional
  public RoleDto criarRole(String nome) {
    String roleNomePadrao = parseRoleNome(nome);
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
    String roleNomePadrao = parseRoleNome(roleNome);
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
    return rbacMapper.toRoleDto(roleSalva);
  }

  @Transactional
  public UsuarioComRolesDto atribuirRoleAoUsuario(UUID usuarioId, String roleNome) {
    String roleNomePadrao = parseRoleNome(roleNome);
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
    return rbacMapper.toUsuarioComRolesDto(usuarioSalvo);
  }

  private String parseRoleNome(String nome) {
    try {
      return RoleNivel.fromNome(nome).name();
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Role invalida. Use: ADMIN, MANAGER, OPERATOR, SUPPORT, CUSTOMER",
          e);
    }
  }
}
