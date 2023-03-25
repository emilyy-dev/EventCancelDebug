plugins {
  java
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(17))
  }
}

group = "ar.emily"
version = "1.0"

repositories {
  mavenCentral()
  maven("https://repo.papermc.io/repository/maven-public/")
}

val bundle: Configuration by configurations.creating {
  isCanBeConsumed = false
  isCanBeResolved = true
  configurations["runtimeClasspath"].extendsFrom(this)
  configurations["compileClasspath"].extendsFrom(this)
}

dependencies {
  implementation("io.papermc.paper:paper-api:1.19.4-R0.1-SNAPSHOT")
  bundle("org.glavo:classfile:0.4.0")
}

tasks {
  compileJava {
    options.release.set(17)
  }

  jar {
    inputs.files(bundle)
    metaInf {
      from(bundle).into("dependencies")
      from(file("LICENSES"))
    }
  }
}
