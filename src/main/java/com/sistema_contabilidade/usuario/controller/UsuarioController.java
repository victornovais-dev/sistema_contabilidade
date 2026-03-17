package com.sistema_contabilidade.usuario.controller;

import com.sistema_contabilidade.rbac.dto.UsuarioComRolesDto;
import com.sistema_contabilidade.usuario.dto.UsuarioCreateRequest;
import com.sistema_contabilidade.usuario.dto.UsuarioDto;
import com.sistema_contabilidade.usuario.dto.UsuarioUpdateByEmailRequest;
import com.sistema_contabilidade.usuario.service.UsuarioService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/usuarios")
@Validated
@RequiredArgsConstructor
public class UsuarioController {

  private static final String ID_PATH = "/{id}";

  private final UsuarioService usuarioService;

  @PostMapping
  public ResponseEntity<UsuarioDto> criar(@Valid @RequestBody UsuarioCreateRequest request) {
    UsuarioDto criado = usuarioService.save(request);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path(ID_PATH)
            .buildAndExpand(criado.getId())
            .toUri();
    return ResponseEntity.created(location).body(criado);
  }

  @GetMapping
  public ResponseEntity<List<UsuarioDto>> listarTodos() {
    List<UsuarioDto> response = usuarioService.listarTodos();
    return ResponseEntity.ok(response);
  }

  @GetMapping(ID_PATH)
  public ResponseEntity<UsuarioDto> buscarPorId(@PathVariable UUID id) {
    return ResponseEntity.ok(usuarioService.findById(id));
  }

  @GetMapping("/por-email")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<UsuarioComRolesDto> buscarPorEmail(
      @RequestParam("email") @NotBlank @Email String email) {
    return ResponseEntity.ok(usuarioService.findComRolesByEmail(email));
  }

  @PutMapping(ID_PATH)
  public ResponseEntity<UsuarioDto> atualizar(
      @PathVariable UUID id, @Valid @RequestBody UsuarioDto request) {
    return ResponseEntity.ok(usuarioService.update(id, request));
  }

  @PutMapping("/por-email")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<UsuarioComRolesDto> atualizarPorEmail(
      @Valid @RequestBody UsuarioUpdateByEmailRequest request) {
    return ResponseEntity.ok(usuarioService.updateByEmail(request));
  }

  @DeleteMapping(ID_PATH)
  public ResponseEntity<Void> deletar(@PathVariable UUID id) {
    usuarioService.deletar(id);
    return ResponseEntity.noContent().build();
  }
}
