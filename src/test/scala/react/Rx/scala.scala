package react.Rx

import react.LibTests.ReactLibraryTests
import org.scalatest.{FlatSpec, Matchers, AsyncFlatSpec}
import react.{ReactiveLibraryUsage, ReactiveLibrary}
import react.impls.{MonixImpl, ScalaRxImpl, MetaRxImpl}
import rx._

import scala.concurrent.Future

class RxTests extends FlatSpec with Matchers with ReactLibraryTests {
  def testImpl(x: ReactiveLibraryUsage with ReactiveLibrary) = x.implementationName should behave like runLibraryTests(x)

  List(new AnyRef with ScalaRxImpl with ReactiveLibraryUsage,
    new AnyRef with MetaRxImpl with ReactiveLibraryUsage,
    new AnyRef with MonixImpl with ReactiveLibraryUsage
  ) foreach testImpl
}