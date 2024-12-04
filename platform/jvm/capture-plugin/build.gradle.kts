 plugins {
    alias(libs.plugins.kotlin)

    id("dependency-license-config")
    id("java-gradle-plugin")
    id("maven-publish")
    id("com.vanniktech.maven.publish") version "0.28.0" apply false
}

dependencies {
    compileOnly("com.android.tools.build:gradle:7.4.0")
    compileOnly("org.ow2.asm:asm-commons:9.4")
    compileOnly("org.ow2.asm:asm-util:9.4")

    testImplementation(gradleTestKit())
    testImplementation(kotlin("test"))
    testImplementation("com.android.tools.build:gradle:7.4.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.ow2.asm:asm-commons:9.4")
    testImplementation("org.ow2.asm:asm-util:9.4")
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.1.0")
}

gradlePlugin {
    plugins {
        create("capturePlugin") {
            id = "io.bitdrift.capture.capture-plugin"
            implementationClass = "io.bitdrift.capture.CapturePlugin"
        }
    }
}

apply {
    plugin("com.vanniktech.maven.publish")
}

publishing {
    repositories {
      mavenLocal()
    }
}

group = "io.bitdrift.capture.capture-plugin"
version = "0.1.0"