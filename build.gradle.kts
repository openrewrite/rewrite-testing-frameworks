plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
    id("org.openrewrite.build.moderne-source-available-license") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "A rewrite module automating best practices and major version migrations for popular Java test frameworks like JUnit and Mockito"

recipeDependencies {
    parserClasspath("com.github.database-rider:rider-junit5:1.44.0")
    parserClasspath("com.github.tomakehurst:wiremock-jre8:2.35.0")
    parserClasspath("com.squareup.okhttp3:mockwebserver:3.14.9")
    parserClasspath("com.squareup.okhttp3:mockwebserver:4.10.0")
    parserClasspath("junit:junit:4.+")
    parserClasspath("org.apiguardian:apiguardian-api:1.1.2")
    parserClasspath("org.assertj:assertj-core:3.+")
    parserClasspath("org.hamcrest:hamcrest:3.+")
    parserClasspath("org.jmockit:jmockit:1.22") // last version with NonStrictExpectations
    parserClasspath("org.jmockit:jmockit:1.49")
    parserClasspath("org.junit.jupiter:junit-jupiter-api:5.+")
    parserClasspath("org.junit.jupiter:junit-jupiter-params:5.+")
    parserClasspath("org.mockito:mockito-all:1.10.19")
    parserClasspath("org.mockito:mockito-core:3.+")
    parserClasspath("org.mockito:mockito-core:5.+")
    parserClasspath("org.mockito:mockito-junit-jupiter:3.+")
    parserClasspath("org.mockito:mockito-junit-jupiter:5.+")
    parserClasspath("org.powermock:powermock-api-mockito:1.6.5")
    parserClasspath("org.powermock:powermock-api-support:1.6.5")
    parserClasspath("org.powermock:powermock-core:1.6.5")
    parserClasspath("org.springframework:spring-test:6.1.+")
    parserClasspath("org.testng:testng:7.+")
    parserClasspath("org.testcontainers:testcontainers:1.20.6")
    parserClasspath("pl.pragmatists:JUnitParams:1.+")
    parserClasspath("org.easytesting:fest-assert-core:2.+")
}

val rewriteVersion = rewriteRecipe.rewriteVersion.get()
dependencies {
    implementation(platform("org.openrewrite:rewrite-bom:$rewriteVersion"))
    implementation("org.openrewrite:rewrite-java")
    implementation("org.openrewrite:rewrite-gradle")
    implementation("org.openrewrite:rewrite-maven")
    implementation("org.openrewrite.recipe:rewrite-java-dependencies:$rewriteVersion")
    implementation("org.openrewrite.recipe:rewrite-static-analysis:$rewriteVersion")
    runtimeOnly("org.openrewrite:rewrite-java-17")

    runtimeOnly("tech.picnic.error-prone-support:error-prone-contrib:latest.release:recipes")
    compileOnly("org.junit.jupiter:junit-jupiter-engine:latest.release")
    compileOnly("org.assertj:assertj-core:3.+")

    compileOnly("org.projectlombok:lombok:latest.release")
    annotationProcessor("org.projectlombok:lombok:latest.release")

    testImplementation("org.openrewrite:rewrite-java-17")
    testImplementation("org.openrewrite:rewrite-groovy")
    testImplementation("org.openrewrite:rewrite-test")
    testImplementation("org.openrewrite:rewrite-kotlin:$rewriteVersion")
    testImplementation("org.openrewrite.gradle.tooling:model:$rewriteVersion")

    annotationProcessor("org.openrewrite:rewrite-templating:${rewriteVersion}")
    implementation("org.openrewrite:rewrite-templating:${rewriteVersion}")
    compileOnly("com.google.errorprone:error_prone_core:2.+:with-dependencies") {
        exclude("com.google.auto.service", "auto-service-annotations")
    }

    testRuntimeOnly(gradleApi())

    testRuntimeOnly("com.tngtech.archunit:archunit:0.23.1")
    testRuntimeOnly("com.github.javafaker:javafaker:latest.release") {
        exclude(group = "org.yaml", module = "snakeyaml")
    }
    testRuntimeOnly("net.datafaker:datafaker:latest.release") {
        exclude(group = "org.yaml", module = "snakeyaml")
    }
    testRuntimeOnly("org.easymock:easymock:latest.release")
    testRuntimeOnly("org.jboss.arquillian.junit:arquillian-junit-core:latest.release")
    testRuntimeOnly("org.mockito.kotlin:mockito-kotlin:latest.release")
    testRuntimeOnly("org.testcontainers:testcontainers:latest.release")
    testRuntimeOnly("org.testcontainers:nginx:latest.release")
    testRuntimeOnly("org.testng:testng:latest.release")
}
