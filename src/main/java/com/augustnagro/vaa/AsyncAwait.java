package com.augustnagro.vaa;

import io.vertx.core.Future;
import io.vertx.core.Promise;

import java.util.Objects;
import java.util.concurrent.Callable;

public class AsyncAwait {
  private static final ContinuationScope ASYNC_SCOPE =
      new ContinuationScope("vertx-async-await-scope");

  /**
   * Execute some code with a Coroutine. Within the Callable, you can
   * {@link } on Futures to program in an imperative style.
   * Stack traces are also significantly improved.
   * <p>
   * No new threads (virtual or otherwise) are created.
   */
  public static <A> Future<A> async(Callable<A> prog) {
    Promise<A> promise = Promise.promise();
    Coroutine coroutine = new Coroutine(
        ASYNC_SCOPE,
        () -> {
          try {
            promise.complete(prog.call());
          } catch (Throwable t) {
            promise.fail(t);
          }
        }
    );
    coroutine.run();
    return promise.future();
  }

  /**
   * Awaits this Future by suspending the current Coroutine.
   */
  public static <A> A await(Future<A> f) {
    Coroutine coroutine = (Coroutine) Objects.requireNonNull(
        Continuation.getCurrentContinuation(ASYNC_SCOPE),
        "await must be called inside of an async scope"
    );
    return coroutine.suspend(f);
  }


}
