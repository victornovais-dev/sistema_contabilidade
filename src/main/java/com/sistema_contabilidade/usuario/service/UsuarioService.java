package com.sistema_contabilidade.usuario.service;

import com.sistema_contabilidade.common.mapper.RbacMapper;
import com.sistema_contabilidade.common.mapper.UsuarioMapper;
import com.sistema_contabilidade.rbac.dto.UsuarioComRolesDto;
import com.sistema_contabilidade.rbac.model.Role;
import com.sistema_contabilidade.rbac.model.RoleNivel;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
import com.sistema_contabilidade.security.service.CustomUserDetailsService;
import com.sistema_contabilidade.usuario.dto.UsuarioCreateRequest;
import com.sistema_contabilidade.usuario.dto.UsuarioDto;
import com.sistema_contabilidade.usuario.dto.UsuarioSelfUpdateRequest;
import com.sistema_contabilidade.usuario.dto.UsuarioUpdateByEmailRequest;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UsuarioService {

  private static final String USUARIO_NAO_ENCONTRADO = "Usuario nao encontrado";
  private final UsuarioRepository usuarioRepository;
  private final RoleRepository roleRepository;
  private final PasswordEncoder passwordEncoder;
  private final UsuarioMapper usuarioMapper;
  private final RbacMapper rbacMapper;
  private final CustomUserDetailsService customUserDetailsService;

  @Transactional
  public UsuarioDto save(UsuarioCreateRequest usuarioCreateRequest) {
    validarEmailDuplicado(usuarioCreateRequest.email(), null);
    Usuario usuario = new Usuario();
    usuario.setNome(usuarioCreateRequest.nome());
    usuario.setEmail(usuarioCreateRequest.email());
    usuario.setSenha(passwordEncoder.encode(usuarioCreateRequest.senha()));
    usuario.getRoles().clear();
    extrairRolesSolicitadas(usuarioCreateRequest).stream()
        .map(this::buscarRole)
        .forEach(usuario.getRoles()::add);
    usuario = usuarioRepository.save(usuario);
    return usuarioMapper.toDto(usuario);
  }

  @Transactional
  public UsuarioDto update(UUID id, UsuarioDto usuarioDto) {
    Usuario usuarioAtualizado = usuarioMapper.toEntity(usuarioDto);
    Usuario usuarioExistente = buscarPorId(id);
    String emailAnterior = usuarioExistente.getEmail();
    validarEmailDuplicado(usuarioAtualizado.getEmail(), id);
    usuarioExistente.setNome(usuarioAtualizado.getNome());
    usuarioExistente.setEmail(usuarioAtualizado.getEmail());
    if (usuarioAtualizado.getSenha() != null && !usuarioAtualizado.getSenha().isBlank()) {
      usuarioExistente.setSenha(passwordEncoder.encode(usuarioAtualizado.getSenha()));
    }
    Usuario salvo = usuarioRepository.save(usuarioExistente);
    customUserDetailsService.atualizarCacheUsuario(salvo.getId(), salvo.getEmail());
    if (!emailAnterior.equalsIgnoreCase(salvo.getEmail())) {
      customUserDetailsService.removerCacheUsuario(salvo.getId(), emailAnterior);
    }
    return usuarioMapper.toDto(salvo);
  }

  @Transactional
  public UsuarioDto updatePerfil(String emailAutenticado, UsuarioSelfUpdateRequest request) {
    Usuario usuarioExistente = buscarPorEmail(emailAutenticado);
    String emailAnterior = usuarioExistente.getEmail();
    validarEmailDuplicado(request.email(), usuarioExistente.getId());
    usuarioExistente.setNome(request.nome());
    usuarioExistente.setEmail(request.email());
    if (request.senha() != null && !request.senha().isBlank()) {
      usuarioExistente.setSenha(passwordEncoder.encode(request.senha()));
    }
    Usuario salvo = usuarioRepository.save(usuarioExistente);
    customUserDetailsService.atualizarCacheUsuario(salvo.getId(), salvo.getEmail());
    if (!emailAnterior.equalsIgnoreCase(salvo.getEmail())) {
      customUserDetailsService.removerCacheUsuario(salvo.getId(), emailAnterior);
    }
    return usuarioMapper.toDto(salvo);
  }

  public List<UsuarioDto> listarTodos() {
    return usuarioRepository.findAll().stream().map(usuarioMapper::toDto).toList();
  }

  public UsuarioDto findById(UUID id) {
    Usuario usuario =
        usuarioRepository
            .findById(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, USUARIO_NAO_ENCONTRADO));
    return usuarioMapper.toDto(usuario);
  }

  public UsuarioComRolesDto findComRolesByEmail(String email) {
    return rbacMapper.toUsuarioComRolesDto(buscarPorEmail(email));
  }

  public String findNomeByEmail(String email) {
    return buscarPorEmail(email).getNome();
  }

  @Transactional
  public UsuarioComRolesDto updateByEmail(UsuarioUpdateByEmailRequest request) {
    Usuario usuarioExistente = buscarPorEmail(request.email());
    usuarioExistente.getRoles().clear();
    normalizarRoles(request.roles()).stream()
        .map(this::buscarRole)
        .forEach(usuarioExistente.getRoles()::add);
    if (request.senha() != null && !request.senha().isBlank()) {
      usuarioExistente.setSenha(passwordEncoder.encode(request.senha()));
    }
    Usuario salvo = usuarioRepository.save(usuarioExistente);
    customUserDetailsService.atualizarCacheUsuario(salvo.getId(), salvo.getEmail());
    return rbacMapper.toUsuarioComRolesDto(salvo);
  }

  public Usuario buscarPorId(UUID id) {
    return usuarioRepository
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, USUARIO_NAO_ENCONTRADO));
  }

  @Transactional
  public void deletar(UUID id) {
    Usuario usuario = buscarPorId(id);
    customUserDetailsService.removerCacheUsuario(usuario.getId(), usuario.getEmail());
    usuarioRepository.delete(usuario);
  }

  private void validarEmailDuplicado(String email, UUID usuarioIdIgnorado) {
    usuarioRepository
        .findByEmail(email)
        .ifPresent(
            usuario -> {
              if (usuarioIdIgnorado == null || !usuario.getId().equals(usuarioIdIgnorado)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email ja cadastrado");
              }
            });
  }

  private Usuario buscarPorEmail(String email) {
    String emailNormalizado = email == null ? "" : email.trim();
    return usuarioRepository
        .findByEmail(emailNormalizado)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, USUARIO_NAO_ENCONTRADO));
  }

  private Role buscarRole(String roleNome) {
    String roleNomePadrao;
    try {
      roleNomePadrao = RoleNivel.fromNome(roleNome).valorBanco();
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Role invalida. Use: ADMIN, MANAGER, TARCISIO, KIM KATAGUIRI, VALDEMAR",
          ex);
    }
    return roleRepository
        .findByNome(roleNomePadrao)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role nao encontrada"));
  }

  private Set<String> extrairRolesSolicitadas(UsuarioCreateRequest usuarioCreateRequest) {
    Set<String> roles = new LinkedHashSet<>();
    if (usuarioCreateRequest.role() != null && !usuarioCreateRequest.role().isBlank()) {
      roles.add(usuarioCreateRequest.role().trim());
    }
    if (usuarioCreateRequest.roles() != null) {
      usuarioCreateRequest.roles().stream()
          .filter(role -> role != null && !role.isBlank())
          .map(String::trim)
          .forEach(roles::add);
    }
    return normalizarRoles(roles);
  }

  private Set<String> normalizarRoles(Set<String> rolesEntrada) {
    Set<String> roles = new LinkedHashSet<>();
    if (rolesEntrada != null) {
      rolesEntrada.stream()
          .filter(role -> role != null && !role.isBlank())
          .map(String::trim)
          .forEach(roles::add);
    }
    if (roles.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Ao menos uma role deve ser informada");
    }
    return roles;
  }
}
