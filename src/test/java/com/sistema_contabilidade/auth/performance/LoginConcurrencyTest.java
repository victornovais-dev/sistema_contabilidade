package com.sistema_contabilidade.auth.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sistema_contabilidade.rbac.model.Role;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Login concurrency tests")
class LoginConcurrencyTest {

  private static final int TOTAL_LOGINS = 50;
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Autowired private MockMvc mockMvc;
  @Autowired private UsuarioRepository usuarioRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @Test
  @DisplayName("Deve medir tempo para 50 logins simultaneos")
  void deveMedirTempoPara50LoginsSimultaneos() throws Exception {
    String email = "loadtest." + UUID.randomUUID() + "@email.com";
    String senha = "123456";
    criarUsuarioDeTeste(email, senha);

    ExecutorService executor = Executors.newFixedThreadPool(TOTAL_LOGINS);
    CountDownLatch inicioSimultaneo = new CountDownLatch(1);
    List<Callable<Long>> tarefas = new ArrayList<>();

    for (int i = 0; i < TOTAL_LOGINS; i++) {
      tarefas.add(
          () -> {
            inicioSimultaneo.await();
            long inicio = System.nanoTime();

            MvcResult csrfResult =
                mockMvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
            JsonNode payload =
                OBJECT_MAPPER.readTree(csrfResult.getResponse().getContentAsString());
            String csrfToken = payload.required("token").asString();
            Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");

            mockMvc
                .perform(
                    post("/api/v1/auth/login")
                        .cookie(csrfCookie)
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            OBJECT_MAPPER.writeValueAsString(
                                java.util.Map.of("email", email, "senha", senha))))
                .andExpect(status().isOk());

            return (System.nanoTime() - inicio) / 1_000_000;
          });
    }

    long inicioTotal = System.nanoTime();
    List<Future<Long>> futures = tarefas.stream().map(executor::submit).toList();
    inicioSimultaneo.countDown();

    List<Long> temposMs = new ArrayList<>();
    for (Future<Long> future : futures) {
      temposMs.add(obterResultado(future));
    }
    long tempoTotalMs = (System.nanoTime() - inicioTotal) / 1_000_000;
    executor.shutdown();

    long minimo = temposMs.stream().mapToLong(Long::longValue).min().orElse(0L);
    long maximo = temposMs.stream().mapToLong(Long::longValue).max().orElse(0L);
    long medio = Math.round(temposMs.stream().mapToLong(Long::longValue).average().orElse(0D));

    System.out.printf(
        "%n[LOGIN LOAD TEST] concorrencia=%d, totalMs=%d, minMs=%d, medioMs=%d, maxMs=%d%n",
        TOTAL_LOGINS, tempoTotalMs, minimo, medio, maximo);

    assertEquals(TOTAL_LOGINS, temposMs.size());
  }

  private Long obterResultado(Future<Long> future) throws Exception {
    try {
      return future.get();
    } catch (ExecutionException executionException) {
      Throwable cause = executionException.getCause();
      if (cause instanceof Exception causeAsException) {
        throw causeAsException;
      }
      throw new RuntimeException("Falha durante execucao concorrente de login", cause);
    }
  }

  private void criarUsuarioDeTeste(String email, String senha) {
    Role adminRole =
        roleRepository
            .findByNome("ADMIN")
            .orElseGet(
                () -> {
                  Role role = new Role();
                  role.setNome("ADMIN");
                  return roleRepository.save(role);
                });

    Usuario usuario = new Usuario();
    usuario.setNome("Load Test User");
    usuario.setEmail(email);
    usuario.setSenha(passwordEncoder.encode(senha));
    usuario.getRoles().add(adminRole);
    usuarioRepository.save(usuario);
  }
}
