package com.sistema_contabilidade.usuario.service;

import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UsuarioService {

  private final UsuarioRepository usuarioRepository;
  private final PasswordEncoder passwordEncoder;

  @Transactional
  public Usuario criar(Usuario usuario) {
    usuario.setSenha(passwordEncoder.encode(usuario.getSenha()));
    return usuarioRepository.save(usuario);
  }

  @Transactional
  public Usuario atualizar(UUID id, Usuario usuarioAtualizado) {
    Usuario usuarioExistente = buscarPorId(id);
    usuarioExistente.setNome(usuarioAtualizado.getNome());
    usuarioExistente.setEmail(usuarioAtualizado.getEmail());
    if (usuarioAtualizado.getSenha() != null && !usuarioAtualizado.getSenha().isBlank()) {
      usuarioExistente.setSenha(passwordEncoder.encode(usuarioAtualizado.getSenha()));
    }
    return usuarioRepository.save(usuarioExistente);
  }

  public List<Usuario> listarTodos() {
    return usuarioRepository.findAll();
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
