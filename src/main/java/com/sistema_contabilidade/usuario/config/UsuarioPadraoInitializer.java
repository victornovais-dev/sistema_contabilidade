package com.sistema_contabilidade.usuario.config;

import com.sistema_contabilidade.rbac.model.RoleNivel;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Order(20)
@Slf4j
@RequiredArgsConstructor
public class UsuarioPadraoInitializer implements CommandLineRunner {

  private static final String EMAIL_PADRAO = "victornovais77@gmail.com";
  private static final String SENHA_PADRAO = "123";
  private static final String NOME_PADRAO = "Victor Novais";

  private final UsuarioRepository usuarioRepository;
  private final RoleRepository roleRepository;
  private final PasswordEncoder passwordEncoder;

  @Override
  @Transactional
  public void run(String... args) {
    if (usuarioRepository.count() > 0) {
      return;
    }

    Usuario usuario = new Usuario();
    usuario.setNome(NOME_PADRAO);
    usuario.setEmail(EMAIL_PADRAO);
    usuario.setSenha(passwordEncoder.encode(SENHA_PADRAO));
    roleRepository
        .findByNome(RoleNivel.ADMIN.valorBanco())
        .ifPresent(role -> usuario.getRoles().add(role));
    usuarioRepository.save(usuario);

    if (log.isInfoEnabled()) {
      log.info("Usuario padrao criado para ambiente vazio | email: {}", EMAIL_PADRAO);
    }
  }
}
