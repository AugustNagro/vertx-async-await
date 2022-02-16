package com.augustnagro.vaa;

import io.vertx.core.Vertx;

public interface AsyncTestBody {
  void apply(Vertx vertx) throws Exception;
}
