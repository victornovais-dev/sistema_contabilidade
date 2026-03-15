package com.sistema_contabilidade.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.auth.model.SessaoUsuario;
import com.sistema_contabilidade.auth.repository.SessaoUsuarioRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessaoUsuarioService unit tests")
class SessaoUsuarioServiceTest {

  @Mock private SessaoUsuarioRepository sessaoUsuarioRepository;

  @Mock private SessionCipherService sessionCipherService;

  @InjectMocks private SessaoUsuarioService sessaoUsuarioService;

  @Test
  @DisplayName("Deve criar sessao e retornar token criptografado")
  void deveCriarSessao() {
    // Arrange
    ReflectionTestUtils.setField(sessaoUsuarioService, "ttlMinutes", 30L);
    UUID usuarioId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UUID sessaoId = UUID.fromString("22222222-2222-2222-2222-222222222222");

    when(sessaoUsuarioRepository.save(any(SessaoUsuario.class)))
        .thenAnswer(
            invocation -> {
              SessaoUsuario sessao = invocation.getArgument(0);
              sessao.setId(sessaoId);
              return sessao;
            });
    when(sessionCipherService.encrypt(sessaoId)).thenReturn("token-criptografado");

    // Act
    String token = sessaoUsuarioService.criarSessao(usuarioId);

    // Assert
    assertEquals("token-criptografado", token);
    ArgumentCaptor<SessaoUsuario> captor = ArgumentCaptor.forClass(SessaoUsuario.class);
    verify(sessaoUsuarioRepository).save(captor.capture());
    assertEquals(usuarioId, captor.getValue().getUsuarioId());
  }

  @Test
  @DisplayName("Deve validar sessao ativa")
  void deveValidarSessaoAtiva() {
    // Arrange
    UUID sessaoId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    UUID usuarioId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    SessaoUsuario sessao = new SessaoUsuario();
    sessao.setId(sessaoId);
    sessao.setUsuarioId(usuarioId);
    sessao.setExpiraEm(LocalDateTime.now().plusMinutes(10));
    sessao.setRevogada(false);

    when(sessionCipherService.decrypt("token")).thenReturn(sessaoId);
    when(sessaoUsuarioRepository.findByIdAndRevogadaFalse(sessaoId))
        .thenReturn(Optional.of(sessao));

    // Act
    UUID resultado = sessaoUsuarioService.validarSessao("token");

    // Assert
    assertEquals(usuarioId, resultado);
  }

  @Test
  @DisplayName("Deve lancar unauthorized para sessao expirada")
  void deveLancarParaSessaoExpirada() {
    // Arrange
    UUID sessaoId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    SessaoUsuario sessao = new SessaoUsuario();
    sessao.setId(sessaoId);
    sessao.setUsuarioId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    sessao.setExpiraEm(LocalDateTime.now().minusMinutes(1));
    sessao.setRevogada(false);

    when(sessionCipherService.decrypt("token")).thenReturn(sessaoId);
    when(sessaoUsuarioRepository.findByIdAndRevogadaFalse(sessaoId))
        .thenReturn(Optional.of(sessao));

    // Act / Assert
    assertThrows(ResponseStatusException.class, () -> sessaoUsuarioService.validarSessao("token"));
  }
}
