/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.benchmarks.java;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

@State(Scope.Benchmark)
public class JavaCompilationUnitState {
    List<J.CompilationUnit> sourceFiles;

    @Setup(Level.Trial)
    public void setup() throws URISyntaxException {
        Path rewriteRoot = Paths.get(JavaCompilationUnitState.class.getResource("./")
                .toURI()).resolve("../../../../../../../").normalize();

        List<Path> inputs = Arrays.asList(
                rewriteRoot.resolve("src/main/java/org/openrewrite/java/testing/junit5/AssertToAssertions.java"),
                rewriteRoot.resolve("src/main/java/org/openrewrite/java/testing/junit5/CategoryToTag.java"),
                rewriteRoot.resolve("src/main/java/org/openrewrite/java/testing/junit5/CleanupJUnitImports.java"),
                rewriteRoot.resolve("src/main/java/org/openrewrite/java/testing/junit5/ExpectedExceptionToAssertThrows.java"),
                rewriteRoot.resolve("src/main/java/org/openrewrite/java/testing/junit5/ParameterizedRunnerToParameterized.java"),
                rewriteRoot.resolve("src/main/java/org/openrewrite/java/testing/junit5/RemoveObsoleteRunners.java"),
                rewriteRoot.resolve("src/main/java/org/openrewrite/java/testing/junit5/RunnerToExtension.java"),
                rewriteRoot.resolve("src/main/java/org/openrewrite/java/testing/junit5/TemporaryFolderToTempDir.java"),
                rewriteRoot.resolve("src/main/java/org/openrewrite/java/testing/junit5/UpdateBeforeAfterAnnotations.java"),
                rewriteRoot.resolve("src/main/java/org/openrewrite/java/testing/junit5/UpdateTestAnnotation.java"),
                rewriteRoot.resolve("src/main/java/org/openrewrite/java/testing/junit5/UseTestMethodOrder.java"),
                rewriteRoot.resolve("src/before/java/org/openrewrite/java/testing/junit5/ExampleJunitTestClass.java"),
                rewriteRoot.resolve("src/main/java/org/openrewrite/java/testing/assertj/JUnitAssertArrayEqualsToAssertThat.java"),
                rewriteRoot.resolve("src/main/java/org/openrewrite/java/testing/mockito/CleanupMockitoImports.java")
        );

        sourceFiles = JavaParser.fromJavaVersion()
                .classpath("lombok", "jackson-annotations", "micrometer-core", "slf4j-api", "mockito-all", "junit", "rewrite-core", "rewrite-java")
                // .logCompilationWarningsAndErrors(true)
                .build()
                .parse(inputs, null, new InMemoryExecutionContext(Throwable::printStackTrace));
    }

    @TearDown(Level.Trial)
    public void tearDown(Blackhole hole) {
        hole.consume(sourceFiles.size());
    }

    public List<J.CompilationUnit> getSourceFiles() {
        return sourceFiles;
    }
}
