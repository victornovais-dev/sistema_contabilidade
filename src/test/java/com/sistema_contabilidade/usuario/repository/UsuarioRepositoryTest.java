package com.sistema_contabilidade.usuario.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sistema_contabilidade.database.crypto.DatabaseDataProtectionBackfill;
import com.sistema_contabilidade.database.crypto.service.BlindIndexService;
import com.sistema_contabilidade.database.crypto.service.DatabaseCryptoService;
import com.sistema_contabilidade.usuario.model.Usuario;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({DatabaseCryptoService.class, BlindIndexService.class})
@DisplayName("UsuarioRepository DataJpa tests")
class UsuarioRepositoryTest {

  @Autowired private UsuarioRepository usuarioRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private DatabaseCryptoService cryptoService;
  @Autowired private BlindIndexService blindIndexService;

  @Test
  @DisplayName("Deve salvar e buscar usuario por email")
  void deveSalvarEBuscarPorEmail() {
    Usuario usuario = new Usuario();
    usuario.setNome("Ana");
    usuario.setEmail("ana@email.com");
    usuario.setSenha("senha-criptografada");
    usuarioRepository.saveAndFlush(usuario);

    Optional<Usuario> encontrado = usuarioRepository.findByEmail(" ANA@EMAIL.COM ");

    assertTrue(encontrado.isPresent());
    assertTrue(usuarioRepository.existsByEmail("ana@email.com"));

    Map<String, Object> raw =
        jdbcTemplate.queryForMap(
            "select email, nome, senha, email_bidx from usuarios where id = ?", usuario.getId());
    assertNotEquals("ana@email.com", raw.get("email"));
    assertNotEquals("Ana", raw.get("nome"));
    assertNotEquals("senha-criptografada", raw.get("senha"));
    assertTrue(raw.get("email").toString().startsWith("enc:v1:"));
    assertEquals(64, raw.get("email_bidx").toString().length());
  }

  @Test
  @DisplayName("Deve preservar registro fisico ao excluir usuario")
  void deveAplicarSoftDelete() {
    Usuario usuario = novoUsuario("soft-delete@email.com");
    usuarioRepository.saveAndFlush(usuario);

    usuarioRepository.delete(usuario);
    usuarioRepository.flush();

    assertFalse(usuarioRepository.findById(usuario.getId()).isPresent());
    Object deletedAt =
        jdbcTemplate.queryForObject(
            "select deleted_at from usuarios where id = ?", Object.class, usuario.getId());
    assertNotNull(deletedAt);
  }

  @Test
  @DisplayName("Deve proteger linhas legadas e preencher blind index no backfill")
  void deveProtegerLinhaLegadaNoBackfill() throws Exception {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        """
        insert into usuarios (id, email, nome, senha, version, troca_senha_obrigatoria)
        values (?, ?, ?, ?, ?, ?)
        """,
        id,
        "legado@email.com",
        "Legado",
        "hash-legado",
        0L,
        false);
    DatabaseDataProtectionBackfill backfill =
        new DatabaseDataProtectionBackfill(jdbcTemplate, cryptoService, blindIndexService);

    backfill.run(new DefaultApplicationArguments());

    Map<String, Object> raw =
        jdbcTemplate.queryForMap(
            "select email, nome, senha, email_bidx from usuarios where id = ?", id);
    assertTrue(raw.get("email").toString().startsWith("enc:v1:"));
    assertTrue(raw.get("nome").toString().startsWith("enc:v1:"));
    assertTrue(raw.get("senha").toString().startsWith("enc:v1:"));
    assertEquals(blindIndexService.email("legado@email.com"), raw.get("email_bidx"));
  }

  private Usuario novoUsuario(String email) {
    Usuario usuario = new Usuario();
    usuario.setNome("Usuario");
    usuario.setEmail(email);
    usuario.setSenha("hash");
    return usuario;
  }
}
