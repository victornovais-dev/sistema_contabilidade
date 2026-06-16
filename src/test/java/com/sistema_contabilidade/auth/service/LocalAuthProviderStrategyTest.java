package com.sistema_contabilidade.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.auth.config.AuthProvider;
import com.sistema_contabilidade.auth.dto.LoginRequest;
import com.sistema_contabilidade.auth.model.SessaoUsuario;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@DisplayName("LocalAuthProviderStrategy unit tests")
class LocalAuthProviderStrategyTest {

  @Mock private UsuarioRepository usuarioRepository;
  @Mock private PasswordEncoder passwordEncoder;

  @InjectMocks private LocalAuthProviderStrategy localAuthProviderStrategy;

  @Test
  @DisplayName("Deve autenticar usuario local e atualizar hash quando necessario")
  void deveAutenticarUsuarioLocalEAtualizarHashQuandoNecessario() {
    Usuario usuario = new Usuario();
    usuario.setId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    usuario.setEmail("ana@email.com");
    usuario.setSenha("hash-atual");
    usuario.setNome("Ana");
    when(usuarioRepository.findByEmail("ana@email.com")).thenReturn(Optional.of(usuario));
    when(passwordEncoder.matches("123456", "hash-atual")).thenReturn(true);
    when(passwordEncoder.upgradeEncoding("hash-atual")).thenReturn(true);
    when(passwordEncoder.encode("123456")).thenReturn("hash-novo");
    when(usuarioRepository.save(usuario)).thenReturn(usuario);

    AuthProviderLoginResult result =
        localAuthProviderStrategy.login(new LoginRequest("ana@email.com", "123456"));

    assertEquals(AuthProvider.LOCAL, result.provider());
    assertEquals("hash-novo", usuario.getSenha());
    assertEquals("ana@email.com", result.email());
    verify(usuarioRepository).save(usuario);
  }

  @Test
  @DisplayName("Deve rejeitar credenciais invalidas sem salvar usuario")
  void deveRejeitarCredenciaisInvalidasSemSalvarUsuario() {
    Usuario usuario = new Usuario();
    LoginRequest request = new LoginRequest("ana@email.com", "errada");
    usuario.setEmail("ana@email.com");
    usuario.setSenha("hash-atual");
    when(usuarioRepository.findByEmail("ana@email.com")).thenReturn(Optional.of(usuario));
    when(passwordEncoder.matches("errada", "hash-atual")).thenReturn(false);

    assertThrows(ResponseStatusException.class, () -> localAuthProviderStrategy.login(request));

    verify(usuarioRepository, never()).save(any());
  }

  @Test
  @DisplayName("Deve usar hash de protecao de timing quando usuario nao existir")
  void deveUsarHashDeProtecaoDeTimingQuandoUsuarioNaoExistir() {
    LoginRequest request = new LoginRequest("naoexiste@email.com", "123456");
    when(usuarioRepository.findByEmail("naoexiste@email.com")).thenReturn(Optional.empty());
    when(passwordEncoder.encode(anyString())).thenReturn("timing-hash");
    when(passwordEncoder.matches("123456", "timing-hash")).thenReturn(false);

    assertThrows(ResponseStatusException.class, () -> localAuthProviderStrategy.login(request));

    verify(passwordEncoder).encode(anyString());
  }

  @Test
  @DisplayName("Deve retornar dados locais no refresh")
  void deveRetornarDadosLocaisNoRefresh() {
    SessaoUsuario sessaoUsuario = new SessaoUsuario();
    sessaoUsuario.setAuthUsername("ana@email.com");
    sessaoUsuario.setCognitoSub("sub-local");

    AuthProviderRefreshResult result = localAuthProviderStrategy.refresh(sessaoUsuario);

    assertEquals(AuthProvider.LOCAL, result.provider());
    assertEquals("ana@email.com", result.email());
    assertEquals("sub-local", result.cognitoSub());
    assertEquals(true, localAuthProviderStrategy.supports(AuthProvider.LOCAL));
  }
}
