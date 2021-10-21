# Vertx-Loom

Async-Await support for [Vertx](https://vertx.io/) using [Project Loom](https://wiki.openjdk.java.net/display/loom/Main).

```java
import static com.augustnagro.vertx.loom.VertxLoom.async;
import static com.augustnagro.vertx.loom.VertxLoom.await;

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
  <version>0.1.0</version>
</dependency>
```

This library requires the latest [JDK 18 Loom Preview Build](http://jdk.java.net/loom/) and depends on `vertx-core` v. 4.1.5.

WARNING: this library uses class [Continuation](https://download.java.net/java/early_access/loom/docs/api/java.base/java/lang/Continuation.html), which has recently [been made private](https://mail.openjdk.java.net/pipermail/loom-dev/2021-October/002983.html) in the Loom OpenJDK fork. It is likely that `await()` will eventually be possible in Loom with a different abstraction, whether that's a restricted Continuation API, or a custom virtual thread scheduler. However, it goes without saying not to use this in production until Loom is merged into OpenJDK.

## Docs:

`async` executes some code with a Coroutine. Within the `async` scope, you can `await` Futures and program in an imperative style. Stack traces are also significantly improved in the case of errors.

`async` can only be called on threads with a Vertx Context (this is always the case when using Verticles). No new threads, virtual or otherwise, are created.

Finally, `async` and `await` calls can be nested to any depth.

## Why Async-Await?
Vertx is great as-is.
* It's [Wicked Fast](https://www.techempower.com/benchmarks/#section=data-r20&hw=ph&test=composite&l=zik0vz-sf)
* Has great [docs](https://vertx.io/docs/vertx-core/java/#_in_the_beginning_there_was_vert_x)
* There's a huge [ecosystem of libraries](https://vertx.io/docs/) under the official Vertx banner, from database connectors to network proxies.
* Because the actor-like [Verticles](https://vertx.io/docs/vertx-core/java/#_verticles) always execute your program on the same event-loop thread, you can code as if you're writing a single-threaded application, despite using Futures!

But there are some downsides too.
* Often it's difficult to express something with the Future API, when it is trivial with simple blocking code.
* Vertx Future is not stack-safe. Ie, it will StackOverflow if you recursively call flatMap.
* It's hard to debug big Future chains in IDEs
* Stack traces are meaningless if the Exception is thrown in a different thread than your Vertx Context. For example, when `pgClient.prepareQuery("SELEC * FROM my_table").execute()` fails, any logged stacktrace won't show you where this code is. This is because the postgress client maintains its own pool of threads.

Project Loom solves all four issues. (Debugging support is not working yet in IDEs for the current JDK Preview).

## Implementation Details

My first stab at this problem using Virtual Threads failed. I forked [vertx-gen](https://github.com/vert-x3/vertx-rx) and made most methods returning `Future<T>` instead join the Future and return T. Then for some methods like `Route.handle(..)` I spawned virtual threads.

This API was beautiful and exactly how Virtual Threads should be used; spawn one for every request and don't worry about blocking them. The big problem is that there's no way to actually block Vert'x Future, which is an interface with unbounded implementations. For this prototype I converted the Future to a Java CompletableFuture, which can be joined: `myFuture.toCompletionStage().toCompletableFuture().join()`. This turns out to be really, really, not good (it essentially spawns another thread that loops until the Future reports completion).

Months later, I saw this Scala [monadic-reflection](https://github.com/lampepfl/monadic-reflection) library using Loom's low-level Continuation api. Inspired, I implemented a vertx-specialized Coroutine that can be suspended and resumed, which is enough to implement `async` and `await`.

