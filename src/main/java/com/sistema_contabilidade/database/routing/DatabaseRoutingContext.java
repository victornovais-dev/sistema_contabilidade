package com.sistema_contabilidade.database.routing;

public final class DatabaseRoutingContext {

  private static final ThreadLocal<DatabaseRoute> ROUTE = new ThreadLocal<>();

  private DatabaseRoutingContext() {}

  public static void allowReader() {
    ROUTE.set(DatabaseRoute.READER);
  }

  public static void forceWriter() {
    ROUTE.set(DatabaseRoute.WRITER);
  }

  public static boolean isReaderAllowed() {
    return ROUTE.get() == DatabaseRoute.READER;
  }

  public static void clear() {
    ROUTE.remove();
  }
}
