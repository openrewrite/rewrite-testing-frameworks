plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
    id("org.openrewrite.build.moderne-source-available-license") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "A rewrite module automating best practices and major version migrations for popular Java test frameworks like JUnit and Mockito"

recipeDependencies {
    parserClasspath("com.github.database-rider:rider-junit5:1.44.0")
    parserClasspath("com.github.stefanbirkner:system-rules:1.19.0")
    parserClasspath("com.github.tomakehurst:wiremock-jre8:2.35.0")
    parserClasspath("com.google.errorprone:error_prone_core:2.+")
    parserClasspath("com.google.guava:guava:33.5.0-jre")
    parserClasspath("com.squareup.okhttp3:mockwebserver:3.14.9")
    parserClasspath("com.squareup.okhttp3:mockwebserver:4.10.0")
    parserClasspath("com.squareup.okhttp3:mockwebserver3:5.1.0")
    parserClasspath("junit:junit:4.+")
    parserClasspath("org.apiguardian:apiguardian-api:1.1.2")
    parserClasspath("org.assertj:assertj-core:3.+")
    parserClasspath("org.easytesting:fest-assert-core:2.+")
    parserClasspath("org.hamcrest:hamcrest:3.+")
    parserClasspath("org.jboss.byteman:byteman-bmunit5:4.0.25")
    parserClasspath("org.jmockit:jmockit:1.22") // last version with NonStrictExpectations
    parserClasspath("org.jmockit:jmockit:1.49")
    parserClasspath("org.junit.jupiter:junit-jupiter-api:5.+")
    parserClasspath("org.junit.jupiter:junit-jupiter-api:6.+")
    parserClasspath("org.junit.jupiter:junit-jupiter-params:5.+")
    parserClasspath("org.junit.jupiter:junit-jupiter-params:6.+")
    parserClasspath("org.junit.jupiter:junit-jupiter-migrationsupport:5.+")
    parserClasspath("org.mockito:mockito-all:1.10.19")
    parserClasspath("org.mockito:mockito-core:3.+")
    parserClasspath("org.mockito:mockito-core:5.+")
    parserClasspath("org.mockito:mockito-junit-jupiter:2.+")
    parserClasspath("org.mockito:mockito-junit-jupiter:3.+")
    parserClasspath("org.mockito:mockito-junit-jupiter:5.+")
    parserClasspath("org.opentest4j:opentest4j:1.+")
    parserClasspath("org.powermock:powermock-api-mockito:1.6.5")
    parserClasspath("org.powermock:powermock-api-support:1.6.5")
    parserClasspath("org.powermock:powermock-core:1.6.5")
    parserClasspath("org.springframework:spring-test:6.1.+")
    parserClasspath("org.testcontainers:testcontainers:1.20.6")
    parserClasspath("org.testcontainers:junit-jupiter:1.20.6")
    parserClasspath("org.testng:testng:7.+")
    parserClasspath("pl.pragmatists:JUnitParams:1.+")
    parserClasspath("uk.org.webcompere:system-stubs-core:2.1.8")
    parserClasspath("uk.org.webcompere:system-stubs-jupiter:2.1.8")

    testParserClasspath("com.github.database-rider:rider-spring:1.18.0")
    testParserClasspath("com.google.guava:guava:33.5.0-jre")
    testParserClasspath("com.google.truth:truth:1.4.5")
    testParserClasspath("io.grpc:grpc-testing:1.+")
    testParserClasspath("org.easymock:easymock:5.6.0")
    testParserClasspath("org.jboss.byteman:byteman-bmunit:4.0.25")
    testParserClasspath("org.powermock:powermock-module-junit4:1.6.5")

    testParserClasspath("org.testcontainers:nginx:1.+")

    testParserClasspath("org.testcontainers:testcontainers:2.0.1")
    testParserClasspath("org.testcontainers:testcontainers-cassandra:2.0.1")
    testParserClasspath("org.testcontainers:testcontainers-kafka:2.0.1")
    testParserClasspath("org.testcontainers:testcontainers-localstack:2.0.1")
    testParserClasspath("org.testcontainers:testcontainers-mysql:2.0.1")
}

val rewriteVersion = rewriteRecipe.rewriteVersion.get()
dependencies {
    implementation(platform("org.openrewrite:rewrite-bom:${rewriteVersion}"))
    implementation("org.openrewrite:rewrite-java")
    implementation("org.openrewrite:rewrite-gradle")
    implementation("org.openrewrite:rewrite-maven")
    implementation("org.openrewrite.recipe:rewrite-java-dependencies:${rewriteVersion}")
    implementation("org.openrewrite.recipe:rewrite-static-analysis:${rewriteVersion}")

    runtimeOnly("tech.picnic.error-prone-support:error-prone-contrib:${rewriteVersion}:recipes")
    compileOnly("org.junit.jupiter:junit-jupiter-engine:5.13.3")
    compileOnly("org.assertj:assertj-core:3.+")
    compileOnly("junit:junit:4.13.2")

    compileOnly("org.projectlombok:lombok:latest.release")
    annotationProcessor("org.projectlombok:lombok:latest.release")
    annotationProcessor("org.openrewrite:rewrite-templating:${rewriteVersion}")
    implementation("org.openrewrite:rewrite-templating:${rewriteVersion}")
    compileOnly("com.google.errorprone:error_prone_core:2.+") {
        exclude("com.google.auto.service", "auto-service-annotations")
        exclude("io.github.eisop","dataflow-errorprone")
    }

    testImplementation("org.openrewrite:rewrite-java-21")
    testImplementation("org.openrewrite:rewrite-groovy")
    testImplementation("org.openrewrite:rewrite-test")
    testImplementation("org.openrewrite:rewrite-kotlin")
    testImplementation("org.openrewrite.gradle.tooling:model:${rewriteVersion}")
    testRuntimeOnly(gradleApi())

    testRuntimeOnly("com.tngtech.archunit:archunit:0.23.1")
    testRuntimeOnly("com.github.javafaker:javafaker:latest.release") {
        exclude(group = "org.yaml", module = "snakeyaml")
    }
    testRuntimeOnly("net.datafaker:datafaker:latest.release") {
        exclude(group = "org.yaml", module = "snakeyaml")
    }
    testRuntimeOnly("org.jboss.arquillian.junit:arquillian-junit-core:latest.release")
    testRuntimeOnly("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testRuntimeOnly("org.testng:testng:latest.release")
}

tasks.test {
    // The `TestNgToAssertJTest` tests require a lot of memory for the `JavaTemplate` caching
    maxHeapSize = "1g"
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Arewrite.javaParserClasspathFrom=resources")
}
