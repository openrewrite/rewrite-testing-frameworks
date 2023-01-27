@file:Suppress("GradlePackageUpdate")

import java.util.*

plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "A rewrite module automating best practices and major version migrations for popular Java test frameworks like JUnit and Mockito"

recipeDependencies {
    parserClasspath("org.assertj:assertj-core:3.+")
    parserClasspath("junit:junit:latest.release")
    parserClasspath("org.junit.platform:junit-platform-suite-api:latest.release")
    parserClasspath("org.junit.jupiter:junit-jupiter-api:latest.release")
    parserClasspath("org.junit.jupiter:junit-jupiter-params:latest.release")
    parserClasspath("io.cucumber:cucumber-java8:7.+")
    parserClasspath("io.cucumber:cucumber-java:7.+")
    parserClasspath("io.cucumber:cucumber-plugin:7.+")
    parserClasspath("io.cucumber:cucumber-junit-platform-engine:7.+")
    parserClasspath("org.hamcrest:hamcrest:latest.release")
    parserClasspath("com.squareup.okhttp3:mockwebserver:3.14.9")
    parserClasspath("org.apiguardian:apiguardian-api:1.1.2")
    parserClasspath("com.github.tomakehurst:wiremock-jre8:2.35.0")
    parserClasspath("org.mockito:mockito-all:1.10.19")
    parserClasspath("org.mockito:mockito-core:3.+")
    parserClasspath("org.mockito:mockito-junit-jupiter:3.+")
}

val rewriteVersion = rewriteRecipe.rewriteVersion.get()
dependencies {
    implementation("org.openrewrite:rewrite-java:$rewriteVersion")
    implementation("org.openrewrite:rewrite-maven:$rewriteVersion")
    runtimeOnly("org.openrewrite:rewrite-java-17:$rewriteVersion")

    compileOnly("org.projectlombok:lombok:latest.release")
    annotationProcessor("org.projectlombok:lombok:latest.release")

    testImplementation("org.openrewrite:rewrite-java-17:$rewriteVersion")
    testImplementation("org.openrewrite:rewrite-groovy:$rewriteVersion")
    testRuntimeOnly("com.github.tomakehurst:wiremock-jre8:latest.release")

    // "Before" framework dependencies
    testRuntimeOnly("junit:junit:latest.release")
    testRuntimeOnly("org.springframework:spring-test:4.+")
    testRuntimeOnly("pl.pragmatists:JUnitParams:1.+")
    testRuntimeOnly("com.squareup.okhttp3:mockwebserver:3.+")
    testRuntimeOnly("org.testng:testng:6.8")
    testRuntimeOnly("io.cucumber:cucumber-java8:7.+")
    testRuntimeOnly("io.cucumber:cucumber-java:7.+")
    testRuntimeOnly("io.cucumber:cucumber-plugin:7.+")
    testRuntimeOnly("io.cucumber:cucumber-junit-platform-engine:7.+")
    testRuntimeOnly("org.junit.platform:junit-platform-suite-api:latest.release")
    testRuntimeOnly("ch.qos.logback:logback-classic:latest.release")
    testRuntimeOnly("com.squareup.okhttp3:mockwebserver:3.14.9")
}
