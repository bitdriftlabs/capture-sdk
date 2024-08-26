import com.github.jk1.license.filter.SpdxLicenseBundleNormalizer
import com.github.jk1.license.render.JsonReportRenderer

plugins {
    id("com.github.jk1.dependency-license-report")
}

licenseReport {
    configurations = arrayOf("releaseRuntimeClasspath")
    allowedLicensesFile = project.rootProject.file("allowed-licenses.json")
    renderers = arrayOf(JsonReportRenderer())
    filters = arrayOf(SpdxLicenseBundleNormalizer())
}
