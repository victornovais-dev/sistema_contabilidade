package com.sistema_contabilidade.usuario.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.common.mapper.RbacMapper;
import com.sistema_contabilidade.common.mapper.UsuarioMapper;
import com.sistema_contabilidade.rbac.dto.RoleResumoDto;
import com.sistema_contabilidade.rbac.dto.UsuarioComRolesDto;
import com.sistema_contabilidade.rbac.model.Role;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
import com.sistema_contabilidade.security.service.CustomUserDetailsService;
import com.sistema_contabilidade.security.validation.InputSanitizer;
import com.sistema_contabilidade.usuario.dto.UsuarioCreateRequest;
import com.sistema_contabilidade.usuario.dto.UsuarioDto;
import com.sistema_contabilidade.usuario.dto.UsuarioSelfUpdateRequest;
import com.sistema_contabilidade.usuario.dto.UsuarioUpdateByEmailRequest;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@DisplayName("UsuarioService unit tests")
class UsuarioServiceTest {

  @Mock private UsuarioRepository usuarioRepository;
  @Mock private RoleRepository roleRepository;

  @Mock private PasswordEncoder passwordEncoder;
  @Mock private UsuarioMapper usuarioMapper;
  @Mock private RbacMapper rbacMapper;
  @Mock private CustomUserDetailsService customUserDetailsService;
  @Spy private InputSanitizer inputSanitizer = new InputSanitizer();

  @InjectMocks private UsuarioService usuarioService;

  @Test
  @DisplayName("Deve criar usuario com sucesso")
  void criarDeveSalvarUsuario() {
    // Arrange
    UsuarioCreateRequest request =
        new UsuarioCreateRequest("Ana", "ana@email.com", "123456", "ADMIN", null);
    Usuario salvo =
        novoUsuario(
            UUID.fromString("11111111-1111-1111-1111-111111111111"), "Ana", "ana@email.com");
    Role role = new Role();
    role.setNome("ADMIN");
    UsuarioDto response = new UsuarioDto(salvo.getId(), salvo.getNome(), salvo.getEmail(), null);
    when(usuarioRepository.findByEmail("ana@email.com")).thenReturn(Optional.empty());
    when(roleRepository.findByNomeIgnoreCase("ADMIN")).thenReturn(Optional.of(role));
    when(passwordEncoder.encode("123456")).thenReturn("encoded-123456");
    when(usuarioRepository.save(any(Usuario.class))).thenReturn(salvo);
    when(usuarioMapper.toDto(salvo)).thenReturn(response);

    // Act
    UsuarioDto resultado = usuarioService.save(request);

    // Assert
    assertEquals(response, resultado);
    verify(usuarioRepository).save(any(Usuario.class));
    verify(roleRepository).findByNomeIgnoreCase("ADMIN");
  }

  @Test
  @DisplayName("Deve criar usuario com multiplas roles")
  void criarComMultiplasRolesDeveSalvarUsuario() {
    UsuarioCreateRequest request =
        new UsuarioCreateRequest(
            "Ana", "ana@email.com", "123456", null, Set.of("ADMIN", "TARCISIO"));
    Usuario salvo =
        novoUsuario(
            UUID.fromString("12121212-1212-1212-1212-121212121212"), "Ana", "ana@email.com");
    Role admin = new Role();
    admin.setNome("ADMIN");
    Role tarcisio = new Role();
    tarcisio.setNome("TARCISIO");
    UsuarioDto response = new UsuarioDto(salvo.getId(), salvo.getNome(), salvo.getEmail(), null);

    when(usuarioRepository.findByEmail("ana@email.com")).thenReturn(Optional.empty());
    when(roleRepository.findByNomeIgnoreCase("ADMIN")).thenReturn(Optional.of(admin));
    when(roleRepository.findByNomeIgnoreCase("TARCISIO")).thenReturn(Optional.of(tarcisio));
    when(passwordEncoder.encode("123456")).thenReturn("encoded-123456");
    when(usuarioRepository.save(any(Usuario.class))).thenReturn(salvo);
    when(usuarioMapper.toDto(salvo)).thenReturn(response);

    UsuarioDto resultado = usuarioService.save(request);

    assertEquals(response, resultado);
    verify(roleRepository).findByNomeIgnoreCase("ADMIN");
    verify(roleRepository).findByNomeIgnoreCase("TARCISIO");
  }

  @Test
  @DisplayName("Deve criar usuario com role cadastrada dinamicamente no banco")
  void criarComRoleDinamicaDeveSalvarUsuario() {
    UsuarioCreateRequest request =
        new UsuarioCreateRequest("Ana", "ana@email.com", "123456", "MARCOS PONTES", null);
    Usuario salvo =
        novoUsuario(
            UUID.fromString("34343434-3434-3434-3434-343434343434"), "Ana", "ana@email.com");
    Role role = role("MARCOS PONTES");
    UsuarioDto response = new UsuarioDto(salvo.getId(), salvo.getNome(), salvo.getEmail(), null);

    when(usuarioRepository.findByEmail("ana@email.com")).thenReturn(Optional.empty());
    when(roleRepository.findByNomeIgnoreCase("MARCOS PONTES")).thenReturn(Optional.of(role));
    when(passwordEncoder.encode("123456")).thenReturn("encoded-123456");
    when(usuarioRepository.save(any(Usuario.class))).thenReturn(salvo);
    when(usuarioMapper.toDto(salvo)).thenReturn(response);

    UsuarioDto resultado = usuarioService.save(request);

    assertEquals(response, resultado);
    verify(roleRepository).findByNomeIgnoreCase("MARCOS PONTES");
  }

  @Test
  @DisplayName("Deve listar todos os usuarios")
  void listarTodosDeveRetornarLista() {
    // Arrange
    List<Usuario> usuarios =
        List.of(
            novoUsuario(
                UUID.fromString("11111111-1111-1111-1111-111111111111"), "Ana", "ana@email.com"),
            novoUsuario(
                UUID.fromString("22222222-2222-2222-2222-222222222222"), "Bia", "bia@email.com"));
    UsuarioDto response1 =
        new UsuarioDto(
            usuarios.get(0).getId(), usuarios.get(0).getNome(), usuarios.get(0).getEmail(), null);
    UsuarioDto response2 =
        new UsuarioDto(
            usuarios.get(1).getId(), usuarios.get(1).getNome(), usuarios.get(1).getEmail(), null);
    when(usuarioRepository.findAll()).thenReturn(usuarios);
    when(usuarioMapper.toDto(usuarios.get(0))).thenReturn(response1);
    when(usuarioMapper.toDto(usuarios.get(1))).thenReturn(response2);

    // Act
    List<UsuarioDto> resultado = usuarioService.listarTodos();

    // Assert
    assertEquals(2, resultado.size());
    verify(usuarioRepository).findAll();
  }

  @Test
  @DisplayName("Deve buscar usuario por id quando existe")
  void buscarPorIdQuandoExisteDeveRetornarUsuario() {
    // Arrange
    UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
    Usuario usuario = novoUsuario(id, "Ana", "ana@email.com");
    when(usuarioRepository.findById(id)).thenReturn(Optional.of(usuario));

    // Act
    Usuario resultado = usuarioService.buscarPorId(id);

    // Assert
    assertEquals(usuario, resultado);
    verify(usuarioRepository).findById(id);
  }

  @Test
  @DisplayName("Deve retornar 404 ao buscar usuario inexistente")
  void buscarPorIdQuandoNaoExisteDeveLancarNotFound() {
    // Arrange
    UUID id = UUID.fromString("99999999-9999-9999-9999-999999999999");
    when(usuarioRepository.findById(id)).thenReturn(Optional.empty());

    // Act
    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> usuarioService.buscarPorId(id));

    // Assert
    assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    assertTrue(ex.getReason().contains("Usuario nao encontrado"));
  }

  @Test
  @DisplayName("Deve atualizar usuario quando ele existe")
  void updateQuandoExisteDeveAtualizarCampos() {
    // Arrange
    UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UsuarioDto request = new UsuarioDto(null, "Ana Maria", "ana.maria@email.com", null);
    Usuario entidadeEntrada = new Usuario();
    entidadeEntrada.setNome("Ana Maria");
    entidadeEntrada.setEmail("ana.maria@email.com");
    Usuario existente = novoUsuario(id, "Ana", "ana@email.com");
    Usuario salvo = novoUsuario(id, "Ana Maria", "ana.maria@email.com");
    UsuarioDto response = new UsuarioDto(id, "Ana Maria", "ana.maria@email.com", null);
    when(usuarioMapper.toEntity(request)).thenReturn(entidadeEntrada);
    when(usuarioRepository.findById(id)).thenReturn(Optional.of(existente));
    when(usuarioRepository.findByEmail("ana.maria@email.com")).thenReturn(Optional.empty());
    when(usuarioRepository.save(existente)).thenReturn(salvo);
    when(usuarioMapper.toDto(salvo)).thenReturn(response);

    // Act
    UsuarioDto resultado = usuarioService.update(id, request);

    // Assert
    assertEquals("Ana Maria", resultado.getNome());
    assertEquals("ana.maria@email.com", resultado.getEmail());
    verify(usuarioRepository).findById(id);
    verify(usuarioRepository).save(existente);
    verify(customUserDetailsService).atualizarCacheUsuario(id, "ana.maria@email.com");
    verify(customUserDetailsService).removerCacheUsuario(id, "ana@email.com");
  }

  @Test
  @DisplayName("Deve atualizar senha quando informada")
  void updateQuandoSenhaInformadaDeveCodificarSenha() {
    UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UsuarioDto request = new UsuarioDto(null, "Ana Maria", "ana.maria@email.com", "nova-senha");
    Usuario entidadeEntrada = new Usuario();
    entidadeEntrada.setNome("Ana Maria");
    entidadeEntrada.setEmail("ana.maria@email.com");
    entidadeEntrada.setSenha("nova-senha");
    Usuario existente = novoUsuario(id, "Ana", "ana@email.com");
    Usuario salvo = novoUsuario(id, "Ana Maria", "ana.maria@email.com");
    salvo.setSenha("encoded-nova-senha");
    UsuarioDto response = new UsuarioDto(id, "Ana Maria", "ana.maria@email.com", null);

    when(usuarioMapper.toEntity(request)).thenReturn(entidadeEntrada);
    when(usuarioRepository.findById(id)).thenReturn(Optional.of(existente));
    when(usuarioRepository.findByEmail("ana.maria@email.com")).thenReturn(Optional.empty());
    when(passwordEncoder.encode("nova-senha")).thenReturn("encoded-nova-senha");
    when(usuarioRepository.save(existente)).thenReturn(salvo);
    when(usuarioMapper.toDto(salvo)).thenReturn(response);

    UsuarioDto resultado = usuarioService.update(id, request);

    assertEquals("Ana Maria", resultado.getNome());
    assertEquals("encoded-nova-senha", existente.getSenha());
    verify(customUserDetailsService).atualizarCacheUsuario(id, "ana.maria@email.com");
    verify(customUserDetailsService).removerCacheUsuario(id, "ana@email.com");
  }

  @Test
  @DisplayName("Deve retornar 404 ao atualizar usuario inexistente")
  void atualizarQuandoNaoExisteDeveLancarNotFound() {
    // Arrange
    UsuarioDto request = new UsuarioDto(null, "Ana Maria", "ana.maria@email.com", null);
    UUID id = UUID.fromString("99999999-9999-9999-9999-999999999999");
    when(usuarioRepository.findById(id)).thenReturn(Optional.empty());

    // Act / Assert
    assertThrows(ResponseStatusException.class, () -> usuarioService.update(id, request));
    verify(usuarioRepository).findById(id);
    verify(usuarioRepository, never()).save(any());
  }

  @Test
  @DisplayName("Deve retornar conflito ao criar usuario com email ja cadastrado")
  void criarComEmailDuplicadoDeveRetornarConflito() {
    UsuarioCreateRequest request =
        new UsuarioCreateRequest("Ana", "ana@email.com", "123456", "ADMIN", null);
    Usuario existente =
        novoUsuario(
            UUID.fromString("11111111-1111-1111-1111-111111111111"), "Ana", "ana@email.com");
    when(usuarioRepository.findByEmail("ana@email.com")).thenReturn(Optional.of(existente));

    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> usuarioService.save(request));

    assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    assertEquals("Email ja cadastrado", ex.getReason());
    verify(usuarioRepository, never()).save(any(Usuario.class));
  }

  @Test
  @DisplayName("Deve retornar bad request ao criar usuario com role invalida")
  void criarComRoleInvalidaDeveRetornarBadRequest() {
    UsuarioCreateRequest request =
        new UsuarioCreateRequest("Ana", "ana@email.com", "123456", "INVALIDA", null);
    when(usuarioRepository.findByEmail("ana@email.com")).thenReturn(Optional.empty());
    when(roleRepository.findByNomeIgnoreCase("INVALIDA")).thenReturn(Optional.empty());

    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> usuarioService.save(request));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    assertTrue(ex.getReason().contains("Role nao encontrada"));
    verify(usuarioRepository, never()).save(any(Usuario.class));
  }

  @Test
  @DisplayName("Deve retornar bad request ao criar usuario sem roles")
  void criarSemRoleDeveRetornarBadRequest() {
    UsuarioCreateRequest request =
        new UsuarioCreateRequest("Ana", "ana@email.com", "123456", null, Set.of());
    when(usuarioRepository.findByEmail("ana@email.com")).thenReturn(Optional.empty());

    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> usuarioService.save(request));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    assertEquals("Ao menos uma role deve ser informada", ex.getReason());
    verify(usuarioRepository, never()).save(any(Usuario.class));
  }

  @Test
  @DisplayName("Deve retornar bad request ao criar usuario com SUPPORT e MANAGER ao mesmo tempo")
  void criarComSupportEManagerDeveRetornarBadRequest() {
    UsuarioCreateRequest request =
        new UsuarioCreateRequest(
            "Ana", "ana@email.com", "123456", null, Set.of("SUPPORT", "MANAGER"));
    when(usuarioRepository.findByEmail("ana@email.com")).thenReturn(Optional.empty());

    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> usuarioService.save(request));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    assertEquals("Usuario nao pode ter as roles SUPPORT e MANAGER ao mesmo tempo.", ex.getReason());
    verify(usuarioRepository, never()).save(any(Usuario.class));
    verify(roleRepository, never()).findByNomeIgnoreCase(any());
  }

  @Test
  @DisplayName("Deve permitir update quando email ja pertence ao mesmo usuario")
  void updateComMesmoEmailDoUsuarioAtualDevePermitir() {
    UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UsuarioDto request = new UsuarioDto(null, "Ana Maria", "ana@email.com", null);
    Usuario entidadeEntrada = new Usuario();
    entidadeEntrada.setNome("Ana Maria");
    entidadeEntrada.setEmail("ana@email.com");
    Usuario existente = novoUsuario(id, "Ana", "ana@email.com");
    Usuario salvo = novoUsuario(id, "Ana Maria", "ana@email.com");
    UsuarioDto response = new UsuarioDto(id, "Ana Maria", "ana@email.com", null);

    when(usuarioMapper.toEntity(request)).thenReturn(entidadeEntrada);
    when(usuarioRepository.findById(id)).thenReturn(Optional.of(existente));
    when(usuarioRepository.findByEmail("ana@email.com")).thenReturn(Optional.of(existente));
    when(usuarioRepository.save(existente)).thenReturn(salvo);
    when(usuarioMapper.toDto(salvo)).thenReturn(response);

    UsuarioDto resultado = usuarioService.update(id, request);

    assertEquals("Ana Maria", resultado.getNome());
    verify(usuarioRepository).save(existente);
    verify(customUserDetailsService).atualizarCacheUsuario(id, "ana@email.com");
    verify(customUserDetailsService, never())
        .removerCacheUsuario(any(UUID.class), any(String.class));
  }

  @Test
  @DisplayName("Deve retornar conflito ao atualizar email para outro usuario existente")
  void updateComEmailDeOutroUsuarioDeveRetornarConflito() {
    UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UUID outroId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    UsuarioDto request = new UsuarioDto(null, "Ana Maria", "bia@email.com", null);
    Usuario entidadeEntrada = new Usuario();
    entidadeEntrada.setNome("Ana Maria");
    entidadeEntrada.setEmail("bia@email.com");
    Usuario existente = novoUsuario(id, "Ana", "ana@email.com");
    Usuario outroUsuario = novoUsuario(outroId, "Bia", "bia@email.com");

    when(usuarioMapper.toEntity(request)).thenReturn(entidadeEntrada);
    when(usuarioRepository.findById(id)).thenReturn(Optional.of(existente));
    when(usuarioRepository.findByEmail("bia@email.com")).thenReturn(Optional.of(outroUsuario));

    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> usuarioService.update(id, request));

    assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    assertEquals("Email ja cadastrado", ex.getReason());
    verify(usuarioRepository, never()).save(any(Usuario.class));
  }

  @Test
  @DisplayName("Deve deletar usuario quando ele existe")
  void deletarQuandoExisteDeveRemoverUsuario() {
    // Arrange
    UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
    Usuario usuario = novoUsuario(id, "Ana", "ana@email.com");
    when(usuarioRepository.findById(id)).thenReturn(Optional.of(usuario));

    // Act
    usuarioService.deletar(id);

    // Assert
    verify(usuarioRepository).findById(id);
    verify(customUserDetailsService).removerCacheUsuario(id, "ana@email.com");
    verify(usuarioRepository).delete(usuario);
  }

  @Test
  @DisplayName("Deve retornar 404 ao deletar usuario inexistente")
  void deletarQuandoNaoExisteDeveLancarNotFound() {
    // Arrange
    UUID id = UUID.fromString("99999999-9999-9999-9999-999999999999");
    when(usuarioRepository.findById(id)).thenReturn(Optional.empty());

    // Act / Assert
    assertThrows(ResponseStatusException.class, () -> usuarioService.deletar(id));
    verify(usuarioRepository).findById(id);
    verify(usuarioRepository, never()).delete(any(Usuario.class));
  }

  @Test
  @DisplayName("Deve buscar usuario dto por id quando existe")
  void findByIdQuandoExisteDeveRetornarDto() {
    UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
    Usuario usuario = novoUsuario(id, "Ana", "ana@email.com");
    UsuarioDto dto = new UsuarioDto(id, "Ana", "ana@email.com", null);
    when(usuarioRepository.findById(id)).thenReturn(Optional.of(usuario));
    when(usuarioMapper.toDto(usuario)).thenReturn(dto);

    UsuarioDto resultado = usuarioService.findById(id);

    assertEquals(dto, resultado);
  }

  @Test
  @DisplayName("Deve retornar 404 ao buscar usuario dto inexistente")
  void findByIdQuandoNaoExisteDeveLancarNotFound() {
    UUID id = UUID.fromString("99999999-9999-9999-9999-999999999999");
    when(usuarioRepository.findById(id)).thenReturn(Optional.empty());

    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> usuarioService.findById(id));

    assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
  }

  @Test
  @DisplayName("Deve buscar usuario com roles por email")
  void findComRolesByEmailDeveRetornarDto() {
    Usuario usuario =
        novoUsuario(
            UUID.fromString("55555555-5555-5555-5555-555555555555"), "Admin", "admin@email.com");
    UsuarioComRolesDto dto =
        new UsuarioComRolesDto(
            usuario.getId(), "Admin", "admin@email.com", Set.of(new RoleResumoDto(null, "ADMIN")));
    when(usuarioRepository.findByEmail("admin@email.com")).thenReturn(Optional.of(usuario));
    when(rbacMapper.toUsuarioComRolesDto(usuario)).thenReturn(dto);

    UsuarioComRolesDto resultado = usuarioService.findComRolesByEmail("admin@email.com");

    assertEquals("admin@email.com", resultado.getEmail());
    assertEquals(1, resultado.getRoles().size());
  }

  @Test
  @DisplayName("Deve atualizar roles por email")
  void updateByEmailDeveAtualizarRoles() {
    Usuario usuario =
        novoUsuario(
            UUID.fromString("66666666-6666-6666-6666-666666666666"), "Suporte", "sup@email.com");
    usuario.getRoles().add(role("TARCISIO"));
    UsuarioUpdateByEmailRequest request =
        new UsuarioUpdateByEmailRequest("sup@email.com", null, Set.of("ADMIN", "TARCISIO"));
    UsuarioComRolesDto dto =
        new UsuarioComRolesDto(
            usuario.getId(),
            "Suporte",
            "sup@email.com",
            Set.of(new RoleResumoDto(null, "ADMIN"), new RoleResumoDto(null, "TARCISIO")));
    when(usuarioRepository.findByEmail("sup@email.com")).thenReturn(Optional.of(usuario));
    when(roleRepository.findByNomeIgnoreCase("ADMIN")).thenReturn(Optional.of(role("ADMIN")));
    when(roleRepository.findByNomeIgnoreCase("TARCISIO")).thenReturn(Optional.of(role("TARCISIO")));
    when(usuarioRepository.save(usuario)).thenReturn(usuario);
    when(rbacMapper.toUsuarioComRolesDto(usuario)).thenReturn(dto);

    UsuarioComRolesDto resultado = usuarioService.updateByEmail(request);

    assertEquals(2, usuario.getRoles().size());
    assertEquals(2, resultado.getRoles().size());
    verify(customUserDetailsService).atualizarCacheUsuario(usuario.getId(), "sup@email.com");
  }

  @Test
  @DisplayName("Deve atualizar roles por email com role dinamica cadastrada no banco")
  void updateByEmailComRoleDinamicaDeveAtualizarRoles() {
    Usuario usuario =
        novoUsuario(
            UUID.fromString("56565656-5656-5656-5656-565656565656"), "Suporte", "sup@email.com");
    UsuarioUpdateByEmailRequest request =
        new UsuarioUpdateByEmailRequest("sup@email.com", null, Set.of("MARCOS PONTES"));
    UsuarioComRolesDto dto =
        new UsuarioComRolesDto(
            usuario.getId(),
            "Suporte",
            "sup@email.com",
            Set.of(new RoleResumoDto(null, "MARCOS PONTES")));

    when(usuarioRepository.findByEmail("sup@email.com")).thenReturn(Optional.of(usuario));
    when(roleRepository.findByNomeIgnoreCase("MARCOS PONTES"))
        .thenReturn(Optional.of(role("MARCOS PONTES")));
    when(usuarioRepository.save(usuario)).thenReturn(usuario);
    when(rbacMapper.toUsuarioComRolesDto(usuario)).thenReturn(dto);

    UsuarioComRolesDto resultado = usuarioService.updateByEmail(request);

    assertEquals(1, usuario.getRoles().size());
    assertEquals("MARCOS PONTES", usuario.getRoles().iterator().next().getNome());
    assertEquals(1, resultado.getRoles().size());
    verify(customUserDetailsService).atualizarCacheUsuario(usuario.getId(), "sup@email.com");
  }

  @Test
  @DisplayName("Deve atualizar o proprio perfil com troca de email e senha")
  void updatePerfilDeveAtualizarEmailESenha() {
    Usuario usuario =
        novoUsuario(
            UUID.fromString("77777777-7777-7777-7777-777777777777"), "Ana", "ana@email.com");
    UsuarioSelfUpdateRequest request =
        new UsuarioSelfUpdateRequest("Ana Clara", "ana.clara@email.com", "nova-senha");
    Usuario salvo = novoUsuario(usuario.getId(), "Ana Clara", "ana.clara@email.com");
    salvo.setSenha("encoded-nova-senha");
    UsuarioDto dto = new UsuarioDto(usuario.getId(), "Ana Clara", "ana.clara@email.com", null);

    when(usuarioRepository.findByEmail("ana@email.com")).thenReturn(Optional.of(usuario));
    when(usuarioRepository.findByEmail("ana.clara@email.com")).thenReturn(Optional.empty());
    when(passwordEncoder.encode("nova-senha")).thenReturn("encoded-nova-senha");
    when(usuarioRepository.save(usuario)).thenReturn(salvo);
    when(usuarioMapper.toDto(salvo)).thenReturn(dto);

    UsuarioDto resultado = usuarioService.updatePerfil("ana@email.com", request);

    assertEquals("Ana Clara", resultado.getNome());
    verify(customUserDetailsService).atualizarCacheUsuario(usuario.getId(), "ana.clara@email.com");
    verify(customUserDetailsService).removerCacheUsuario(usuario.getId(), "ana@email.com");
  }

  @Test
  @DisplayName("Deve buscar nome por email normalizado")
  void findNomeByEmailDeveBuscarNomePorEmailNormalizado() {
    Usuario usuario =
        novoUsuario(
            UUID.fromString("88888888-8888-8888-8888-888888888888"), "Maria", "maria@email.com");
    when(usuarioRepository.findByEmail("maria@email.com")).thenReturn(Optional.of(usuario));

    String nome = usuarioService.findNomeByEmail("  maria@email.com ");

    assertEquals("Maria", nome);
  }

  @Test
  @DisplayName("Deve retornar role nao encontrada ao salvar com role ausente")
  void saveDeveRetornarRoleNaoEncontradaQuandoRoleNaoExistir() {
    UsuarioCreateRequest request =
        new UsuarioCreateRequest("Ana", "ana@email.com", "123456", "ADMIN", null);
    when(usuarioRepository.findByEmail("ana@email.com")).thenReturn(Optional.empty());
    when(roleRepository.findByNomeIgnoreCase("ADMIN")).thenReturn(Optional.empty());

    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> usuarioService.save(request));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    assertTrue(ex.getReason().contains("Role nao encontrada"));
  }

  @Test
  @DisplayName("Deve retornar bad request ao atualizar roles por email sem roles")
  void updateByEmailSemRolesDeveRetornarBadRequest() {
    UsuarioUpdateByEmailRequest request =
        new UsuarioUpdateByEmailRequest("sup@email.com", null, Set.of());
    Usuario usuario =
        novoUsuario(
            UUID.fromString("99999999-9999-9999-9999-999999999999"), "Suporte", "sup@email.com");
    when(usuarioRepository.findByEmail("sup@email.com")).thenReturn(Optional.of(usuario));

    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> usuarioService.updateByEmail(request));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    assertEquals("Ao menos uma role deve ser informada", ex.getReason());
  }

  @Test
  @DisplayName(
      "Deve retornar bad request ao atualizar usuario com SUPPORT e MANAGER ao mesmo tempo")
  void updateByEmailComSupportEManagerDeveRetornarBadRequest() {
    UsuarioUpdateByEmailRequest request =
        new UsuarioUpdateByEmailRequest("sup@email.com", null, Set.of("SUPPORT", "MANAGER"));
    Usuario usuario =
        novoUsuario(
            UUID.fromString("11111111-2222-3333-4444-555555555555"), "Suporte", "sup@email.com");
    when(usuarioRepository.findByEmail("sup@email.com")).thenReturn(Optional.of(usuario));

    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> usuarioService.updateByEmail(request));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    assertEquals("Usuario nao pode ter as roles SUPPORT e MANAGER ao mesmo tempo.", ex.getReason());
    verify(usuarioRepository, never()).save(any(Usuario.class));
    verify(roleRepository, never()).findByNomeIgnoreCase(any());
  }

  private Usuario novoUsuario(UUID id, String nome, String email) {
    Usuario usuario = new Usuario();
    usuario.setId(id);
    usuario.setNome(nome);
    usuario.setEmail(email);
    usuario.setSenha(id == null ? "654321" : "123456");
    return usuario;
  }

  private Role role(String nome) {
    Role role = new Role();
    role.setNome(nome);
    return role;
  }
}
