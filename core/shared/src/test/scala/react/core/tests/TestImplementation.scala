package react.core.tests

import org.scalatest.{FlatSpec, Matchers}

trait TestImplementation extends Matchers with ReactLibraryTests {
  self: FlatSpec =>
  def shouldRunPropertyTests: Boolean

  def testConfiguration: TestConfiguration = TestConfiguration.all

  reactLibrary.implementationName should behave like runLibraryTests(testConfiguration)
  if (shouldRunPropertyTests) {
    it should behave like runPropertyTests
  }
}
