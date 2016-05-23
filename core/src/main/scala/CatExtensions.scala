package react.cat

import cats.Functor

import language.higherKinds

trait Filterable[F[+_]] {
  def filter[A](v: F[A], cond: A => Boolean): F[A]
}

trait Mergeable[F[+_]] {
  def merge[A](x1: F[A], x2: F[A]): F[A]
}

object implicits {
  implicit class FilterableObs[F[+_], +A](value: F[A])(implicit isFilterable: Filterable[F]) {
    def filter(cond: A => Boolean) = isFilterable.filter(value, cond)

    def mapPartial[B](f: PartialFunction[A, B])(implicit isFunctor: Functor[F]) = {
      import cats.syntax.functor._
      filter(f.isDefinedAt).map(f.apply)
    }
  }

  implicit class MergeableObs[F[+_], +A](value: F[A])(implicit isMergeable: Mergeable[F]) {
    def merge[B >: A](other: F[B]): F[B] = isMergeable.merge(value, other)
  }
}