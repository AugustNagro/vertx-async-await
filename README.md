# Vertx-Loom

Async-Await support for [Vertx](https://vertx.io/) using [Project Loom](https://wiki.openjdk.java.net/display/loom/org.getshaka.vertx.loom.SequencingBugTests).

```java
import static com.augustnagro.vertx.loom.VertxLoom.async;
import static com.augustnagro.vertx.loom.VertxLoom.coroutine;

Future<byte[]> buildPdf() {
  return async(() -> {
    
    List<Long> userIds = coroutine(userIdsFromDb());
    
    List<String> userNames = new ArrayList<>(userIds.size());
    for (Long id : userIds) {
      userNames.add(coroutine(userNameFromSomeApi(id)))
    }
    
    byte[] pdf = coroutine(somePdfBuilder(userIds))
  
    System.out.println(userIds);
    return pdf;
  });
}
```

vs.

```java
Future<byte[]> buildPdf() {
  userIdsFromDb().flatMap(userIds -> {
    List<Future<String>> userNameFutures = new ArrayList<>(userIds.size());
    // note that we could flood the api here by starting all futures at once!
    for (Long id : userIds) {
      userNameFutures.add(userNameFromSomeApi(id));
    }
    return CompositeFuture.all(userNameFutures).flatMap(compositeFuture -> {
      List<String> userNames = compositeFuture.list();
      return somePdfBuilder(userNames)
        .onSuccess(pdf -> {
          System.out.prinln(userIds);
        });
    });
  });
}
```

## Maven Coordinates

```xml
<dependency>
  <groupId>com.augustnagro</groupId>
  <artifactId>vertx-loom</artifactId>
  <version>0.2.0</version>
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

## Implementation Details

My first stab at this problem using Virtual Threads failed. I forked [vertx-gen](https://github.com/vert-x3/vertx-rx) and made most methods returning `Future<T>` instead join the Future and return T. Then for some methods like `Route.handle(..)` I spawned virtual threads.

This API was beautiful and exactly how Virtual Threads should be used; spawn one for every request and don't worry about blocking them. The big problem is that there's no way to actually block Vert'x Future, which is an interface with unbounded implementations. For this prototype I converted the Future to a Java CompletableFuture, which can be joined: `myFuture.toCompletionStage().toCompletableFuture().join()`. This turns out to be really, really, not good (it essentially spawns another thread that loops until the Future reports completion).

Months later, I saw this Scala [monadic-reflection](https://github.com/lampepfl/monadic-reflection) library using Loom's low-level Continuation api. Inspired, I implemented a vertx-specialized Coroutine that can be suspended and resumed, which is enough to implement `async` and `coroutine`.


# Testing Notes:
* the `io.vertx.ext.unit.junit.RunTestOnContext` JUnit 4 rule is not working with this. See the simple implementation of `asyncTest` in this project.