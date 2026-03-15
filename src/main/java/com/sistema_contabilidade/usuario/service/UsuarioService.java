package com.sistema_contabilidade.usuario.service;

import com.sistema_contabilidade.common.mapper.GenericModelMapperService;
import com.sistema_contabilidade.usuario.dto.UsuarioDto;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UsuarioService {

  private final UsuarioRepository usuarioRepository;
  private final PasswordEncoder passwordEncoder;
  @Autowired private final GenericModelMapperService<Usuario, UsuarioDto> modelMapperService;

  @Transactional
  public UsuarioDto save(UsuarioDto usuarioDto) {
    Usuario usuario = modelMapperService.convertToEntity(usuarioDto, Usuario.class);
    usuario.setSenha(passwordEncoder.encode(usuario.getSenha()));
    usuario = usuarioRepository.save(usuario);
    return modelMapperService.convertToDto(usuario, UsuarioDto.class);
  }

  @Transactional
  public UsuarioDto update(UUID id, UsuarioDto usuarioDto) {
    Usuario usuarioAtualizado = modelMapperService.convertToEntity(usuarioDto, Usuario.class);
    Usuario usuarioExistente = buscarPorId(id);
    usuarioExistente.setNome(usuarioAtualizado.getNome());
    usuarioExistente.setEmail(usuarioAtualizado.getEmail());
    if (usuarioAtualizado.getSenha() != null && !usuarioAtualizado.getSenha().isBlank()) {
      usuarioExistente.setSenha(passwordEncoder.encode(usuarioAtualizado.getSenha()));
    }
    Usuario salvo = usuarioRepository.save(usuarioExistente);
    return modelMapperService.convertToDto(salvo, UsuarioDto.class);
  }

  public List<UsuarioDto> listarTodos() {
    return usuarioRepository.findAll().stream()
        .map(usuario -> modelMapperService.convertToDto(usuario, UsuarioDto.class))
        .toList();
  }

  public UsuarioDto findById(UUID id) {
    Usuario usuario =
        usuarioRepository
            .findById(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario nao encontrado"));
    return modelMapperService.convertToDto(usuario, UsuarioDto.class);
  }

  public Usuario buscarPorId(UUID id) {
    return usuarioRepository
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario nao encontrado"));
  }

  @Transactional
  public void deletar(UUID id) {
    Usuario usuario = buscarPorId(id);
    usuarioRepository.delete(usuario);
  }
}
