package com.iheart.happy.path

import cats._
import cats.data.{Xor, XorT}
import cats.syntax.{MonadFilterSyntax, TraverseSyntax}

import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Success, Failure, Try}

object FutureEitherSyntax extends FutureEitherSyntax {
  type EitherOrReason[T] = Either[Reason, T]
}

/** Convenient syntax mixin to avoid the need to import cats.syntax._ */
trait FutureEitherSyntax extends TraverseSyntax with MonadFilterSyntax {
  import ExecutionContext.Implicits.global // TODO: make this controllable by the client

  // futureMonad to allow `for` comprehensions on FutureEither by importing FutureEither._
  implicit val futureMonad: Monad[Future] = cats.std.future.futureInstance

  // listTraverse to allow `.sequenceU` on List[FutureEither[T]] by importing FutureEither._
  implicit val listTraverse: Traverse[List] = cats.std.list.listInstance

  /**
   * monadFilter to allow pattern matching and guards in `for` comprehensions
   * e.g., `for { (x, y) ← FutureEither.right((1, 2)) } yield x + y`
   */
  implicit val monadFilter: MonadFilter[FutureEither] = new MonadFilter[FutureEither] {
    def flatMap[A, B](fa: FutureEither[A])(f: A ⇒ FutureEither[B]): FutureEither[B] = fa.flatMap(f)
    def pure[A](x: A): FutureEither[A] = FutureEither.right(x)
    def empty[A]: FutureEither[A] = FutureEither.left(reasonMonoid.empty)
  }

  implicit val reasonMonoid: Monoid[Reason] = new Monoid[Reason] {
    def empty: Reason = RegularReason("No Message")
    def combine(x: Reason, y: Reason): Reason = x
  }

  implicit class FutureEitherOps[T](val self: FutureEither[T]) {
    import FutureEither._
    def toTry: Future[Try[T]] = self.toEither.map(eitherReasonToTry)
    def toFuture: Future[T] = self.toTry.map(_.get)

    def recoverLeft(
      pf: PartialFunction[Reason, T]
    ): FutureEither[T] = self.toEither.map {
      case Left(reason) if pf.isDefinedAt(reason) ⇒ Right[Reason, T](pf(reason))
      case other                                  ⇒ other
    }

    def recoverLeftWith(
      pf: PartialFunction[Reason, FutureEither[T]]
    ): FutureEither[T] = ofFuture(self.toEither).flatMap {
      case Left(reason) if pf.isDefinedAt(reason) ⇒ pf(reason)
      case other                                  ⇒ Future.successful(other)
    }

    def ensureF(onLeft: T ⇒ Reason)(f: T ⇒ Boolean): FutureEither[T] =
      self.flatMap { t: T ⇒
        if (f(t)) right(t) else left(onLeft(t))
      }

    def withFilter(p: T ⇒ Boolean): FutureEither[T] = self.filter(p)
  }

  implicit class FutureEitherOptionOps[T](val self: FutureEither[Option[T]]) {
    import FutureEither.{right, left}
    def flatten: FutureEither[T] = self.flatMap {
      case Some(t) ⇒ right(t)
      case None    ⇒ left(ItemNotFound(None))
    }
  }
}

import FutureEitherSyntax._

private[path] abstract class FutureEitherFunctions {
  import ExecutionContext.Implicits.global // TODO: make this controllable by the client

  private def toEitherTry[T](t: ⇒ T): Either[Reason, T] = tryToEither(Try(t))

  implicit private def eitherToFuture[T](e: EitherOrReason[T]): FutureEither[T] =
    Future.successful[EitherOrReason[T]](e)

  private def optionToEitherReason[T](
    o: Option[T],
    reason: ⇒ Reason = ItemNotFound(None)
  ): Either[Reason, T] =
    o.map(Right(_)).getOrElse(Left(reason))

  /**
   *  Doesn't execute in Future thread, only wrap in Future.successful.
   *  For IO blocking operations, use {@code BlockingIO}
   */
  def wrapWithTry[T](t: ⇒ T): FutureEither[T] = toEitherTry[T](t)

  def ofTry[T](t: Try[T]): FutureEither[T] = tryToEither(t)

  def ofOption[T](o: Option[T]): FutureEither[T] = optionToEitherReason[T](o)

  /**
   * Use this when you want the None to propagate as None all the way back to the client
   * instead of returning an error
   */
  def ofOptional[T](
    o: Option[T],
    reason: ⇒ Reason = OptionalItemNotFound(None)
  ): FutureEither[T] =
    optionToEitherReason[T](o, reason)

  def ofEither[L, T](either: Either[L, T]): FutureEither[T] = eitherToEitherOrReason(either)

  implicit def apply[T](fe: Future[Either[Reason, T]]): FutureEither[T] = XorT {
    fe.map(Xor.fromEither).recover {
      case scala.util.control.NonFatal(t) ⇒ Xor.left(ExceptionReason(t))
    }
  }

  def ofFuture[T](f: ⇒ Future[T]): FutureEither[T] = f.map(Right.apply)

  def ofFutureOption[T](f: Future[Option[T]]): FutureEither[T] = f.map(optionToEitherReason(_))

  def ofBoolean[T](boolean: Boolean, v: T, reasonForFalse: String): FutureEither[T] =
    if (boolean) right(v) else left(RegularReason(reasonForFalse))

  def ofFutureBoolean[T](boolean: Future[Boolean], v: T, reasonForFalse: String): FutureEither[T] =
    boolean.flatMap(b ⇒ ofBoolean(b, v, reasonForFalse).toEither)

  /**
   * Use this when you want the None to propagate as None all the way back to the client
   * instead of returning an error
   */
  def ofFutureOptional[T](
    f: Future[Option[T]],
    reason: ⇒ Reason = OptionalItemNotFound(None)
  ): FutureEither[T] =
    f.map(optionToEitherReason(_, reason))

  def ofFutureTry[T](f: Future[Try[T]]): FutureEither[T] = f.map(tryToEither)

  def ofFutureEither[L, T](f: Future[Either[L, T]]): FutureEither[T] = f.map(eitherToEitherOrReason)

  // `XorT.right` and `XorT.left` accept an `F[T]`. These two helpers accept a T, similar to `XorT.pure`

  def left[T](reason: Reason): FutureEither[T] = XorT.left(Future.successful(reason))

  def right[T](t: T): FutureEither[T] = XorT.pure(t)

  private def eitherToEitherOrReason[L, T](either: Either[L, T]): EitherOrReason[T] = either.left.map(RegularReason(_))

  private[path] def eitherReasonToTry[T](e: EitherOrReason[T]): Try[T] = {
    def itemNotFound(reason: Option[String]): Failure[T] = Failure[T] {
      reason match {
        case Some(message) ⇒ new NoSuchElementException(message)
        case None          ⇒ new NoSuchElementException()
      }
    }

    e.fold({
      case ExceptionReason(t)           ⇒ Failure(t)
      case r: RegularReason[_]          ⇒ Failure(RegularReasonToException(r))
      case ItemNotFound(reason)         ⇒ itemNotFound(reason)
      case ValidationReason(reasons)    ⇒ Failure(ValidationException(reasons))
      case OptionalItemNotFound(reason) ⇒ itemNotFound(reason)
    }, Success(_))
  }

  final case class ValidationException(reasons: Seq[String]) extends Exception(s"Failed validation: ${reasons.mkString(", ")}")

  final case class RegularReasonToException[T](reason: RegularReason[T]) extends Exception(reason.toString)

  private def tryToEither[T](t: Try[T]): EitherOrReason[T] =
    t.transform(s ⇒ Success(Right(s)), f ⇒ Success(Left(ExceptionReason(f)))).get

}
