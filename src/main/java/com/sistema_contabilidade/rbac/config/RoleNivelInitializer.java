package com.sistema_contabilidade.rbac.config;

import com.sistema_contabilidade.rbac.model.Role;
import com.sistema_contabilidade.rbac.model.RoleNivel;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoleNivelInitializer implements CommandLineRunner {

  private final RoleRepository roleRepository;

  @Override
  public void run(String... args) {
    for (RoleNivel roleNivel : RoleNivel.values()) {
      if (roleRepository.findByNome(roleNivel.valorBanco()).isEmpty()) {
        Role role = new Role();
        role.setNome(roleNivel.valorBanco());
        roleRepository.save(role);
      }
    }
  }
}
