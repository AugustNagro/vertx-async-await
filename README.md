# Vertx-Async-Await

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

## Docs:

`async` executes some code with a Coroutine. Within the `async` scope, you can `await` Futures and program in an imperative style. Stack traces are also significantly improved in the case of errors.

`async` can only be called on threads with a Vertx Context (this is always the case when using Verticles). No new platform threads are created; all execution is done on the thread that calls async(). This means you can program Verticles [without worrying about synchronization](https://vertx.io/docs/vertx-core/java/#_standard_verticles).

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

After sharing to the vertx-dev and loom-dev mailing lists, I learned that the Continuation API turned out to be unsafe and was made jdk-private. After playing with Virtual Threads, I found that the Coroutine I had made could be re-implemented with a Virtual Thread and simple Reentrant Lock. For the execution to remain on the correct Vertx Thread, I set the Executor to use Context::runOnContext instead of the default ForkJoinPool. Finally, ThreadLocals are used to keep track of the Coroutine and Vertx Context; in the future these can be replaced by ScopeLocals when their JEP is approved.

## See Also
The same library, but for the JDK's CompletionStage: https://github.com/AugustNagro/java-async-await
