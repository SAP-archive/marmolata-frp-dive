package react.Rx

import org.scalatest.{FlatSpec, Matchers, AsyncFlatSpec}
import react.{ReactiveLibraryUsage, ReactiveLibrary}
import react.LibTests.{TestConfiguration, ReactLibraryTests}

import scala.concurrent.Future

trait TestImplementation extends Matchers with ReactLibraryTests {
  self: FlatSpec =>
  def shouldRunPropertyTests: Boolean

  def testConfiguration: TestConfiguration = TestConfiguration.all

  reactLibrary.implementationName should behave like runLibraryTests(testConfiguration)
  if (shouldRunPropertyTests) {
    it should behave like runPropertyTests
  }
}
