package react.Rx

import org.scalatest.{FlatSpec, Matchers, AsyncFlatSpec}
import react.{ReactiveLibraryUsage, ReactiveLibrary}
import react.LibTests.ReactLibraryTests

import scala.concurrent.Future


trait TestImplementation extends FlatSpec with Matchers with ReactLibraryTests {
  reactLibrary.implementationName should behave like runLibraryTests
  it should behave like runPropertyTests
}
