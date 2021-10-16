package org.getshaka.vertx.loom;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

import java.util.Objects;
import java.util.concurrent.Callable;

public class VertxLoom {
  private static final ContinuationScope VERTX_LOOM_SCOPE =
    new ContinuationScope("vertx-loom-scope");

  /**
   * Like {@link #async(Context, Callable)}, but using this thread's
   * Vertx context.
   */
  public static <A> Future<A> async(Callable<A> program) {
    Context context = Objects.requireNonNull(
      Vertx.currentContext(),
      "This thread needs a Vertx Context to use async/await"
    );
    return async(context, program);
  }

  /**
   * Execute some code with a Coroutine. Within the Callable, you can
   * {@link #await(Future) await} Futures and program in an imperative style.
   * Stack traces are also significantly improved.
   *
   * Must be called on a thread with a Vertx Context.
   * No new threads (virtual or otherwise) are created.
   */
  public static <A> Future<A> async(Context context, Callable<A> program) {
    Promise<A> promise = Promise.promise();
    Coroutine coroutine = new Coroutine(context, VERTX_LOOM_SCOPE, () -> {
      try {
        promise.complete(program.call());
      } catch (Exception e) {
        promise.fail(e);
      }
    });
    context.runOnContext(voidd -> coroutine.run());
    return promise.future();
  }

  /**
   * Awaits this Future by suspending the current Coroutine.
   */
  public static <A> A await(Future<A> f) {
    Coroutine coroutine = (Coroutine) Objects.requireNonNull(
      Continuation.getCurrentContinuation(VERTX_LOOM_SCOPE),
      "await must be called inside of an async scope."
    );
    return coroutine.suspend(f);
  }

}
