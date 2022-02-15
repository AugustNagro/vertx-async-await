package org.getshaka.vertx.loom;

import io.vertx.core.Future;
import io.vertx.core.http.*;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.getshaka.vertx.loom.AsyncAwait.async;
import static org.getshaka.vertx.loom.AsyncAwait.await;
import static org.getshaka.vertx.loom.Util.asyncTest;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SequencingBugTests {

  private static final int PORT = 8888;
  private static final String TEST_ENDPOINT = "/test";
  private static final String REQ_BODY = "req body";
  private static final String RESP_BODY = "resp body";
  private static final int REQ_ITERATIONS = 100;

  private static Future<String> respBodyFuture() {
    return Future.succeededFuture(RESP_BODY);
  }

  @Test
  public void testClientServer() {
    asyncTest(vertx -> {

      HttpServer server = vertx.createHttpServer();
      server.requestHandler(req -> async(() -> {
        String reqBody = await(req.body()).toString();
        assertEquals(REQ_BODY, reqBody);
        String resp = await(respBodyFuture());
        req.response().end(resp);
        return null;
      }));
      await(server.listen(PORT, "localhost"));

      HttpClient client = vertx.createHttpClient();
      for (int i = 0; i < REQ_ITERATIONS; ++i) {
        HttpClientRequest req =
            await(client.request(HttpMethod.GET, PORT, "localhost", TEST_ENDPOINT));
        HttpClientResponse resp = await(req.send(REQ_BODY));
        String bodyString = await(resp.body()).toString();
        assertEquals(RESP_BODY, bodyString);
      }

    });
  }

  @Test
  public void testClientServerWithRouter() {
    asyncTest(vertx -> {

      Router router = Router.router(vertx);

      router.route().handler(BodyHandler.create());

      router.get(TEST_ENDPOINT).handler(ctx -> async(() -> {
        assertEquals(REQ_BODY, ctx.getBodyAsString());
        String resp = await(respBodyFuture());
        ctx.response().end(resp);
        return null;
      }));

      HttpServer server = vertx.createHttpServer();
      server.requestHandler(router);
      await(server.listen(PORT, "localhost"));

      HttpClient client = vertx.createHttpClient();
      for (int i = 0; i < REQ_ITERATIONS; ++i) {
        HttpClientRequest req =
            await(client.request(HttpMethod.GET, PORT, "localhost", TEST_ENDPOINT));
        HttpClientResponse resp = await(req.send(REQ_BODY));
        String bodyString = await(resp.body()).toString();
        assertEquals(RESP_BODY, bodyString);
      }
    });
  }

  @Test
  public void testContextSwitchIssue() {
    asyncTest(vertx -> {

      CountDownLatch latch = new CountDownLatch(1);
      vertx.getOrCreateContext().runOnContext(__ -> {
        latch.countDown();
      });

      boolean finished = latch.await(3, TimeUnit.SECONDS);
      assertTrue(finished);
    });
  }

  @Test
  public void testArnavarrBug() {
    asyncTest(vertx -> {

      ReentrantLock rl = new ReentrantLock();
      rl.lock();

      vertx.getOrCreateContext().runOnContext(__ -> {
        System.out.println("try to lock");
        rl.lock();
        System.out.println("I locked it");
      });

      System.out.println("before sleep");
      Thread.sleep(100);
      System.out.println("after sleep");

      rl.unlock();
    });
  }
}
