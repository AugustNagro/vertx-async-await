package com.augustnagro.vaa;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

import java.util.Objects;
import java.util.concurrent.*;

public class Async {

  // todo refactor to use ScopeLocal when it's no longer a draft JEP.
  // https://openjdk.java.net/jeps/8263012
  private static final ThreadLocal<AsyncContext> ASYNC_CONTEXT = new ThreadLocal<>();
  private static final ThreadLocal<Coroutine> AWAIT_CONTEXT = new ThreadLocal<>();

  private record AsyncContext(Context vertxContext, ThreadFactory vThreadFactory) {}

  public static <A> Future<A> async(Callable<A> fn) {
    AsyncContext asyncContext = ASYNC_CONTEXT.get();

    if (asyncContext == null) {
      Context vertxContext = Objects.requireNonNull(
        Vertx.currentContext(),
        "This thread needs a Vertx Context to use async/await"
      );

      // Executor that executes the partner Virtual Thread on this Vertx Context.
      // todo; cast Context to ContextInternal, and use one of dispatch/emit/execute?
      //https://vert-x3.github.io/advanced-vertx-guide/index.html#_firing-events
      Executor contextThreadExecutor = command -> {
        vertxContext.runOnContext(v -> command.run());
      };
      ThreadFactory vtFactory = Thread.ofVirtual()
        .scheduler(contextThreadExecutor)
        .factory();

      asyncContext = new AsyncContext(vertxContext, vtFactory);
      ASYNC_CONTEXT.set(asyncContext);
    }
    AsyncContext finalAsyncCtx = asyncContext;

    Promise<A> promise = Promise.promise();

    finalAsyncCtx.vThreadFactory.newThread(() -> {
      try {
        ASYNC_CONTEXT.set(finalAsyncCtx);
        AWAIT_CONTEXT.set(new Coroutine(finalAsyncCtx.vertxContext));
        promise.complete(fn.call());
        AWAIT_CONTEXT.remove();
      } catch (Exception e) {
        promise.fail(e);
      }
    }).start();

    return promise.future();
  }

  public static <A> A await(Future<A> future) {
    Coroutine coroutine = Objects.requireNonNull(
      AWAIT_CONTEXT.get(),
      "Must call await from inside an async scope"
    );

    return coroutine.await(future);
  }


}
