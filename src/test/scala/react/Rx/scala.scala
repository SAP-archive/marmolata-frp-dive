package react.Rx

import react.LibTests.ReactLibraryTests
import org.scalatest.{FlatSpec, Matchers, AsyncFlatSpec}
import react.impls.{ReactRxImpl, MetaRxImpl}
import rx._

import scala.concurrent.Future

class RxTests extends FlatSpec with Matchers with ReactLibraryTests {
  "ReactRx" should behave like runLibraryTests(ReactRxImpl)
  "MetaRx" should behave like runLibraryTests(MetaRxImpl)
}