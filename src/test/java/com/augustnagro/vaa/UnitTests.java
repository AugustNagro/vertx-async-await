package com.augustnagro.vaa;

import io.vertx.core.Future;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static com.augustnagro.vaa.AsyncAwait.async;
import static com.augustnagro.vaa.AsyncAwait.await;
import static com.augustnagro.vaa.Util.asyncTest;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class UnitTests {

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
  public void testSimpleAsyncAwait() {
    asyncTest(__ -> {
      List<Long> userIds = await(userIdsFromDb());

      List<String> userNames = userIds.stream()
        .map(id -> await(userNameFromSomeApi(id)))
        .toList();

      byte[] pdf = await(buildPdf(userNames));
      assertNotNull(pdf);
    });
  }

  @Test(expected = RuntimeException.class)
  public void testAwaitOutsideAsync() {
    await(Future.succeededFuture());
  }

  @Test
  public void testAwaitNesting() {
    asyncTest(__ -> {
      List<Long> ids1 = await(userIdsFromDb());
      List<Long> ids2 = await(async(() -> await(userIdsFromDb())));

      assertEquals(6, ids1.size() + ids2.size());
    });
  }

  @Test
  public void testAwaitForLoop() {
    asyncTest(__ -> {

      ArrayList<String> userNames = new ArrayList<>();
      for (long userId = 1; userId <= 1000; ++userId) {
        userNames.add(await(userNameFromSomeApi(userId)));
      }

      String lastUserName = userNames.get(userNames.size() - 1);
      assertEquals("User 1000", lastUserName);
    });
  }

}
