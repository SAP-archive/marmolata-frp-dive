package react.Rx

import org.scalatest.{FlatSpec, Matchers, AsyncFlatSpec}
import react.{ReactiveLibraryUsage, ReactiveLibrary}
import react.LibTests.ReactLibraryTests

import scala.concurrent.Future


trait TestImplementation extends FlatSpec with Matchers with ReactLibraryTests {
  def testImpl(x: ReactiveLibraryUsage with ReactiveLibrary) = x.implementationName should behave like runLibraryTests(x)

  def impls: List[ReactiveLibraryUsage with ReactiveLibrary]

  impls foreach testImpl
}
