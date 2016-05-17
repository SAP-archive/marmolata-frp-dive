package react.Rx

import react.LibTests.ReactLibraryTests
import org.scalatest.{FlatSpec, Matchers, AsyncFlatSpec}
import react.ReactiveLibrary
import react.impls.{MonixImpl, ScalaRxImpl, MetaRxImpl}
import rx._

import scala.concurrent.Future

class RxTests extends FlatSpec with Matchers with ReactLibraryTests {
  def testImpl(x: ReactiveLibrary) = x.implementationName should behave like runLibraryTests(x)

  List(ScalaRxImpl, MetaRxImpl, MonixImpl) foreach testImpl
}