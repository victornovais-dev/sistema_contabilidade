package com.sistema_contabilidade.database.routing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DatabaseRoutingContext unit tests")
class DatabaseRoutingContextTest {

  @AfterEach
  void clearContext() {
    DatabaseRoutingContext.clear();
  }

  @Test
  @DisplayName("Deve usar writer por default")
  void deveUsarWriterPorDefault() {
    assertFalse(DatabaseRoutingContext.isReaderAllowed());
    assertFalse(DatabaseRoutingContext.isStickyWriter());
  }

  @Test
  @DisplayName("Deve identificar writer forçado por sticky")
  void deveIdentificarWriterForcadoPorSticky() {
    DatabaseRoutingContext.forceWriterForSticky();

    assertFalse(DatabaseRoutingContext.isReaderAllowed());
    assertTrue(DatabaseRoutingContext.isStickyWriter());
  }

  @Test
  @DisplayName("Nao deve propagar permissao de reader para outra thread")
  void naoDevePropagarPermissaoDeReaderParaOutraThread() throws InterruptedException {
    DatabaseRoutingContext.allowReader();
    AtomicBoolean readerAllowedInOtherThread = new AtomicBoolean(true);

    Thread thread =
        Thread.ofVirtual()
            .start(() -> readerAllowedInOtherThread.set(DatabaseRoutingContext.isReaderAllowed()));
    thread.join();

    assertFalse(readerAllowedInOtherThread.get());
    assertTrue(DatabaseRoutingContext.isReaderAllowed());
    assertFalse(DatabaseRoutingContext.isStickyWriter());
  }
}
