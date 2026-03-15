package com.sistema_contabilidade.rbac.service;

import com.sistema_contabilidade.rbac.model.Permissao;
import com.sistema_contabilidade.rbac.model.Role;
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

  @Transactional
  public Role criarRole(String nome) {
    roleRepository
        .findByNome(nome)
        .ifPresent(
            role -> {
              throw new ResponseStatusException(HttpStatus.CONFLICT, "Role ja existe");
            });
    Role role = new Role();
    role.setNome(nome);
    return roleRepository.save(role);
  }

  @Transactional
  public Permissao criarPermissao(String nome) {
    permissaoRepository
        .findByNome(nome)
        .ifPresent(
            permissao -> {
              throw new ResponseStatusException(HttpStatus.CONFLICT, "Permissao ja existe");
            });
    Permissao permissao = new Permissao();
    permissao.setNome(nome);
    return permissaoRepository.save(permissao);
  }

  @Transactional
  public Role adicionarPermissaoNaRole(String roleNome, String permissaoNome) {
    Role role =
        roleRepository
            .findByNome(roleNome)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role nao encontrada"));
    Permissao permissao =
        permissaoRepository
            .findByNome(permissaoNome)
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Permissao nao encontrada"));

    role.getPermissoes().add(permissao);
    return roleRepository.save(role);
  }

  @Transactional
  public Usuario atribuirRoleAoUsuario(UUID usuarioId, String roleNome) {
    Usuario usuario =
        usuarioRepository
            .findById(usuarioId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario nao encontrado"));
    Role role =
        roleRepository
            .findByNome(roleNome)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role nao encontrada"));

    usuario.getRoles().add(role);
    return usuarioRepository.save(usuario);
  }
}
