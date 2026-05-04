package com.sistema_contabilidade.item.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.rbac.model.Role;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@DisplayName("ItemRepository race condition tests")
class ItemRepositoryConcurrencyTest {

  @Autowired private ItemRepository itemRepository;
  @Autowired private UsuarioRepository usuarioRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private PlatformTransactionManager transactionManager;

  @AfterEach
  void limparDados() {
    itemRepository.deleteAll();
    usuarioRepository.deleteAll();
    roleRepository.deleteAll();
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("Deve detectar lost update em atualizacao concorrente do mesmo item")
  void deveDetectarLostUpdateEmAtualizacaoConcorrenteDoMesmoItem() throws Exception {
    Usuario criador = criarUsuarioComRole("concorrencia@email.com", "FINANCEIRO");
    Item item = itemRepository.save(novoItem(criador));
    TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
    CountDownLatch carregados = new CountDownLatch(2);
    CountDownLatch inicio = new CountDownLatch(1);
    CountDownLatch fim = new CountDownLatch(2);
    AtomicInteger sucessos = new AtomicInteger();
    AtomicInteger conflitos = new AtomicInteger();
    List<Throwable> erros = java.util.Collections.synchronizedList(new ArrayList<>());
    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      Future<?> primeiraAtualizacao =
          executor.submit(
              () ->
                  executarAtualizacaoConcorrente(
                      txTemplate,
                      item.getId(),
                      "observacao-a",
                      carregados,
                      inicio,
                      fim,
                      sucessos,
                      conflitos,
                      erros));
      Future<?> segundaAtualizacao =
          executor.submit(
              () ->
                  executarAtualizacaoConcorrente(
                      txTemplate,
                      item.getId(),
                      "observacao-b",
                      carregados,
                      inicio,
                      fim,
                      sucessos,
                      conflitos,
                      erros));

      assertTrue(carregados.await(5, TimeUnit.SECONDS));
      inicio.countDown();
      assertTrue(fim.await(5, TimeUnit.SECONDS));
      primeiraAtualizacao.get();
      segundaAtualizacao.get();
    } finally {
      executor.shutdown();
      assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    if (!erros.isEmpty()) {
      throw new AssertionError(
          "Nao esperava erro inesperado em teste concorrente", erros.getFirst());
    }

    Item atualizado = itemRepository.findById(item.getId()).orElseThrow();
    assertEquals(1, sucessos.get());
    assertEquals(1, conflitos.get());
    assertEquals(1L, atualizado.getVersion());
    assertTrue(
        "observacao-a".equals(atualizado.getObservacao())
            || "observacao-b".equals(atualizado.getObservacao()));
  }

  private void executarAtualizacaoConcorrente(
      TransactionTemplate txTemplate,
      java.util.UUID itemId,
      String observacao,
      CountDownLatch carregados,
      CountDownLatch inicio,
      CountDownLatch fim,
      AtomicInteger sucessos,
      AtomicInteger conflitos,
      List<Throwable> erros) {
    try {
      txTemplate.executeWithoutResult(
          status -> {
            Item carregado = itemRepository.findById(itemId).orElseThrow();
            carregados.countDown();
            aguardar(inicio);
            carregado.setObservacao(observacao);
            itemRepository.saveAndFlush(carregado);
            sucessos.incrementAndGet();
          });
    } catch (RuntimeException exception) {
      if (isConflitoOtimista(exception)) {
        conflitos.incrementAndGet();
      } else {
        erros.add(exception);
      }
    } finally {
      fim.countDown();
    }
  }

  private boolean isConflitoOtimista(Throwable throwable) {
    Throwable atual = throwable;
    while (atual != null) {
      if (atual instanceof ObjectOptimisticLockingFailureException
          || atual instanceof UnexpectedRollbackException) {
        return true;
      }
      atual = atual.getCause();
    }
    return false;
  }

  private void aguardar(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError("Thread interrompida durante teste concorrente", exception);
    }
  }

  private Usuario criarUsuarioComRole(String email, String roleNome) {
    Usuario usuario = new Usuario();
    usuario.setNome(email);
    usuario.setEmail(email);
    usuario.setSenha("123456");
    Role role =
        roleRepository
            .findByNome(roleNome)
            .orElseGet(() -> roleRepository.save(novaRole(roleNome)));
    usuario.getRoles().add(role);
    return usuarioRepository.save(usuario);
  }

  private Role novaRole(String nome) {
    Role role = new Role();
    role.setNome(nome);
    return role;
  }

  private Item novoItem(Usuario criadoPor) {
    Item item = new Item();
    item.setValor(new BigDecimal("50.00"));
    item.setData(LocalDate.of(2026, 4, 28));
    item.setHorarioCriacao(LocalDateTime.of(2026, 4, 28, 10, 30, 0));
    item.setCaminhoArquivoPdf("uploads/itens/concorrencia.pdf");
    item.setTipo(TipoItem.RECEITA);
    item.setDescricao("SERVICOS");
    item.setRazaoSocialNome("EMPRESA TESTE");
    item.setCnpjCpf("12.345.678/0001-99");
    item.setObservacao("inicial");
    item.setRoleNome("FINANCEIRO");
    item.setCriadoPor(criadoPor);
    return item;
  }
}
