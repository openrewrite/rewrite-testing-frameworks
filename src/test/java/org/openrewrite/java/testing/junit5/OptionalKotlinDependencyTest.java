/*
 * Copyright 2026 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.*;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.java.JavaParser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class OptionalKotlinDependencyTest {
    @Test
    void javaSourceRecipesRunWithoutKotlinParserOrCompiler() throws Exception {
        String classpath = Arrays.stream(System.getProperty("rewrite.kotlin.executionClasspath").split(
                        Pattern.quote(File.pathSeparator)))
                .filter(entry -> !containsKotlin(Paths.get(entry)))
                .collect(Collectors.joining(File.pathSeparator));
        Path log = Files.createTempFile("rewrite-without-kotlin-", ".log");
        try {
            Process process = new ProcessBuilder(Paths.get(System.getProperty("java.home"), "bin", "java").toString(),
                    "-cp", classpath, JavaOnly.class.getName())
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new AssertionError("Java-only source migration timed out");
            }
            assertThat(process.exitValue()).withFailMessage("%s", Files.readString(log)).isZero();
        } finally {
            Files.deleteIfExists(log);
        }
    }

    private static boolean containsKotlin(Path entry) {
        String parser = "org/openrewrite/kotlin/tree/K$CompilationUnit.class";
        String compiler = "org/jetbrains/kotlin/cli/jvm/K2JVMCompiler.class";
        if (Files.isDirectory(entry)) {
            return Files.exists(entry.resolve(parser)) || Files.exists(entry.resolve(compiler));
        }
        if (Files.isRegularFile(entry) && entry.toString().endsWith(".jar")) {
            try (JarFile jar = new JarFile(entry.toFile())) {
                return jar.getJarEntry(parser) != null || jar.getJarEntry(compiler) != null;
            } catch (Exception e) {
                throw new IllegalStateException("Cannot inspect classpath entry " + entry, e);
            }
        }
        return false;
    }

    public static class JavaOnly {
        public static void main(String[] args) throws Exception {
            try {
                Class.forName("org.openrewrite.kotlin.tree.K$CompilationUnit");
                throw new AssertionError("Kotlin parser must be absent");
            } catch (ClassNotFoundException expected) {
                // Exercise optional linkage in a fresh JVM.
            }
            ExecutionContext ctx = new InMemoryExecutionContext(t -> {
                throw new AssertionError(t);
            });
            for (String name : Arrays.asList("AddMissingNested", "AddMissingTestBeforeAfterAnnotations",
                    "AssertThrowsOnLastStatement", "AssertToAssertions", "CategoryToTag", "CsvSourceToValueSource",
                    "EnclosedToNested", "EnvironmentVariables", "ExpectedExceptionToAssertThrows",
                    "ExpectedSystemExitToCatchSystemExit", "HandleExternalResourceRules", "JUnitParamsRunnerToParameterized",
                    "JUnitSoftAssertionsToSoftAssertionsExtension", "KotlinAssumeNotNullToAssumptions",
                    "KotlinJUnit5MigrationCleanup", "MigrateJUnitTestCase", "MockitoJUnitToMockitoExtension",
                    "NormalizeKotlinJUnit4Aliases", "ParameterizedRunnerToParameterized", "SystemPropertyRulesToPioneer",
                    "SystemRulesToSystemStubs", "TempDirNonFinal", "TemporaryFolderToTempDir", "TestRuleToTestInfo",
                    "UpdateMockWebServer", "UpdateTestAnnotation", "UseTestMethodOrder", "WireMockRuleToWireMockServer")) {
                ((Recipe) Class.forName("org.openrewrite.java.testing.junit5." + name).getConstructor().newInstance()).getVisitor();
            }
            List<SourceFile> sources = JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-4")
                    .build().parse(ctx, """
                            import org.junit.Test;
                            import static org.junit.Assert.assertEquals;
                            import org.junit.Rule;
                            import org.junit.rules.TemporaryFolder;
                            class Example {
                                @Rule public TemporaryFolder folder = new TemporaryFolder();
                                @Test public void test() { assertEquals(1, 1); }
                            }
                            """).toList();
            Recipe migration = new Recipe() {
                public String getDisplayName() {
                    return "Java-only JUnit source migration";
                }

                public String getDescription() {
                    return "Exercise source recipes without the optional Kotlin parser and compiler.";
                }

                public List<Recipe> getRecipeList() {
                    return Arrays.asList(new NormalizeKotlinJUnit4Aliases(), new KotlinAssumeNotNullToAssumptions(),
                            new UpdateTestAnnotation(), new AssertToAssertions(), new CategoryToTag(),
                            new TemporaryFolderToTempDir(), new TempDirNonFinal(), new KotlinJUnit5MigrationCleanup());
                }
            };
            List<Result> results = migration.run(new InMemoryLargeSourceSet(sources), ctx).getChangeset().getAllResults();
            if (results.size() != 1 || !results.get(0).getAfter().printAll().contains("org.junit.jupiter.api.Test")) {
                throw new AssertionError("Java-only source migration did not complete");
            }
            if (!results.get(0).getAfter().printAll().contains("@TempDir")) {
                throw new AssertionError("Java-only rule migration did not complete");
            }
        }
    }
}
