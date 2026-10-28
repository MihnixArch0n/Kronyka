plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
}

group = "example"
version = "0.0.1"
description = "Kronyka"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    val springBootBom = platform(libs.springBoot.dependencies)
    implementation(springBootBom)

    implementation(libs.springBoot.h2console)
    implementation(libs.springBoot.starterActuator)
    implementation(libs.springBoot.starterSecurity)
    implementation(libs.springBoot.starterValidation)
    implementation(libs.springBoot.starterWebmvc)
    implementation(libs.exposed.springBoot4Starter)
    implementation(libs.exposed.javaTime)
    implementation(libs.exposed.kotlinDatetime)
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.moduleKotlin)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.springdoc.openapi.webmvc.api)
    implementation(libs.scalar.webmvc)
    runtimeOnly(libs.h2)
    runtimeOnly(libs.postgresql)
    testImplementation(libs.springBoot.starterActuatorTest)
    testImplementation(libs.springBoot.starterSecurityTest)
    testImplementation(libs.springBoot.starterValidationTest)
    testImplementation(libs.springBoot.starterWebmvcTest)
    testImplementation(libs.kotlin.testJunit5)
    testRuntimeOnly(libs.junit.platformLauncher)
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.kotest.bom))
    testImplementation(libs.kotest.runnerJunit5)
    testImplementation(libs.kotest.assertionsCore)
    testImplementation(libs.mockk)
    testImplementation(libs.springmockk)
    testImplementation("org.springframework.security:spring-security-test")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

springBoot {
    mainClass.set("example.kronyka.KronykaApplicationKt")
    buildInfo()
}

tasks.named<Jar>("jar") {
    enabled = false
}

tasks.withType<Test> {
    useJUnitPlatform()
}
