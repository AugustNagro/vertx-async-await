# Vertx-Async-Await

Async-Await support for [Vertx](https://vertx.io/) using [Project Loom](https://wiki.openjdk.java.net/display/loom/org.getshaka.vertx.loom.SequencingBugTests).

Please note that this project is deprecated in favor of https://github.com/vert-x3/vertx-virtual-threads-incubator

```java
import static com.augustnagro.vertx.loom.AsyncAwait.async;
import static com.augustnagro.vertx.loom.AsyncAwait.await;

Future<byte[]> buildPdf() {
  return async(() -> {
    
    List<Long> userIds = await(userIdsFromDb());
    
    List<String> userNames = new ArrayList<>(userIds.size());
    for (Long id : userIds) {
      userNames.add(await(userNameFromSomeApi(id)))
    }
    
    byte[] pdf = await(somePdfBuilder(userIds))
  
    System.out.println(userIds);
    return pdf;
  });
}
```

vs.

```java
Future<byte[]> buildPdf() {
  return userIdsFromDb().flatMap(userIds -> {
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

```

## Maven Coordinates

```xml
<dependency>
  <groupId>com.augustnagro</groupId>
  <artifactId>vertx-loom</artifactId>
  <version>0.2.2</version>
</dependency>
```

This library requires a [JDK 18 Loom Preview Build](http://jdk.java.net/loom/) and depends on `vertx-core` v. 4.2.4.

JDK 18 is used instead of the new 19 previews because no IDEs work with 19 yet.

WARNING: this library uses class [Continuation](https://download.java.net/java/early_access/loom/docs/api/java.base/java/lang/Continuation.html), which has recently [been made private](https://mail.openjdk.java.net/pipermail/loom-dev/2021-October/002983.html) in the Loom OpenJDK fork. It is likely that `Continuation` will return later in some form, either with a restricted API or virtual thread schedulers. In newer preview builds, we will use `--add-exports` to get access.

## Docs:

`async(Callable<A>)` returns `Future<A>`. Within the provided Callable, you can `await` Futures and program in an imperative style. Stack traces are also significantly improved in the case of errors.

The execution context remains on the current thread; no new threads, virtual or otherwise, are created by calling `async`.

Finally, `async` and `await` calls can be nested to any depth although recursion is not stack-stafe.

## Why Async-Await?
Vertx is great as-is.
* It's [Wicked Fast](https://www.techempower.com/benchmarks/#section=data-r20&hw=ph&test=composite&l=zik0vz-sf)
* Has great [docs](https://vertx.io/docs/vertx-core/java/#_in_the_beginning_there_was_vert_x)
* There's a huge [ecosystem of libraries](https://vertx.io/docs/), from database connectors to network proxies
* Actor-like [Verticles](https://vertx.io/docs/vertx-core/java/#_verticles) provide threading guarantees that make it safe to use Loom's Continuation class directly

But there are some downsides too.
* Using Futures is harder to read & maintain than simple blocking code.
* It's hard to debug big Future chains in IDEs
* Stack traces are poor, especially if the Exception is thrown on a different thread than your current Vertx Context. For example, when `pgClient.prepareQuery("SELEC * FROM my_table").execute()` fails, any logged stacktrace won't show you where this code is. This is because the postgres client maintains its own pool of threads.

Project Loom solves all three issues. The goal of this project is to combine the performance of Vertx's event loop with the productivity of Loom's synchronous programming model.

# Testing Notes:
* the `io.vertx.ext.unit.junit.RunTestOnContext` JUnit 4 rule is not working with this. See the simple implementation of `asyncTest` in this project.
