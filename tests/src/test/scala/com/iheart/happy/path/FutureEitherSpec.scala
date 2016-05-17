package com.iheart.happy.path

import com.iheart.happy.path.FutureEither._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import org.specs2.specification.mutable.ExecutionEnvironment
import org.typelevel.discipline.specs2.mutable.Discipline
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Try, Failure, Success}

class FutureEitherSpec extends Specification with ExecutionEnvironment with Discipline {

  val defaultTimeout = 10.seconds // to compensage GC time on travis

  def is(implicit ee: ExecutionEnv) = {

    "apply (implicit def)" >> {
      type EitherR[T] = Either[Reason, T]
      "Right" >> {
        val either: EitherR[Int] = Right(1)
        val f: FutureEither[Int] = Future.successful(either)
        f.toEither must beRight(1).awaitFor(defaultTimeout)
      }

      "Left" >> {
        val reason: Reason = RegularReason("Something")
        val either: EitherR[Int] = Left(reason)
        val f: FutureEither[Int] = Future.successful(either)
        f.toEither must beLeft(reason).awaitFor(defaultTimeout)
      }

      "Failed" >> {
        val e = new RuntimeException("Could not run")
        val f: FutureEither[Int] = Future.failed(e)
        f.toEither must beLeft(ExceptionReason(e): Reason).awaitFor(defaultTimeout)
      }
    }

    "for comprehension" >> {
      "Option None" >> {
        val result = for {
          a ← ofOption(None: Option[String])
          b ← ofTry(Failure[String](new Exception("shouldn't throw")))
        } yield b
        result.toEither.map(_.left.get) must be_==(ItemNotFound(None)).awaitFor(defaultTimeout)
      }

      "Try Failure" >> {
        val ex = new Exception("my exception")
        val result = for {
          b ← ofTry(Failure[String](ex))
          a ← ofOption(None: Option[String])
        } yield b
        result.toEither.map(_.left.get) must be_==(ExceptionReason(ex)).awaitFor(defaultTimeout)
      }

      "tuple matching" >> {
        val result: FutureEither[Int] = for {
          (x, y) ← FutureEither.right((1, 2))
        } yield x + y

        result.toEither must beRight(3).awaitFor(defaultTimeout)
      }

      "case class matching" >> {
        case class Animal(name: String, species: String, age: Int)

        val result: FutureEither[String] = for {
          Animal(name, species, age) ← FutureEither.right(Animal("Steve", "dog", 3))
        } yield s"$name the $species is $age years old"

        result.toEither must beRight("Steve the dog is 3 years old").awaitFor(defaultTimeout)
      }
    }

    "ofFuture" >> {
      "successful Future should be FutureEither.right" >> {
        val r = "good result"
        val f = Future.successful(r)
        ofFuture(f).toEither must beRight(r).awaitFor(defaultTimeout)
      }

      "failed Future should be FutureEither.left with ExceptionReason" >> {
        val e = new RuntimeException("This failed")
        val f = Future.failed(e)
        ofFuture(f).toEither must beLeft(ExceptionReason(e): Reason).awaitFor(defaultTimeout)
      }
    }

    "ofOptional" >> {
      "Some" >> {
        ofOptional(Some(123)).toEither must beRight(123).awaitFor(defaultTimeout)
      }

      "None" >> {
        val result = ofOptional(None: Option[String])
        val expectedReason = OptionalItemNotFound(None)
        result.toEither.map(_.left.get) must be_==(expectedReason).awaitFor(defaultTimeout)
      }

      "None with toReason" >> {
        val reason = OptionalItemNotFound("Oh no")
        val result = ofOptional[String](None, reason)
        result.toEither.map(_.left.get) must be_==(reason).awaitFor(defaultTimeout)
      }
    }

    "ofFutureOption" >> {
      "Future of Some" >> {
        ofFutureOption(Future.successful(Some(123))).toEither must beRight(123).awaitFor(defaultTimeout)
      }

      "Future of None" >> {
        val expectedReason: Reason = ItemNotFound(None)
        val result = ofFutureOption(Future.successful(None))

        result.toEither must beLeft(expectedReason).awaitFor(defaultTimeout)
      }

      "failed Future" >> {
        val e = new RuntimeException("abc")
        val result = ofFutureOption(Future.failed(e))

        result.toEither must beLeft(ExceptionReason(e): Reason).awaitFor(defaultTimeout)
      }
    }

    "ofFutureOptional" >> {
      "Future of Some" >> {
        ofFutureOptional(Future.successful(Some(123))).toEither must beRight(123).awaitFor(defaultTimeout)
      }

      "Future of None" >> {
        val expectedReason: Reason = OptionalItemNotFound(None)
        val result = ofFutureOptional(Future.successful(None))

        result.toEither must beLeft(expectedReason).awaitFor(defaultTimeout)
      }

      "Future of None, with reason" >> {
        val reason: Reason = OptionalItemNotFound("Oh no")
        val result = ofFutureOptional(Future.successful(None), reason)

        result.toEither must beLeft(reason).awaitFor(defaultTimeout)
      }

      "failed Future" >> {
        val e = new RuntimeException("Ahhh")
        val f = Future.failed(e)

        ofFutureOptional(f).toEither must beLeft(ExceptionReason(e): Reason).awaitFor(defaultTimeout)
      }
    }

    "ofFutureBoolean" >> {
      "true" >> {
        val f = Future.successful(true)
        val result = ofFutureBoolean(f, 123, "")
        result.toEither must beRight(123).awaitFor(defaultTimeout)
      }

      "false" >> {
        val f = Future.successful(false)
        val result = ofFutureBoolean(f, (), "Reason for fail")
        result.toEither must beLeft(RegularReason("Reason for fail"): Reason).awaitFor(defaultTimeout)
      }

      "failed Future" >> {
        val e = new RuntimeException("Failed")
        val result = ofFutureBoolean(Future.failed(e), 123, "Whatever")

        result.toEither must beLeft(ExceptionReason(e): Reason).awaitFor(defaultTimeout)
      }
    }

    "ofFutureTry" >> {
      "Success" >> {
        val f = Future.successful(Success(1))
        ofFutureTry(f).toEither must beRight(1).awaitFor(defaultTimeout)
      }

      "Failure" >> {
        val e = new IllegalArgumentException("wrong")
        val f = Future.successful(Failure(e))
        ofFutureTry(f).toEither must beLeft(ExceptionReason(e): Reason).awaitFor(defaultTimeout)
      }

      "failed Future" >> {
        val e = new IllegalStateException("Something")
        val f = Future.failed(e)

        ofFutureTry(f).toEither must beLeft(ExceptionReason(e): Reason).awaitFor(defaultTimeout)
      }
    }

    "ofFutureEither" >> {
      "Right" >> {
        val f = Future.successful(Right(3))
        ofFutureEither(f).toEither must beRight(3).awaitFor(defaultTimeout)
      }

      "Left" >> {
        val f = Future.successful(Left("Hey"))
        ofFutureEither(f).toEither must beLeft(RegularReason("Hey"): Reason).awaitFor(defaultTimeout)
      }

      "failed Future" >> {
        val e = new NumberFormatException("That's the wrong number")
        val f = Future.failed(e)

        ofFutureEither(f).toEither must beLeft(ExceptionReason(e): Reason).awaitFor(defaultTimeout)
      }
    }

    "sequenceU" >> {
      val result: FutureEither[List[String]] = List(
        FutureEither.right("a"),
        FutureEither.right("b")
      ).sequenceU

      result.toEither must beRight(List("a", "b")).awaitFor(defaultTimeout)
    }

    "recoverLeft" >> {
      val pf: PartialFunction[Reason, Int] = {
        case RegularReason("blah") ⇒ 3
      }

      "keep the Right result" >> {
        FutureEither.right(100).recoverLeft(pf).toEither must
          beRight(100).awaitFor(defaultTimeout)
      }

      "recover if PartialFunction matches" >> {
        FutureEither.left(RegularReason("blah")).recoverLeft(pf).toEither must
          beRight(3).awaitFor(defaultTimeout)
      }

      "keep the Left result of PartialFunction fails to match" >> {
        FutureEither.left(ItemNotFound("item")).recoverLeft(pf).toEither must
          beLeft(ItemNotFound("item"): Reason).awaitFor(defaultTimeout)
      }
    }

    "recoverLeftWith" >> {
      val pf: PartialFunction[Reason, FutureEither[Int]] = {
        case ExceptionReason(e: NoSuchElementException) ⇒
          FutureEither.left(ItemNotFound("The item"): Reason)
      }

      "keep the Right result" >> {
        FutureEither.right(100).recoverLeftWith(pf).toEither must
          beRight(100).awaitFor(defaultTimeout)
      }

      "return the result of the PartialFunction if matched" >> {
        val r: Reason = ExceptionReason(new NoSuchElementException)
        FutureEither.left(r).recoverLeftWith(pf).toEither must
          beLeft(ItemNotFound("The item"): Reason).awaitFor(defaultTimeout)
      }

      "keep the Left result if PartialFunction fails to match" >> {
        val er: Reason = ExceptionReason(new RuntimeException)
        FutureEither.left(er).recoverLeftWith(pf).toEither must
          beLeft(er).awaitFor(defaultTimeout)
      }
    }

    "ensureF" >> {
      "on success" >> {
        val f = FutureEither.right("Hello").ensureF(_ ⇒ ???)(_ == "Hello")
        f.toEither must beRight("Hello").awaitFor(defaultTimeout)
      }

      "on fail" >> {
        val f = FutureEither.right(5).ensureF { n: Int ⇒
          RegularReason(s"Number $n is too small")
        }(_ > 100)

        f.toEither must beLeft(RegularReason("Number 5 is too small"): Reason).awaitFor(defaultTimeout)
      }
    }

    "wrapWithTry" >> {
      "evaluate Function0 inside a Try and return a FutureEither" >> {
        val e = new RuntimeException("run")
        wrapWithTry(throw e).toEither must beLeft(ExceptionReason(e): Reason).awaitFor(defaultTimeout)
      }
    }

    "toTry" >> {
      "ItemNotFound" >> {
        val f: FutureEither[Int] = FutureEither.left(ItemNotFound("Some reason"))
        f.toTry must beFailedTry.withThrowable[NoSuchElementException]("Some reason").awaitFor(defaultTimeout)
      }

      "OptionalItemNotFound" >> {
        val f: FutureEither[Int] = FutureEither.left(OptionalItemNotFound("Some other reason"))
        f.toTry must beFailedTry.withThrowable[NoSuchElementException]("Some other reason").awaitFor(defaultTimeout)
      }

      "RegularReason" >> {
        val reason = RegularReason(123)
        val f: FutureEither[Int] = FutureEither.left(reason)
        f.toTry must beFailedTry(RegularReasonToException(reason)).awaitFor(defaultTimeout)
      }
    }

    "FutureEitherOptionOps" >> {
      "flatten" >> {
        "Some" >> {
          val f: FutureEither[Option[Int]] = FutureEither.right(Some(1))
          f.flatten.toEither must beRight(1).awaitFor(defaultTimeout)
        }

        "None" >> {
          val f: FutureEither[Option[Int]] = FutureEither.right(None)
          f.flatten.toEither must beLeft(ItemNotFound(None): Reason).awaitFor(defaultTimeout)
        }
      }
    }
  }

  "can do for comprehension of FutureEithers without extra import" >> {
    val result = for {
      i ← FutureEither.right(1)
      j ← FutureEither.right(2)
    } yield i + j

    // cannot use the `.awaitFor(defaultTimeout)` method which will need an implicit ExecutionContext,
    // which would defeat the purpose of this test
    Await.result(result.toFuture, defaultTimeout) == 3
  }

  "satisfies cats.laws.MonadFilterLaws" >> {
    import algebra.Eq
    import cats.Monad
    import cats.laws.discipline.CartesianTests.Isomorphisms
    import cats.laws.discipline.MonadFilterTests
    import org.scalacheck.{Arbitrary, Gen}
    import cats.std.all._

    implicit val timeout: Duration = 1.second

    // Check that two `FutureEither`s are equal
    implicit def futureEitherEq[T](implicit timeout: Duration) = new Eq[FutureEither[T]] {
      def eqv(fx: FutureEither[T], fy: FutureEither[T]): Boolean =
        Await.result(fx.toEither, timeout) == Await.result(fy.toEither, timeout)
    }

    // Arbitrary FutureEither generator. The Left case is the `empty` used for MonadFilter.
    implicit def futureEitherArbitrary[T: Arbitrary]: Arbitrary[FutureEither[T]] = Arbitrary {
      implicitly[Arbitrary[T]].arbitrary.flatMap { t: T ⇒
        Gen.oneOf(
          FutureEither.monadFilter.empty: FutureEither[T],
          FutureEither.right(t)
        )
      }
    }

    // Implicits required by MonadFilterTests
    implicit val futureEitherIsomorphisms: Isomorphisms[FutureEither] =
      Isomorphisms.invariant(implicitly[Monad[FutureEither]])

    checkAll("FutureEither[T]", MonadFilterTests[FutureEither].monadFilter[String, Int, Boolean])
  }

}

