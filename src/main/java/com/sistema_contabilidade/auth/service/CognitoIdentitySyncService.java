package com.sistema_contabilidade.auth.service;

import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.auth.provider", havingValue = "cognito")
public class CognitoIdentitySyncService {

  private final UsuarioRepository usuarioRepository;
  private final PasswordEncoder passwordEncoder;

  public Usuario synchronizeLoginIdentity(AuthProviderLoginResult authResult) {
    return synchronizeIdentity(
        authResult.cognitoSub(),
        authResult.providerUsername(),
        authResult.email(),
        authResult.nome());
  }

  public Usuario synchronizeRefreshIdentity(AuthProviderRefreshResult authResult) {
    return synchronizeIdentity(
        authResult.cognitoSub(),
        authResult.providerUsername(),
        authResult.email(),
        authResult.nome());
  }

  private Usuario synchronizeIdentity(
      String cognitoSub, String providerUsername, String email, String nome) {
    Optional<Usuario> bySub =
        cognitoSub == null || cognitoSub.isBlank()
            ? Optional.empty()
            : usuarioRepository.findByCognitoSub(cognitoSub);
    Optional<Usuario> byEmail =
        email == null || email.isBlank() ? Optional.empty() : usuarioRepository.findByEmail(email);

    if (bySub.isPresent() && byEmail.isPresent() && !sameUser(bySub.get(), byEmail.get())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Conflito entre email e cognito_sub para o mesmo login");
    }

    Usuario usuario = bySub.orElseGet(() -> byEmail.orElseGet(Usuario::new));
    if (usuario.getId() == null) {
      usuario.setSenha(passwordEncoder.encode(UUID.randomUUID().toString()));
    } else if (usuario.getSenha() == null || usuario.getSenha().isBlank()) {
      usuario.setSenha(passwordEncoder.encode(UUID.randomUUID().toString()));
    }
    usuario.setNome(nome == null || nome.isBlank() ? email : nome);
    usuario.setEmail(email);
    usuario.setCognitoSub(blankToNull(cognitoSub));
    usuario.setCognitoUsername(blankToNull(providerUsername));

    try {
      return usuarioRepository.save(usuario);
    } catch (DataIntegrityViolationException exception) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Falha ao sincronizar identidade Cognito", exception);
    }
  }

  private boolean sameUser(Usuario left, Usuario right) {
    return left.getId() != null && left.getId().equals(right.getId());
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
