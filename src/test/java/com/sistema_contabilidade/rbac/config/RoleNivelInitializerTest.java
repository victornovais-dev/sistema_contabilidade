package com.sistema_contabilidade.rbac.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.rbac.model.Role;
import com.sistema_contabilidade.rbac.model.RoleNivel;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("RoleNivelInitializer unit tests")
class RoleNivelInitializerTest {

  @Test
  @DisplayName("Deve criar roles quando nao existem")
  void deveCriarRolesQuandoNaoExistem() {
    RoleRepository roleRepository = mock(RoleRepository.class);
    when(roleRepository.findByNome(any())).thenReturn(Optional.empty());
    RoleNivelInitializer initializer = new RoleNivelInitializer(roleRepository);

    initializer.run();

    ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
    verify(roleRepository, times(RoleNivel.values().length)).save(captor.capture());
    List<String> nomes = captor.getAllValues().stream().map(Role::getNome).toList();
    for (RoleNivel nivel : RoleNivel.values()) {
      assertTrue(nomes.contains(nivel.valorBanco()));
    }
  }

  @Test
  @DisplayName("Nao deve salvar role ja existente")
  void naoDeveSalvarRoleJaExistente() {
    RoleRepository roleRepository = mock(RoleRepository.class);
    when(roleRepository.findByNome(any()))
        .thenAnswer(
            invocation -> {
              String nome = invocation.getArgument(0);
              if ("ADMIN".equals(nome)) {
                return Optional.of(new Role());
              }
              return Optional.empty();
            });
    RoleNivelInitializer initializer = new RoleNivelInitializer(roleRepository);

    initializer.run();

    int totalEsperado = RoleNivel.values().length - 1;
    ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
    verify(roleRepository, times(totalEsperado)).save(captor.capture());
    List<String> nomes = captor.getAllValues().stream().map(Role::getNome).toList();
    assertTrue(nomes.stream().noneMatch("ADMIN"::equals));
    assertEquals(totalEsperado, nomes.size());
  }
}
