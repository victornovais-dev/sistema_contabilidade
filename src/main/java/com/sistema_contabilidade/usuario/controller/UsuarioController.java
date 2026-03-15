package com.sistema_contabilidade.usuario.controller;

import com.sistema_contabilidade.usuario.dto.UsuarioCreateRequest;
import com.sistema_contabilidade.usuario.dto.UsuarioResponse;
import com.sistema_contabilidade.usuario.dto.UsuarioUpdateRequest;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.service.UsuarioService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/usuarios")
@Validated
@RequiredArgsConstructor
public class UsuarioController {

  private final UsuarioService usuarioService;

  @PostMapping
  public ResponseEntity<UsuarioResponse> criar(@Valid @RequestBody UsuarioCreateRequest request) {
    Usuario usuario = new Usuario();
    usuario.setNome(request.nome());
    usuario.setEmail(request.email());
    usuario.setSenha(request.senha());

    Usuario criado = usuarioService.criar(usuario);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(criado.getId())
            .toUri();
    return ResponseEntity.created(location).body(UsuarioResponse.from(criado));
  }

  @GetMapping
  public ResponseEntity<List<UsuarioResponse>> listarTodos() {
    List<UsuarioResponse> response =
        usuarioService.listarTodos().stream().map(UsuarioResponse::from).toList();
    return ResponseEntity.ok(response);
  }

  @GetMapping("/{id}")
  public ResponseEntity<UsuarioResponse> buscarPorId(@PathVariable UUID id) {
    Usuario usuario = usuarioService.buscarPorId(id);
    return ResponseEntity.ok(UsuarioResponse.from(usuario));
  }

  @PutMapping("/{id}")
  public ResponseEntity<UsuarioResponse> atualizar(
      @PathVariable UUID id, @Valid @RequestBody UsuarioUpdateRequest request) {
    Usuario usuario = new Usuario();
    usuario.setNome(request.nome());
    usuario.setEmail(request.email());

    Usuario atualizado = usuarioService.atualizar(id, usuario);
    return ResponseEntity.ok(UsuarioResponse.from(atualizado));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deletar(@PathVariable UUID id) {
    usuarioService.deletar(id);
    return ResponseEntity.noContent().build();
  }
}
