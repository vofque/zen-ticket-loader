package org.vfq.zenticketloader.utils

object Utils {

  implicit class OptionOps[A](valueOpt: Option[A]) {

    def toEither[B](left: => B): Either[B, A] = {
      valueOpt match {
        case Some(value) => Right(value)
        case None        => Left(left)
      }
    }
  }
}
