package com.sistema_contabilidade.rbac.config;

import com.sistema_contabilidade.rbac.model.Role;
import com.sistema_contabilidade.rbac.model.RoleNivel;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoleNivelInitializer implements CommandLineRunner {

  private static final List<String> ROLES_REMOVIDAS = List.of("KIM KATAGUIRI", "VALDEMAR");

  private final RoleRepository roleRepository;
  private final UsuarioRepository usuarioRepository;

  @Override
  @Transactional
  public void run(String... args) {
    removerRolesDescontinuadas();
    for (RoleNivel roleNivel : RoleNivel.values()) {
      if (roleRepository.findByNome(roleNivel.valorBanco()).isEmpty()) {
        Role role = new Role();
        role.setNome(roleNivel.valorBanco());
        roleRepository.save(role);
      }
    }
  }

  private void removerRolesDescontinuadas() {
    for (String roleNome : ROLES_REMOVIDAS) {
      roleRepository
          .findByNome(roleNome)
          .ifPresent(
              role -> {
                for (Usuario usuario : usuarioRepository.findAll()) {
                  usuario
                      .getRoles()
                      .removeIf(usuarioRole -> roleNome.equals(usuarioRole.getNome()));
                }
                role.getPermissoes().clear();
                roleRepository.save(role);
                roleRepository.delete(role);
              });
    }
  }
}
