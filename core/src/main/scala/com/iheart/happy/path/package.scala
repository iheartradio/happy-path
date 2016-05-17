package com.iheart.happy

import cats.data.XorT

import scala.concurrent.Future

package object path extends FutureEitherSyntax {
  type FutureEither[T] = XorT[Future, Reason, T]

  // a workaround for https://issues.scala-lang.org/browse/SI-7139
  object FutureEither extends FutureEitherFunctions
}
