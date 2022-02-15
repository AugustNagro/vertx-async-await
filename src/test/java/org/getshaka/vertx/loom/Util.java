package org.getshaka.vertx.loom;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.getshaka.vertx.loom.AsyncAwait.async;

public class Util {

  public static void asyncTest(AsyncTestBody testBody) {
    Vertx vertx = Vertx.vertx()
        .exceptionHandler(Throwable::printStackTrace);

    Future<Void> future = async(() -> {
      testBody.apply(vertx);
      return null;
    });

    try {
      future.toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      throwAsUnchecked(e.getCause());
    } catch (Throwable t) {
      throwAsUnchecked(t);
    } finally {
      vertx.close();
    }
  }

  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void throwAsUnchecked(Throwable t) throws E {
    throw (E) t;
  }
}
