package com.sistema_contabilidade.usuario.service;

import com.sistema_contabilidade.common.mapper.RbacMapper;
import com.sistema_contabilidade.common.mapper.UsuarioMapper;
import com.sistema_contabilidade.rbac.dto.UsuarioComRolesDto;
import com.sistema_contabilidade.rbac.model.Role;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
import com.sistema_contabilidade.security.service.CustomUserDetailsService;
import com.sistema_contabilidade.security.validation.InputSanitizer;
import com.sistema_contabilidade.usuario.dto.UsuarioCreateRequest;
import com.sistema_contabilidade.usuario.dto.UsuarioDto;
import com.sistema_contabilidade.usuario.dto.UsuarioSelfUpdateRequest;
import com.sistema_contabilidade.usuario.dto.UsuarioUpdateByEmailRequest;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UsuarioService {

  private static final String USUARIO_NAO_ENCONTRADO = "Usuario nao encontrado";
  private static final String CAMPO_NOME = "nome";
  private static final String CAMPO_EMAIL = "email";
  private static final String CAMPO_ROLE = "role";
  private static final String ROLE_SUPPORT = "SUPPORT";
  private static final String ROLE_MANAGER = "MANAGER";
  private static final String SUPPORT_MANAGER_CONFLITO =
      "Usuario nao pode ter as roles SUPPORT e MANAGER ao mesmo tempo.";
  private final UsuarioRepository usuarioRepository;
  private final RoleRepository roleRepository;
  private final PasswordEncoder passwordEncoder;
  private final UsuarioMapper usuarioMapper;
  private final RbacMapper rbacMapper;
  private final CustomUserDetailsService customUserDetailsService;
  private final InputSanitizer inputSanitizer;

  @Transactional
  public UsuarioDto save(UsuarioCreateRequest usuarioCreateRequest) {
    String nome = inputSanitizer.sanitizeInlineText(usuarioCreateRequest.nome(), CAMPO_NOME, 120);
    String email = inputSanitizer.sanitizeEmail(usuarioCreateRequest.email(), CAMPO_EMAIL);
    validarEmailDuplicado(email, null);
    Usuario usuario = new Usuario();
    usuario.setNome(nome);
    usuario.setEmail(email);
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
    String nome = inputSanitizer.sanitizeInlineText(usuarioDto.getNome(), CAMPO_NOME, 120);
    String email = inputSanitizer.sanitizeEmail(usuarioDto.getEmail(), CAMPO_EMAIL);
    validarEmailDuplicado(email, id);
    usuarioExistente.setNome(nome);
    usuarioExistente.setEmail(email);
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
    String nome = inputSanitizer.sanitizeInlineText(request.nome(), CAMPO_NOME, 120);
    String email = inputSanitizer.sanitizeEmail(request.email(), CAMPO_EMAIL);
    validarEmailDuplicado(email, usuarioExistente.getId());
    usuarioExistente.setNome(nome);
    usuarioExistente.setEmail(email);
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
    return rbacMapper.toUsuarioComRolesDto(
        buscarPorEmail(inputSanitizer.sanitizeEmail(email, CAMPO_EMAIL)));
  }

  public String findNomeByEmail(String email) {
    return buscarPorEmail(inputSanitizer.sanitizeEmail(email, CAMPO_EMAIL)).getNome();
  }

  @Transactional
  public UsuarioComRolesDto updateByEmail(UsuarioUpdateByEmailRequest request) {
    String email = inputSanitizer.sanitizeEmail(request.email(), CAMPO_EMAIL);
    Usuario usuarioExistente = buscarPorEmail(email);
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
    String emailNormalizado = inputSanitizer.sanitizeEmail(email, CAMPO_EMAIL);
    usuarioRepository
        .findByEmail(emailNormalizado)
        .ifPresent(
            usuario -> {
              if (usuarioIdIgnorado == null || !usuario.getId().equals(usuarioIdIgnorado)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email ja cadastrado");
              }
            });
  }

  private Usuario buscarPorEmail(String email) {
    String emailNormalizado = inputSanitizer.sanitizeEmail(email, CAMPO_EMAIL);
    return usuarioRepository
        .findByEmail(emailNormalizado)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, USUARIO_NAO_ENCONTRADO));
  }

  private Role buscarRole(String roleNome) {
    String roleNomeNormalizado = inputSanitizer.sanitizeInlineText(roleNome, CAMPO_ROLE, 80);
    return roleRepository
        .findByNomeIgnoreCase(roleNomeNormalizado)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Role nao encontrada: " + roleNomeNormalizado));
  }

  private Set<String> extrairRolesSolicitadas(UsuarioCreateRequest usuarioCreateRequest) {
    Set<String> roles = new LinkedHashSet<>();
    if (usuarioCreateRequest.role() != null && !usuarioCreateRequest.role().isBlank()) {
      roles.add(inputSanitizer.sanitizeInlineText(usuarioCreateRequest.role(), CAMPO_ROLE, 80));
    }
    if (usuarioCreateRequest.roles() != null) {
      usuarioCreateRequest.roles().stream()
          .filter(role -> role != null && !role.isBlank())
          .map(role -> inputSanitizer.sanitizeInlineText(role, CAMPO_ROLE, 80))
          .forEach(roles::add);
    }
    return normalizarRoles(roles);
  }

  private Set<String> normalizarRoles(Set<String> rolesEntrada) {
    Set<String> roles = new LinkedHashSet<>();
    if (rolesEntrada != null) {
      rolesEntrada.stream()
          .filter(role -> role != null && !role.isBlank())
          .map(role -> inputSanitizer.sanitizeInlineText(role, CAMPO_ROLE, 80))
          .forEach(roles::add);
    }
    if (roles.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Ao menos uma role deve ser informada");
    }
    validarCombinacaoDeRoles(roles);
    return roles;
  }

  private void validarCombinacaoDeRoles(Set<String> roles) {
    Set<String> rolesNormalizadas =
        roles.stream().map(role -> role.toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
    if (rolesNormalizadas.contains(ROLE_SUPPORT) && rolesNormalizadas.contains(ROLE_MANAGER)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, SUPPORT_MANAGER_CONFLITO);
    }
  }
}
