package com.augustnagro.vaa;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.future.FutureInternal;
import io.vertx.core.impl.future.Listener;

class Coroutine extends Continuation {
  final ContinuationScope scope;
  final Context ctx;

  private volatile Object channel = null;

  Coroutine(Context ctx, ContinuationScope scope, Runnable prog) {
    super(scope, prog);
    this.ctx = ctx;
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

    ctx.runOnContext(v -> {
      future.onComplete((AsyncResult<A> ar) -> {
        if (ar.succeeded()) {
          resume(ar.result());
        } else {
          resume(ar.cause());
        }
      });
    });


    Continuation.yield(scope);

    Object chan = channel;
    if (chan instanceof Throwable t) {
      throwAsUnchecked(t);
      throw new UnsupportedOperationException("Unreachable");
    } else {
      return (A) chan;
    }
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
