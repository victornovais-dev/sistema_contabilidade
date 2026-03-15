package com.sistema_contabilidade.auth.service;

import com.sistema_contabilidade.auth.model.SessaoUsuario;
import com.sistema_contabilidade.auth.repository.SessaoUsuarioRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
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
    SessaoUsuario sessao = new SessaoUsuario();
    sessao.setUsuarioId(usuarioId);
    sessao.setCriadaEm(LocalDateTime.now());
    sessao.setExpiraEm(LocalDateTime.now().plusMinutes(ttlMinutes));
    sessao.setRevogada(false);

    SessaoUsuario salva = sessaoUsuarioRepository.save(sessao);
    return sessionCipherService.encrypt(salva.getId());
  }

  public UUID validarSessao(String tokenCriptografado) {
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

    return sessao.getUsuarioId();
  }

  @Transactional
  public void revogarSessao(String tokenCriptografado) {
    UUID sessaoId = sessionCipherService.decrypt(tokenCriptografado);
    SessaoUsuario sessao =
        sessaoUsuarioRepository
            .findByIdAndRevogadaFalse(sessaoId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessao nao encontrada"));
    sessao.setRevogada(true);
    sessaoUsuarioRepository.save(sessao);
  }
}
