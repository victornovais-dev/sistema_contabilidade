package com.sistema_contabilidade.database.routing;

public final class DatabaseRoutingContext {

  private static final ThreadLocal<RoutingState> STATE = new ThreadLocal<>();

  private DatabaseRoutingContext() {}

  public static void allowReader() {
    STATE.set(new RoutingState(DatabaseRoute.READER, false));
  }

  public static void forceWriter() {
    STATE.set(new RoutingState(DatabaseRoute.WRITER, false));
  }

  public static void forceWriterForSticky() {
    STATE.set(new RoutingState(DatabaseRoute.WRITER, true));
  }

  public static boolean isReaderAllowed() {
    RoutingState state = STATE.get();
    return state != null && state.route() == DatabaseRoute.READER;
  }

  public static boolean isStickyWriter() {
    RoutingState state = STATE.get();
    return state != null && state.stickyWriter();
  }

  public static void clear() {
    STATE.remove();
  }

  private record RoutingState(DatabaseRoute route, boolean stickyWriter) {}
}
