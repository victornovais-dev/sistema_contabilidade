package com.sistema_contabilidade.auth.service;

import com.sistema_contabilidade.auth.model.SessaoUsuario;
import com.sistema_contabilidade.auth.repository.SessaoUsuarioRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class SessaoUsuarioService {

  private final SessaoUsuarioRepository sessaoUsuarioRepository;
  private final SessionCipherService sessionCipherService;

  @Value("${app.session.ttl-minutes:30}")
  private long ttlMinutes;

  @Transactional
  public String criarSessao(UUID usuarioId) {
    return criarSessao(
        new SessionCreationRequest(
            usuarioId,
            com.sistema_contabilidade.auth.config.AuthProvider.LOCAL,
            null,
            null,
            null,
            Set.of(),
            null));
  }

  @Transactional
  public String criarSessao(SessionCreationRequest request) {
    SessaoUsuario sessao = new SessaoUsuario();
    sessao.setUsuarioId(request.usuarioId());
    sessao.setCriadaEm(LocalDateTime.now());
    sessao.setAtualizadaEm(LocalDateTime.now());
    sessao.setExpiraEm(LocalDateTime.now().plusMinutes(ttlMinutes));
    sessao.setRevogada(false);
    sessao.setAuthProvider(request.authProvider());
    sessao.setAuthUsername(request.authUsername());
    sessao.setCognitoSub(request.cognitoSub());
    sessao.setRefreshTokenCiphertext(encryptRefreshToken(request.refreshToken()));
    sessao.setGroupsSnapshot(serializeGroups(request.groups()));
    sessao.setGroupsHash(request.groupsHash());

    SessaoUsuario salva = sessaoUsuarioRepository.save(sessao);
    return sessionCipherService.encrypt(salva.getId());
  }

  public UUID validarSessao(String tokenCriptografado) {
    return obterSessaoAtiva(tokenCriptografado).getUsuarioId();
  }

  public SessaoUsuario obterSessaoAtiva(String tokenCriptografado) {
    UUID sessaoId = sessionCipherService.decrypt(tokenCriptografado);
    SessaoUsuario sessao =
        sessaoUsuarioRepository
            .findByIdAndRevogadaFalse(sessaoId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessao nao encontrada"));

    if (sessao.getExpiraEm().isBefore(LocalDateTime.now())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessao expirada");
    }

    return sessao;
  }

  @Transactional
  public void revogarSessao(String tokenCriptografado) {
    SessaoUsuario sessao = obterSessaoAtiva(tokenCriptografado);
    sessao.setRevogada(true);
    sessao.setRevogadaEm(LocalDateTime.now());
    sessao.setAtualizadaEm(LocalDateTime.now());
    sessaoUsuarioRepository.save(sessao);
  }

  @Transactional
  public void revogarSessoesAtivas(UUID usuarioId) {
    List<SessaoUsuario> sessoesAtivas =
        sessaoUsuarioRepository.findAllByUsuarioIdAndRevogadaFalse(usuarioId);
    for (SessaoUsuario sessao : sessoesAtivas) {
      sessao.setRevogada(true);
      sessao.setRevogadaEm(LocalDateTime.now());
      sessao.setAtualizadaEm(LocalDateTime.now());
    }
    sessaoUsuarioRepository.saveAll(sessoesAtivas);
  }

  @Transactional
  public SessaoUsuario atualizarSessao(
      SessaoUsuario sessaoUsuario, AuthProviderRefreshResult refreshResult, String groupsHash) {
    sessaoUsuario.setAtualizadaEm(LocalDateTime.now());
    sessaoUsuario.setAuthUsername(refreshResult.providerUsername());
    sessaoUsuario.setCognitoSub(refreshResult.cognitoSub());
    if (refreshResult.refreshToken() != null && !refreshResult.refreshToken().isBlank()) {
      sessaoUsuario.setRefreshTokenCiphertext(encryptRefreshToken(refreshResult.refreshToken()));
    }
    sessaoUsuario.setGroupsSnapshot(serializeGroups(refreshResult.groups()));
    sessaoUsuario.setGroupsHash(groupsHash);
    return sessaoUsuarioRepository.save(sessaoUsuario);
  }

  public String decryptRefreshToken(SessaoUsuario sessaoUsuario) {
    if (sessaoUsuario.getRefreshTokenCiphertext() == null
        || sessaoUsuario.getRefreshTokenCiphertext().isBlank()) {
      return null;
    }
    return sessionCipherService.decryptString(sessaoUsuario.getRefreshTokenCiphertext());
  }

  public Set<String> groupsSnapshot(SessaoUsuario sessaoUsuario) {
    if (sessaoUsuario.getGroupsSnapshot() == null || sessaoUsuario.getGroupsSnapshot().isBlank()) {
      return Set.of();
    }
    Set<String> groups = new LinkedHashSet<>();
    for (String value : sessaoUsuario.getGroupsSnapshot().split("\\R")) {
      if (value != null && !value.isBlank()) {
        groups.add(value.trim());
      }
    }
    return Set.copyOf(groups);
  }

  private String encryptRefreshToken(String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) {
      return null;
    }
    return sessionCipherService.encryptString(refreshToken);
  }

  private String serializeGroups(Set<String> groups) {
    return groups == null || groups.isEmpty() ? null : String.join("\n", groups);
  }
}
