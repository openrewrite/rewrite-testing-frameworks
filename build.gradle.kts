@file:Suppress("GradlePackageUpdate")

import nebula.plugin.contacts.Contact
import nebula.plugin.contacts.ContactsExtension
import nebula.plugin.release.NetflixOssStrategies.SNAPSHOT
import nebula.plugin.release.git.base.ReleasePluginExtension
import nl.javadude.gradle.plugins.license.LicenseExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.*

plugins {
    `java-library`
    `maven-publish`
    signing

    id("org.jetbrains.kotlin.jvm") version "1.6.21"
    id("nebula.maven-resolved-dependencies") version "17.3.2"
    id("nebula.release") version "15.3.1"
    id("io.github.gradle-nexus.publish-plugin") version "1.0.0"

    id("com.github.hierynomus.license") version "0.16.1"
    id("com.github.jk1.dependency-license-report") version "1.16"
    id("org.owasp.dependencycheck") version "latest.release"

    id("nebula.maven-publish") version "17.3.2"
    id("nebula.contacts") version "5.1.0"
    id("nebula.info") version "11.1.0"

    id("nebula.javadoc-jar") version "17.3.2"
    id("nebula.source-jar") version "17.3.2"
    id("nebula.maven-apache-license") version "17.3.2"

    id("org.openrewrite.rewrite") version "latest.release"
}

apply(plugin = "nebula.publish-verification")

rewrite {
    activeRecipe("org.openrewrite.java.format.AutoFormat", "org.openrewrite.java.cleanup.Cleanup")
}

configure<ReleasePluginExtension> {
    defaultVersionStrategy = SNAPSHOT(project)
}

dependencyCheck {
    analyzers.assemblyEnabled = false
    suppressionFile = "suppressions.xml"
    failBuildOnCVSS = 9.0F
}

group = "org.openrewrite.recipe"
description =
    "A rewrite module automating best practices and major version migrations for popular Java test frameworks like JUnit and Mockito"

val mockitoVersions: List<String> = listOf("3")

sourceSets {
    mockitoVersions.forEach { version ->
        create("testWithMockito_${version}") {
            compileClasspath += sourceSets.getByName("main").output
            runtimeClasspath += sourceSets.getByName("main").output
        }
    }
}

repositories {
    if(!project.hasProperty("releasing")) {
        mavenLocal()
        maven {
            url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
        }
    }
    mavenCentral()
}

nexusPublishing {
    repositories {
        sonatype()
    }
}


val signingKey: String? by project
val signingPassword: String? by project
val requireSigning = project.hasProperty("forceSigning") || project.hasProperty("releasing")
if(signingKey != null && signingPassword != null) {
    signing {
        isRequired = requireSigning
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["nebula"])
    }
} else if(requireSigning) {
    throw RuntimeException("Artifact signing is required, but signingKey and/or signingPassword are null")
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
    all {
        resolutionStrategy {
            cacheChangingModulesFor(0, TimeUnit.SECONDS)
            cacheDynamicVersionsFor(0, TimeUnit.SECONDS)
        }
    }
}

val mockito1Version = "1.10.19"
val assertJVersion = "3.18.1"

val rewriteVersion = if(project.hasProperty("releasing")) {
    "latest.release"
} else {
    "latest.integration"
}
dependencies {
    implementation("org.openrewrite:rewrite-java:$rewriteVersion")
    implementation("org.openrewrite:rewrite-maven:$rewriteVersion")
    runtimeOnly("com.fasterxml.jackson.core:jackson-core:2.13.4")
    runtimeOnly("org.openrewrite:rewrite-java-17:$rewriteVersion")

    runtimeOnly("org.assertj:assertj-core:3.+")
    runtimeOnly("io.cucumber:cucumber-java8:7.+")
    runtimeOnly("io.cucumber:cucumber-java:7.+")
    runtimeOnly("io.cucumber:cucumber-plugin:7.+")
    runtimeOnly("io.cucumber:cucumber-junit-platform-engine:7.+")
    runtimeOnly("org.junit.platform:junit-platform-suite-api:latest.release")


    compileOnly("org.projectlombok:lombok:latest.release")
    annotationProcessor("org.projectlombok:lombok:latest.release")

    testImplementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    testImplementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    testImplementation("org.openrewrite:rewrite-java-11:$rewriteVersion")
    testImplementation("org.openrewrite:rewrite-test:$rewriteVersion")
    testImplementation("org.openrewrite:rewrite-java-tck:$rewriteVersion")
    testImplementation("org.assertj:assertj-core:latest.release")
    testImplementation("org.junit.jupiter:junit-jupiter-api:latest.release")
    testImplementation("org.junit.jupiter:junit-jupiter-params:latest.release")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:latest.release")
    testRuntimeOnly("com.github.tomakehurst:wiremock-jre8:latest.release")

    // "Before" framework dependencies
    testRuntimeOnly("junit:junit:latest.release")
    testRuntimeOnly("org.springframework:spring-test:4.+")
    testRuntimeOnly("ch.qos.logback:logback-classic:1.2.+")
    testRuntimeOnly("org.mockito:mockito-all:$mockito1Version")
    testRuntimeOnly("org.hamcrest:hamcrest:latest.release")
    testRuntimeOnly("pl.pragmatists:JUnitParams:1.+")
    testRuntimeOnly("com.squareup.okhttp3:mockwebserver:3.+")
    


    "testWithMockito_3RuntimeOnly"("org.junit.jupiter:junit-jupiter-engine:latest.release")
    "testWithMockito_3RuntimeOnly"("junit:junit:latest.release")
    "testWithMockito_3RuntimeOnly"("org.mockito:mockito-core:3.+")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.withType(KotlinCompile::class.java).configureEach {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    jvmArgs = listOf("-Xmx1g", "-XX:+UnlockDiagnosticVMOptions", "-XX:+ShowHiddenFrames")
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

tasks.named<JavaCompile>("compileJava") {
    sourceCompatibility = JavaVersion.VERSION_1_8.toString()
    targetCompatibility = JavaVersion.VERSION_1_8.toString()

    options.isFork = true
    options.compilerArgs.addAll(listOf("--release", "8"))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.withType<Javadoc> {
    // assertTrue(boolean condition) -> assertThat(condition).isTrue()
    // warning - invalid usage of tag >
    // see also: https://blog.joda.org/2014/02/turning-off-doclint-in-jdk-8-javadoc.html
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

configure<ContactsExtension> {
    val j = Contact("jkschneider@gmail.com")
    j.moniker("Jonathan Schneider")

    people["jkschneider@gmail.com"] = j
}

configure<LicenseExtension> {
    ext.set("year", Calendar.getInstance().get(Calendar.YEAR))
    skipExistingHeaders = true
    header = project.rootProject.file("gradle/licenseHeader.txt")
    mapping(mapOf("kt" to "SLASHSTAR_STYLE", "java" to "SLASHSTAR_STYLE"))
    strictCheck = true
}

configure<PublishingExtension> {
    publications {
        named("nebula", MavenPublication::class.java) {
            suppressPomMetadataWarningsFor("runtimeElements")

            pom.withXml {
                (asElement().getElementsByTagName("dependencies").item(0) as org.w3c.dom.Element).let { dependencies ->
                    dependencies.getElementsByTagName("dependency").let { dependencyList ->
                        var i = 0
                        var length = dependencyList.length
                        while (i < length) {
                            (dependencyList.item(i) as org.w3c.dom.Element).let { dependency ->
                                if ((dependency.getElementsByTagName("scope")
                                        .item(0) as org.w3c.dom.Element).textContent == "provided"
                                ) {
                                    dependencies.removeChild(dependency)
                                    i--
                                    length--
                                }
                            }
                            i++
                        }
                    }
                }
            }
        }
    }
}

