/*
 * Copyright (c) 2017-2018 The Typelevel Cats-effect Project Developers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cats.effect.laws.util

import cats.effect.IO
import cats.kernel.Eq
import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
 * Defines instances for `Future` and for `IO`, meant for law testing
 * by means of [[TestContext]].
 *
 * The [[TestContext]] interpreter is used here for simulating
 * asynchronous execution.
 */
trait TestInstances {
  /**
   * Defines equality for `IO` references that can
   * get interpreted by means of a [[TestContext]].
   */
  implicit def eqIO[A](implicit A: Eq[A], ec: TestContext): Eq[IO[A]] =
    new Eq[IO[A]] {
      def eqv(x: IO[A], y: IO[A]): Boolean =
        eqFuture[A].eqv(x.unsafeToFuture(), y.unsafeToFuture())
    }

  /**
    * Defines equality for `IO.Par` references that can
    * get interpreted by means of a [[TestContext]].
    */
  implicit def eqIOPar[A](implicit A: Eq[A], ec: TestContext): Eq[IO.Par[A]] =
    new Eq[IO.Par[A]] {
      import IO.Par.unwrap
      def eqv(x: IO.Par[A], y: IO.Par[A]): Boolean =
        eqFuture[A].eqv(unwrap(x).unsafeToFuture(), unwrap(y).unsafeToFuture())
    }

  /**
   * Defines equality for `Future` references that can
   * get interpreted by means of a [[TestContext]].
   */
  implicit def eqFuture[A](implicit A: Eq[A], ec: TestContext): Eq[Future[A]] =
    new Eq[Future[A]] {
      def eqv(x: Future[A], y: Future[A]): Boolean = {
        // Executes the whole pending queue of runnables
        ec.tick()

        x.value match {
          case None =>
            y.value.isEmpty
          case Some(Success(a)) =>
            y.value match {
              case Some(Success(b)) => A.eqv(a, b)
              case _ => false
            }
          case Some(Failure(_)) =>
            y.value match {
              case Some(Failure(_)) =>
                // All exceptions are non-terminating and given exceptions
                // aren't values (being mutable, they implement reference
                // equality), then we can't really test them reliably,
                // especially due to race conditions or outside logic
                // that wraps them (e.g. ExecutionException)
                true
              case _ =>
                false
            }
        }
      }
    }
}

object TestInstances extends TestInstances