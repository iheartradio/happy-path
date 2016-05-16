[![Build Status](https://travis-ci.org/kailuowang/henkan.svg)](https://travis-ci.org/kailuowang/henkan)
[![Codacy Badge](https://api.codacy.com/project/badge/grade/94b5ef789e73441ca101c5d0e083aef6)](https://www.codacy.com/app/kailuo-wang/henkan)
[![Codacy Badge](https://api.codacy.com/project/badge/coverage/94b5ef789e73441ca101c5d0e083aef6)](https://www.codacy.com/app/kailuo-wang/henkan)
[![Stories in Ready](https://badge.waffle.io/kailuowang/henkan.svg?label=ready&title=Ready)](http://waffle.io/kailuowang/henkan)
[ ![Download](https://api.bintray.com/packages/kailuowang/maven/henkan/images/download.svg) ](https://bintray.com/kailuowang/maven/henkan/_latestVersion)

# HappyPath [変換]

## FutureEither

`FutureEither` is to unify `Future`s of different
types (e.g., `Future[T]`, `Future[Option[T]]`, `Future[Try[T]]`, `Future[Boolean]`).
It can be seen as a wrapper around `Future[Either[_, _]]`, where the `Right` side of the `Either`
is the successful result `T` and the `Left` side is a `Reason` explaining why the computation failed.

### Example usage

To use `FutureEither`, it's recommended to use the wildcard import,

```scala
scala> import com.iheart.happy.path._
import com.iheart.happy.path._

scala> import com.iheart.happy.path.FutureEither._
import com.iheart.happy.path.FutureEither._

scala> import concurrent.Future
import concurrent.Future
```

This provides all the instances needed for things such as `map`/`flatMap`
(otherwise you might see various `could not find implicit value` compilation errors).

Once imported, you can use various helpers:
```scala
scala> import scala.util.Success
import scala.util.Success

scala> // The expressions below each yield a `FutureEither[Int]` containing a `Right(1)`
     | val f1 = right(1)
f1: com.iheart.happy.path.FutureEither.FutureEither[Int] = XorT(List())

scala> val f2 = ofFuture(Future.successful(1))
f2: com.iheart.happy.path.FutureEither.FutureEither[Int] = XorT(List())

scala> val f3 = ofTry(Success(1))
f3: com.iheart.happy.path.FutureEither.FutureEither[Int] = XorT(List())

scala> val f4 = ofOption(Some(1))
f4: com.iheart.happy.path.FutureEither.FutureEither[Int] = XorT(List())

scala> val f5 = ofFutureOption(Future.successful(Some(1)))
f5: com.iheart.happy.path.FutureEither.FutureEither[Int] = XorT(List())

scala> // If we wanted to add some of these futures together:
     | val f123 = for {
     |   v1 <- f1
     |   v2 <- f2
     |   v3 <- f3
     | } yield v1 + v2 + v3
f123: cats.data.XorT[scala.concurrent.Future,com.iheart.happy.path.Reason,Int] = XorT(List())
```

`f123` is a `FutureEither[Int]` containing the results of `f1`, `f2`, `f3` added together (3).

Now let's say somewhere we encounter a failure:

```scala
scala> val failedF: FutureEither[Int] = left(RegularReason("Something happened"))
failedF: com.iheart.happy.path.FutureEither.FutureEither[Int] = XorT(List())

scala> val f45 = for {
     |   v4 <- f4
     |   failed <- failedF
     |   v5 <- f5
     | } yield v4 + v5
f45: cats.data.XorT[scala.concurrent.Future,com.iheart.happy.path.Reason,Int] = XorT(List())
```

The result of `f45` is a failed `FutureEither[Int]` containing a `Left[RegularReason[String]]`.

Some other usage examples can be found in [the tests](https://github.com/iheartradio/poweramp-common/blob/master/lib/src/test/scala/com/iheart/poweramp/common/functional/FutureEitherSpec.scala).

### Reason

When a `FutureEither` fails, usually the reason for failure needs to be propagated back to the
client, or matched to figure out how to recover from it. Poweramp defines the following
`Reason`s for failure:

### `ExceptionReason`
When the underlying `Future` fails because of an exception:

```scala
scala> val failedF = Future.failed(new RuntimeException("Something happened"))
failedF: scala.concurrent.Future[Nothing] = scala.concurrent.impl.Promise$KeptPromise@1ac6924a

scala> val failedFE = ofFuture(failedF)
failedFE: com.iheart.happy.path.FutureEither.FutureEither[Nothing] = XorT(List())
```

In the above example, `failedFE` is a `FutureEither` containing a `Left[ExceptionReason[RuntimeException]]`.
This will be propagated to the client as a 500 error.

#### `OptionalItemNotFound` / `ItemNotFound`
There are two different cases we want to handle when an optional item returns `None`.

If it's a requested resource that makes sense to be absent
(for example, user requests a track ID that doesn't exist in the database)
we use `OptionalItemNotFound` to propagate the error as a 404.

Otherwise, if it's an internal error (some essential resource that we expect to exist,
but doesn't), we use `ItemNotFound`, which will propagate as a 500 error.

```scala
scala> val opt: Option[Int] = None
opt: Option[Int] = None

scala> val optFE1 = ofOptional(opt)
optFE1: com.iheart.happy.path.FutureEither.FutureEither[Int] = XorT(List())

scala> val optFE2 = ofOption(opt)
optFE2: com.iheart.happy.path.FutureEither.FutureEither[Int] = XorT(Success(Left(ItemNotFound(None))))

scala> val optF = Future.successful(opt)
optF: scala.concurrent.Future[Option[Int]] = scala.concurrent.impl.Promise$KeptPromise@65382169

scala> val optFE3 = ofFutureOptional(optF, OptionalItemNotFound("This thing not found"))
optFE3: com.iheart.happy.path.FutureEither.FutureEither[Int] = XorT(List())

scala> val optFE4 = ofFutureOption(optF)
optFE4: com.iheart.happy.path.FutureEither.FutureEither[Int] = XorT(List())
```

In the above example, `optFE1` and `optFE3` are equivalent, and both contain a `Left[OptionalItemNotFound]`.
`optFE2` and `optFE4` are equivalent, and both contain a `Left[ItemNotFound]`.

#### `ValidationReason`
If the user provides invalid input, we want to return a list of all the validation errors.

TODO

#### `RegularReason`
For computations that fail for inconsequential reasons (usually internal),
we use `RegularReason` to avoid logging them.
