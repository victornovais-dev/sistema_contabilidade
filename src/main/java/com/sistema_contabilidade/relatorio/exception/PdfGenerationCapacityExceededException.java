package com.sistema_contabilidade.relatorio.exception;

import java.io.Serial;

public final class PdfGenerationCapacityExceededException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final long retryAfterSeconds;

  public PdfGenerationCapacityExceededException(long retryAfterSeconds) {
    super("Capacidade de geracao de PDF esgotada");
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
