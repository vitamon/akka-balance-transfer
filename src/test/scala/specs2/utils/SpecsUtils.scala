package specs2.utils

import org.specs2.specification.core
import org.specs2.specification.core.SpecificationStructure
import org.specs2.specification.create.FragmentsFactory

trait BeforeAfterAllStopOnError extends SpecificationStructure with FragmentsFactory {
  def beforeAll = {}

  def afterAll = {}

  override def map(
    fs: => core.Fragments) = {
    super.map(fs).prepend(fragmentFactory.step(beforeAll).stopOnError).append(fragmentFactory.step(afterAll))
  }
}
