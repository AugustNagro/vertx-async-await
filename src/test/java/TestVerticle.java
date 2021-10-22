import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.concurrent.CompletableFuture;

import static com.augustnagro.vaa.Async.async;
import static com.augustnagro.vaa.Async.await;

public class TestVerticle extends AbstractVerticle {

  public static void main(String[] args) {
    Vertx.vertx()
      .exceptionHandler(Throwable::printStackTrace)
      .deployVerticle(new TestVerticle());
  }

  @Override
  public void start() throws Exception {
    Router router = Router.router(vertx);
    router.route().respond(this::doSomething);

    vertx.createHttpServer()
      .requestHandler(router)
      .listen(8088)
      .onSuccess(server -> System.out.println("deployed server"));
  }

  private Future<String> doSomething(RoutingContext ctx) {
    return async(() -> {
      Integer a = await(a());
      System.out.println("done a");
      Integer b = await(b());
      System.out.println("done b");
      Integer a1 = await(a());
      System.out.println("done a1");

      Integer c = await(async(() -> {
        Integer x = await(a());
        return x;
      }));

      return "The num is: " + a + b + a1 + c;
    }).onFailure(Throwable::printStackTrace);
  }

  private Future<Integer> a() {
    return Future.fromCompletionStage(CompletableFuture.supplyAsync(() -> {
      try {
        Thread.sleep(1000);
        return 1;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }));
  }

  private Future<Integer> b() {
    return Future.fromCompletionStage(CompletableFuture.supplyAsync(() -> 2));
  }

  private Future<Integer> c() {
    return Future.fromCompletionStage(CompletableFuture.supplyAsync(() -> {
      throw new RuntimeException("hello world exception");
    }));
  }

}
