@file:Suppress("GradlePackageUpdate")

import java.util.*

plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
    id("org.openrewrite.rewrite") version "latest.release"
}

rewrite {
    activeRecipe("org.openrewrite.java.format.AutoFormat", "org.openrewrite.java.cleanup.Cleanup")
}

group = "org.openrewrite.recipe"
description = "A rewrite module automating best practices and major version migrations for popular Java test frameworks like JUnit and Mockito"

val mockitoVersions: List<String> = listOf("3")

sourceSets {
    mockitoVersions.forEach { version ->
        create("testWithMockito_${version}") {
            compileClasspath += sourceSets.getByName("main").output
            runtimeClasspath += sourceSets.getByName("main").output
        }
    }
}

configurations {
    mockitoVersions.forEach { version ->
        getByName("testWithMockito_${version}RuntimeOnly") {
            isCanBeResolved = true
            extendsFrom(getByName("testImplementation"))
        }
        getByName("testWithMockito_${version}Implementation") {
            isCanBeResolved = true
            extendsFrom(getByName("testImplementation"))
        }
    }
}

val mockito1Version = "1.10.19"
val assertJVersion = "3.18.1"

val rewriteVersion = rewriteRecipe.rewriteVersion.get()
dependencies {
    implementation("org.openrewrite:rewrite-java:$rewriteVersion")
    implementation("org.openrewrite:rewrite-maven:$rewriteVersion")
    runtimeOnly("org.openrewrite:rewrite-java-17:$rewriteVersion")

    runtimeOnly("org.assertj:assertj-core:3.+")
    runtimeOnly("io.cucumber:cucumber-java8:7.+")
    runtimeOnly("io.cucumber:cucumber-java:7.+")
    runtimeOnly("io.cucumber:cucumber-plugin:7.+")
    runtimeOnly("io.cucumber:cucumber-junit-platform-engine:7.+")
    runtimeOnly("org.hamcrest:hamcrest:latest.release")
    runtimeOnly("org.junit.platform:junit-platform-suite-api:latest.release")
    runtimeOnly("org.junit.jupiter:junit-jupiter-api:latest.release")
    runtimeOnly("org.junit.jupiter:junit-jupiter-params:latest.release")

    compileOnly("org.projectlombok:lombok:latest.release")
    annotationProcessor("org.projectlombok:lombok:latest.release")

    testImplementation("org.openrewrite:rewrite-java-17:$rewriteVersion")
    testImplementation("org.openrewrite:rewrite-groovy:$rewriteVersion")
    testRuntimeOnly("com.github.tomakehurst:wiremock-jre8:latest.release")

    // "Before" framework dependencies
    testRuntimeOnly("junit:junit:latest.release")
    testRuntimeOnly("org.springframework:spring-test:4.+")
    testRuntimeOnly("ch.qos.logback:logback-classic:1.2.+")
    testRuntimeOnly("org.mockito:mockito-all:$mockito1Version")
    testRuntimeOnly("pl.pragmatists:JUnitParams:1.+")
    testRuntimeOnly("com.squareup.okhttp3:mockwebserver:3.+")
    testRuntimeOnly("org.testng:testng:6.8")

    "testWithMockito_3RuntimeOnly"("org.junit.jupiter:junit-jupiter-engine:latest.release")
    "testWithMockito_3RuntimeOnly"("junit:junit:latest.release")
    "testWithMockito_3RuntimeOnly"("org.mockito:mockito-core:3.+")
}

mockitoVersions.forEach { version ->
    val sourceSetName = "testWithMockito_${version}"
    val sourceSetReference = project.sourceSets.getByName(sourceSetName)
    val testTask = tasks.register<Test>(sourceSetName) {
        description = "Runs the unit tests for ${sourceSetName}."
        group = "verification"
        useJUnitPlatform()
        jvmArgs = listOf("-XX:+UnlockDiagnosticVMOptions", "-XX:+ShowHiddenFrames")
        testClassesDirs = sourceSetReference.output.classesDirs
        classpath = sourceSetReference.runtimeClasspath
        shouldRunAfter(tasks.test)
    }
    tasks.named("check").configure {
        dependsOn(testTask)
    }
}
