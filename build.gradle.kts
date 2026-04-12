plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlinter)
    alias(libs.plugins.detekt)
    alias(libs.plugins.versions)
    alias(libs.plugins.graal)
}

group = "net.chrissearle"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(libs.kotlin.logging)
    implementation(libs.logback.classic)
    implementation(libs.m3u)
    implementation(libs.asciitable)
    implementation(libs.kotlinx.cli)
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("car_playlist")
            mainClass.set("ApplicationKt")
            //buildArgs.add("--enable-url-protocols=http")
            //buildArgs.add("--enable-url-protocols=https")
            javaLauncher.set(javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(25))
                vendor.set(JvmVendorSpec.GRAAL_VM)
            })
        }
    }
}

tasks.configureEach {
    if ("ApplicationKt.main" in name) {
        outputs.upToDateWhen { false }
    }
}