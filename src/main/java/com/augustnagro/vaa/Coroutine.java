package com.augustnagro.vaa;

import io.vertx.core.Context;
import io.vertx.core.Future;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * await() must be run on a Virtual Thread. Whenever we block, the Virtual Thread will
 * yield execution. So await() blocks until the Future.onComplete handler unblocks us.
 */
class Coroutine {

  private final ReentrantLock lock = new ReentrantLock();
  private final Condition cond = lock.newCondition();
  private final Context vertxContext;

  Coroutine(Context vertxContext) {
    this.vertxContext = vertxContext;
  }

  <A> A await(Future<A> future) {
    lock.lock();
    try {

      future.onComplete(ar -> {
        // Future.onComplete can execute immediately,
        // which would cause deadlock if we don't run it asynchronously.
        vertxContext.runOnContext(voidd -> {
          lock.lock();
          try {
            cond.signal();
          } finally {
            lock.unlock();
          }
        });
      });

      cond.await();

      if (future.succeeded()) {
        return future.result();
      } else {
        throw new RuntimeException(future.cause());
      }

    } catch (InterruptedException e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    } finally {
      lock.unlock();
    }

  }
}
