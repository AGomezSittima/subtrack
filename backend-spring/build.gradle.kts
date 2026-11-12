import org.apache.tools.ant.filters.ReplaceTokens
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
  java
  alias(libs.plugins.spring.boot)
  alias(libs.plugins.spotless)
}

group = "dev.subtrack"
version = "0.1.0"

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(25)
  }
}

repositories {
  mavenCentral()
}

dependencies {
  implementation(platform(SpringBootPlugin.BOM_COORDINATES))
  implementation(libs.spring.boot.starter.webmvc)
  implementation(libs.spring.boot.starter.data.jpa)
  implementation(libs.hibernate.community.dialects)
  implementation(libs.jspecify)
  runtimeOnly(libs.sqlite.jdbc)

  testImplementation(platform(SpringBootPlugin.BOM_COORDINATES))
  testImplementation(libs.spring.boot.starter.test)
  testImplementation(libs.spring.boot.starter.webmvc.test)
  testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<JavaCompile>().configureEach {
  options.encoding = "UTF-8"
  options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.processResources {
  val appVersion = project.version.toString()
  inputs.property("version", appVersion)
  filesMatching("application.yml") {
    filter<ReplaceTokens>("tokens" to mapOf("version" to appVersion))
  }
}

tasks.bootJar {
  archiveFileName = "subtrack.jar"
}

tasks.jar {
  enabled = false
}

tasks.test {
  useJUnitPlatform()
  // sqlite-jdbc loads its native library through System::load.
  jvmArgs("--enable-native-access=ALL-UNNAMED")
}

val googleJavaFormatVersion: String =
  libs.versions.google.java.format
    .get()

spotless {
  java {
    googleJavaFormat(googleJavaFormatVersion)
    removeUnusedImports()
    formatAnnotations()
  }
  kotlinGradle {
    ktlint()
  }
}
