package com.iheart.happy.path

/**
 * Reason due to which happy path result isn't obtained.
 */
sealed trait Reason extends Product with Serializable

@SerialVersionUID(1L)
final case class ExceptionReason[R <: Throwable](throwable: R) extends Reason

@SerialVersionUID(1L)
final case class RegularReason[R](inner: R) extends Throwable with Reason

@SerialVersionUID(1L)
final case class OptionalItemNotFound(reason: Option[String]) extends Reason

@SerialVersionUID(1L)
final case class ItemNotFound(reason: Option[String]) extends Reason

@SerialVersionUID(1L)
final case class ValidationReason(errors: Seq[String]) extends Reason

object ItemNotFound {
  def apply(reason: String): ItemNotFound = ItemNotFound(Option(reason))
}

object OptionalItemNotFound {
  def apply(reason: String): OptionalItemNotFound = OptionalItemNotFound(Option(reason))
}

