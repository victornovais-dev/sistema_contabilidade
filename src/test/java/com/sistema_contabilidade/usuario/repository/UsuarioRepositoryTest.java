package com.sistema_contabilidade.usuario.repository;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sistema_contabilidade.usuario.model.Usuario;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@DisplayName("UsuarioRepository DataJpa tests")
class UsuarioRepositoryTest {

  @Autowired private UsuarioRepository usuarioRepository;

  @Test
  @DisplayName("Deve salvar e buscar usuario por email")
  void deveSalvarEBuscarPorEmail() {
    Usuario usuario = new Usuario();
    usuario.setNome("Ana");
    usuario.setEmail("ana@email.com");
    usuario.setSenha("senha-criptografada");
    usuarioRepository.save(usuario);

    Optional<Usuario> encontrado = usuarioRepository.findByEmail("ana@email.com");

    assertTrue(encontrado.isPresent());
    assertTrue(usuarioRepository.existsByEmail("ana@email.com"));
  }
}
