plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
}

group = "example"
version = "0.0.1-SNAPSHOT"
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
    implementation("org.projectlombok:lombok")
    val springBootBom = platform(libs.springBoot.dependencies)
    implementation(springBootBom)

    implementation(libs.springBoot.h2console)
    implementation(libs.springBoot.starterActuator)
    implementation(libs.springBoot.starterJdbc)
    implementation(libs.springBoot.starterSecurity)
    implementation(libs.springBoot.starterValidation)
    implementation(libs.springBoot.starterWebmvc)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.javaTime)
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.moduleKotlin)
    runtimeOnly(libs.h2)
    runtimeOnly(libs.postgresql)
    testImplementation(libs.springBoot.starterActuatorTest)
    testImplementation(libs.springBoot.starterSecurityTest)
    testImplementation(libs.springBoot.starterValidationTest)
    testImplementation(libs.springBoot.starterWebmvcTest)
    testImplementation(libs.kotlin.testJunit5)
    testRuntimeOnly(libs.junit.platformLauncher)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
