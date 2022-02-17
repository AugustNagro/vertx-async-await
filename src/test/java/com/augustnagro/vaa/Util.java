package com.augustnagro.vaa;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.augustnagro.vaa.AsyncAwait.async;

public class Util {

  public static void asyncTest(AsyncTestBody testBody) {
    Vertx vertx = Vertx.vertx()
      .exceptionHandler(Throwable::printStackTrace);

    Promise<Void> promise = Promise.promise();
    vertx.runOnContext(v -> {
      async(() -> {
        testBody.apply(vertx);
        return null;
      })
        .onSuccess(x -> promise.complete())
        .onFailure(promise::fail);
    });

    try {
      promise.future().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
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
