package com.augustnagro.vaa;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;

class Coroutine extends Continuation {
  private final ContinuationScope scope;

  private Object channel = null;

  Coroutine(ContinuationScope scope, Runnable prog) {
    super(scope, prog);
    this.scope = scope;
  }

  /**
   * Suspends this Coroutine by yielding its Continuation.
   * A handler is added to future, so that when the Future completes,
   * resume() is called, at which point this method returns and the program
   * resumes.
   */
  @SuppressWarnings("unchecked")
  <A> A suspend(Future<A> future) {
    if (future.isComplete()) {
      if (future.succeeded()) {
        return future.result();
      } else {
        throwAsUnchecked(future.cause());
      }
    }

    future.onComplete((AsyncResult<A> ar) -> {
      if (ar.succeeded()) {
        resume(ar.result());
      } else {
        throwAsUnchecked(ar.cause());
      }
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

  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void throwAsUnchecked(Throwable t) throws E {
    throw (E) t;
  }

}
