package com.sistema_contabilidade.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@DisplayName("CognitoIdentitySyncService unit tests")
class CognitoIdentitySyncServiceTest {

  @Mock private UsuarioRepository usuarioRepository;
  @Mock private PasswordEncoder passwordEncoder;

  @InjectMocks private CognitoIdentitySyncService cognitoIdentitySyncService;

  @Test
  @DisplayName("Deve criar usuario local quando identidade Cognito ainda nao existir")
  void deveCriarUsuarioLocalQuandoIdentidadeCognitoAindaNaoExistir() {
    AuthProviderLoginResult authResult =
        new AuthProviderLoginResult(
            com.sistema_contabilidade.auth.config.AuthProvider.COGNITO,
            null,
            "ana.provider",
            "ana@email.com",
            "Ana",
            "sub-123",
            Set.of("ADMIN"),
            null,
            null);
    when(usuarioRepository.findByCognitoSub("sub-123")).thenReturn(Optional.empty());
    when(usuarioRepository.findByEmail("ana@email.com")).thenReturn(Optional.empty());
    when(passwordEncoder.encode(any())).thenReturn("encoded-password");
    when(usuarioRepository.save(any(Usuario.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, Usuario.class));

    Usuario usuario = cognitoIdentitySyncService.synchronizeLoginIdentity(authResult);

    assertEquals("Ana", usuario.getNome());
    assertEquals("ana@email.com", usuario.getEmail());
    assertEquals("sub-123", usuario.getCognitoSub());
    assertEquals("ana.provider", usuario.getCognitoUsername());
    assertEquals("encoded-password", usuario.getSenha());
  }

  @Test
  @DisplayName("Deve reutilizar usuario existente e preencher senha quando estiver em branco")
  void deveReutilizarUsuarioExistenteEPreencherSenhaQuandoEstiverEmBranco() {
    Usuario existente = new Usuario();
    existente.setId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    existente.setEmail("bia@email.com");
    existente.setSenha(" ");
    when(usuarioRepository.findByCognitoSub("sub-999")).thenReturn(Optional.of(existente));
    when(usuarioRepository.findByEmail("bia@email.com")).thenReturn(Optional.of(existente));
    when(passwordEncoder.encode(any())).thenReturn("encoded-updated");
    when(usuarioRepository.save(any(Usuario.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, Usuario.class));

    AuthProviderRefreshResult authResult =
        new AuthProviderRefreshResult(
            com.sistema_contabilidade.auth.config.AuthProvider.COGNITO,
            "bia.provider",
            "bia@email.com",
            " ",
            "sub-999",
            Set.of("SUPPORT"),
            null);

    Usuario usuario = cognitoIdentitySyncService.synchronizeRefreshIdentity(authResult);

    assertEquals("bia@email.com", usuario.getNome());
    assertEquals("encoded-updated", usuario.getSenha());
    assertEquals("bia.provider", usuario.getCognitoUsername());
  }

  @Test
  @DisplayName("Deve falhar quando cognitoSub e email apontarem para usuarios diferentes")
  void deveFalharQuandoCognitoSubEEmailApontaremParaUsuariosDiferentes() {
    Usuario bySub = new Usuario();
    bySub.setId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    Usuario byEmail = new Usuario();
    byEmail.setId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
    when(usuarioRepository.findByCognitoSub("sub-1")).thenReturn(Optional.of(bySub));
    when(usuarioRepository.findByEmail("conflito@email.com")).thenReturn(Optional.of(byEmail));

    AuthProviderRefreshResult authResult =
        new AuthProviderRefreshResult(
            com.sistema_contabilidade.auth.config.AuthProvider.COGNITO,
            "conflito.provider",
            "conflito@email.com",
            "Conflito",
            "sub-1",
            Set.of(),
            null);

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class,
            () -> cognitoIdentitySyncService.synchronizeRefreshIdentity(authResult));

    assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
  }

  @Test
  @DisplayName("Deve normalizar campos em branco para nulo")
  void deveNormalizarCamposEmBrancoParaNulo() {
    when(usuarioRepository.findByEmail("sem-sub@email.com")).thenReturn(Optional.empty());
    when(passwordEncoder.encode(any())).thenReturn("encoded-password");
    when(usuarioRepository.save(any(Usuario.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, Usuario.class));

    AuthProviderRefreshResult authResult =
        new AuthProviderRefreshResult(
            com.sistema_contabilidade.auth.config.AuthProvider.COGNITO,
            " ",
            "sem-sub@email.com",
            "Nome",
            " ",
            Set.of(),
            null);

    Usuario usuario = cognitoIdentitySyncService.synchronizeRefreshIdentity(authResult);

    assertNull(usuario.getCognitoSub());
    assertNull(usuario.getCognitoUsername());
    assertNotNull(usuario.getSenha());
  }

  @Test
  @DisplayName("Deve traduzir erro de integridade para conflito HTTP")
  void deveTraduzirErroDeIntegridadeParaConflitoHttp() {
    when(usuarioRepository.findByCognitoSub("sub-erro")).thenReturn(Optional.empty());
    when(usuarioRepository.findByEmail("erro@email.com")).thenReturn(Optional.empty());
    when(passwordEncoder.encode(any())).thenReturn("encoded-password");
    when(usuarioRepository.save(any(Usuario.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate"));

    AuthProviderRefreshResult authResult =
        new AuthProviderRefreshResult(
            com.sistema_contabilidade.auth.config.AuthProvider.COGNITO,
            "erro.provider",
            "erro@email.com",
            "Erro",
            "sub-erro",
            Set.of(),
            null);

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class,
            () -> cognitoIdentitySyncService.synchronizeRefreshIdentity(authResult));

    assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    verify(usuarioRepository).save(any(Usuario.class));
  }
}
