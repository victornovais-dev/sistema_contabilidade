package com.sistema_contabilidade.usuario.config;

import com.sistema_contabilidade.rbac.model.RoleNivel;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Order(20)
@Slf4j
public class UsuarioPadraoInitializer implements CommandLineRunner {

  private final UsuarioRepository usuarioRepository;
  private final RoleRepository roleRepository;
  private final PasswordEncoder passwordEncoder;
  private final String nomePadrao;
  private final String emailPadrao;
  private final String senhaPadrao;

  public UsuarioPadraoInitializer(
      UsuarioRepository usuarioRepository,
      RoleRepository roleRepository,
      PasswordEncoder passwordEncoder,
      @Value("${app.default-admin.name:Administrador}") String nomePadrao,
      @Value("${app.default-admin.email:admin@sistema.local}") String emailPadrao,
      @Value("${app.default-admin.password:}") String senhaPadrao) {
    this.usuarioRepository = usuarioRepository;
    this.roleRepository = roleRepository;
    this.passwordEncoder = passwordEncoder;
    this.nomePadrao = nomePadrao;
    this.emailPadrao = emailPadrao;
    this.senhaPadrao = senhaPadrao;
  }

  @Override
  @Transactional
  public void run(String... args) {
    if (usuarioRepository.count() > 0) {
      return;
    }
    if (senhaPadrao == null || senhaPadrao.isBlank()) {
      log.warn(
          "Usuario padrao nao criado: defina app.default-admin.password para ambientes vazios.");
      return;
    }

    Usuario usuario = new Usuario();
    usuario.setNome(nomePadrao);
    usuario.setEmail(emailPadrao);
    usuario.setSenha(passwordEncoder.encode(senhaPadrao));
    roleRepository
        .findByNome(RoleNivel.ADMIN.valorBanco())
        .ifPresent(role -> usuario.getRoles().add(role));
    usuarioRepository.save(usuario);

    if (log.isInfoEnabled()) {
      log.info("Usuario padrao criado para ambiente vazio | email: {}", emailPadrao);
    }
  }
}
