package org.getshaka.vertx.loom;

import io.vertx.core.Vertx;

public interface AsyncTestBody {
  void apply(Vertx vertx) throws Exception;
}
