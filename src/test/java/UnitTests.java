import io.vertx.core.Future;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import static com.augustnagro.vaa.Async.async;
import static com.augustnagro.vaa.Async.await;

@RunWith(VertxUnitRunner.class)
public class UnitTests {

  @Rule
  public RunTestOnContext rule = new RunTestOnContext();

  Future<List<Long>> userIdsFromDb() {
    return Future.succeededFuture(List.of(1L, 2L, 3L));
  }

  Future<String> userNameFromSomeApi(Long userId) {
    return Future.succeededFuture("User " + userId);
  }

  Future<byte[]> buildPdf(List<String> userNames) {
    return Future.succeededFuture(new byte[0]);
  }

  @Test
  public void testSimpleAsyncAwait(TestContext ctx) {
    Future<byte[]> future = async(() -> {
      List<Long> userIds = await(userIdsFromDb());

      List<String> userNames = userIds.stream()
        .map(id -> await(userNameFromSomeApi(id)))
        .toList();

      byte[] pdf = await(buildPdf(userNames));
      System.out.println("Generated pdf for user ids: " + userIds);
      return pdf;
    });

    future.onComplete(ctx.asyncAssertSuccess());
  }

  @Test
  public void testSimpleFlatMap(TestContext ctx) {
    Future<byte[]> future = userIdsFromDb().flatMap(userIds -> {

      Future<List<String>> userNamesFuture =
        Future.succeededFuture(new ArrayList<>());

      for (Long userId : userIds) {
        userNamesFuture = userNamesFuture.flatMap(list -> {
          return userNameFromSomeApi(userId)
            .map(userName -> {
              list.add(userName);
              return list;
            });
        });
      }

      return userNamesFuture.flatMap(userNames -> {
        return buildPdf(userNames)
          .onComplete(__ ->
            System.out.println("Generated pdf for user ids: " + userIds)
          );
      });
    });

    future.onComplete(ctx.asyncAssertSuccess(pdf -> ctx.assertEquals(0, pdf.length)));
  }

  @Test
  public void testAwaitNesting(TestContext ctx) {
    Future<Integer> future = async(() -> {
      List<Long> ids1 = await(userIdsFromDb());
      List<Long> ids2 = await(async(() -> await(userIdsFromDb())));

      return ids1.size() + ids2.size();
    });

    future.onComplete(ctx.asyncAssertSuccess(size -> ctx.assertEquals(6, size)));
  }

  @Test
  public void testAwaitForLoop(TestContext ctx) {
    Future<String> future = async(() -> {
      ArrayList<String> userNames = new ArrayList<>();
      for (long userId = 1; userId <= 1000; ++userId) {
        userNames.add(await(userNameFromSomeApi(userId)));
      }

      return userNames.get(userNames.size() - 1);
    });

    future.onComplete(ctx.asyncAssertSuccess(lastUserName -> {
      ctx.assertEquals("User 1000", lastUserName);
    }));
  }

  private static final long recurseIterations = 20_000;

  @Test
  public void testRecursiveAwait(TestContext ctx) {
    Future<Long> future = recurseAsync(recurseIterations, 0);

    future.onComplete(ctx.asyncAssertSuccess(res -> ctx.assertEquals(recurseIterations, res)));
  }

  // Using Future here StackOverflows, because Future is not stack safe.
  @Ignore
  @Test
  public void testRecursiveFlatMap(TestContext ctx) {
    Future<Long> future = recurseFlatMap(recurseIterations, 0);

    future.onComplete(ctx.asyncAssertSuccess(res -> ctx.assertEquals(recurseIterations, res)));
  }

  private Future<Long> recurseAsync(long iterations, long result) {
    return async(() -> {
      if (iterations == 0) {
        return result;
      } else {
        Long newResult = await(calculateNewResult(result));
        return await(recurseAsync(iterations - 1, newResult));
      }
    });
  }

  private Future<Long> calculateNewResult(long oldResult) {
    return Future.succeededFuture(oldResult + 1);
  }

  private Future<Long> recurseFlatMap(long iterations, long result) {
    Future<Long> newResult = calculateNewResult(result);
    return newResult.flatMap(res -> recurseFlatMap(iterations - 1, res));
  }
}
