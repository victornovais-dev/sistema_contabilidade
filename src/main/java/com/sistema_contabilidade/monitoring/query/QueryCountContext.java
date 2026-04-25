package com.sistema_contabilidade.monitoring.query;

import java.util.concurrent.atomic.AtomicInteger;

public final class QueryCountContext {

  private static final ThreadLocal<AtomicInteger> COUNTER = new ThreadLocal<>();

  private QueryCountContext() {}

  public static void start() {
    COUNTER.set(new AtomicInteger());
  }

  public static int increment() {
    AtomicInteger counter = COUNTER.get();
    if (counter == null) {
      return 0;
    }
    return counter.incrementAndGet();
  }

  public static int get() {
    AtomicInteger counter = COUNTER.get();
    return counter == null ? 0 : counter.get();
  }

  public static void clear() {
    COUNTER.remove();
  }
}
