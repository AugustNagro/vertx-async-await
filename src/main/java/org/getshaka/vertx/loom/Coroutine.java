package org.getshaka.vertx.loom;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;

class Coroutine extends Continuation {
  private final Context vertxContext;
  private final ContinuationScope scope;

  // This is either a Future, or the value of that future.
  // It will always be set & read by the one thread that the
  // Vertx Context is associated with.
  private Object channel = null;

  Coroutine(Context vertxContext, ContinuationScope scope, Runnable program) {
    super(scope, program);
    this.vertxContext = vertxContext;
    this.scope = scope;
  }

  /**
   * Suspends this Coroutine by yielding its Continuation.
   * A handler is added to future, so that when the Future completes,
   * resume() is called, at which point this method returns and the program
   * resumes.
   *
   * Must only ever be called on the VertxContext thread.
   */
  @SuppressWarnings("unchecked")
  <A> A suspend(Future<A> future) {
    channel = future;

    future.onComplete((AsyncResult<A> ar) -> {
      // the Future could be running on a different context, like
      // ForkJoin, or a Postgress Thread Pool. So, we need to make sure
      // resume() is called on the right thread & order via runOnContext.
      vertxContext.runOnContext(voidd -> {
        if (ar.succeeded()) {
          resume(ar.result());
        } else {
          throw new RuntimeException(ar.cause());
        }
      });
    });

    Continuation.yield(scope);
    // When the future completes, it will call resume(). resume() will set
    // channel equal to the Future's resolved value, and continue the continuation,
    // executing the line below..
    return (A) channel;
  }

  /**
   * Resumes this Coroutine, setting its channel.
   */
  void resume(Object v) {
    channel = v;
    run();
  }

}
