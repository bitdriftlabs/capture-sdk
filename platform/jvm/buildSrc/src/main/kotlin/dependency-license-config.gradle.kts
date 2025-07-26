import com.github.jk1.license.filter.SpdxLicenseBundleNormalizer
import com.github.jk1.license.render.ReportRenderer
import com.github.jk1.license.render.TextReportRenderer

plugins {
    id("com.github.jk1.dependency-license-report")
}

licenseReport {
    configurations = arrayOf("releaseRuntimeClasspath")
    allowedLicensesFile = project.rootProject.file("allowed-licenses.json")
    excludes = arrayOf("capture-sdk")
    filters = arrayOf(SpdxLicenseBundleNormalizer())
    // generates THIRD-PARTY-NOTICES.txt when running `./gradlew generateLicenseReport`
    renderers = arrayOf<ReportRenderer>(TextReportRenderer())
}
