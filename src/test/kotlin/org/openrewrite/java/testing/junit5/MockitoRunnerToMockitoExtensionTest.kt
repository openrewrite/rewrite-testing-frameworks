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
package org.openrewrite.java.testing.junit5

import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.openrewrite.InMemoryExecutionContext
import org.openrewrite.Issue
import org.openrewrite.SourceFile
import org.openrewrite.java.JavaParser
import org.openrewrite.java.search.FindAnnotations
import org.openrewrite.maven.MavenParser

class MockitoRunnerToMockitoExtensionTest {

    private val javaParser = JavaParser.fromJavaVersion().classpath(
            JavaParser.dependenciesFromClasspath("junit", "mockito")).build()

    private val mavenParser = MavenParser.builder().build()

    @Test
    fun replacesAnnotationAddsDependency() {
        val javaSource = javaParser.parse("""
            package org.openrewrite.java.testing.junit5;

            import org.junit.Test;
            import org.junit.runner.RunWith;
            import org.mockito.Mock;
            import org.mockito.runners.MockitoJUnitRunner;
            
            import java.util.List;
            
            @RunWith(MockitoJUnitRunner.class)
            public class MockitoRunnerTest {
            
                @Mock
                private List<Integer> list;
            
                @Test
                public void shouldDoSomething() {
                    list.add(100);
                }
            }
        """.trimIndent())[0]

        val mavenSource = mavenParser.parse("""
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.mycompany.app</groupId>
              <artifactId>my-app</artifactId>
              <version>1</version>
            </project>
        """.trimIndent())[0]

        val sources: List<SourceFile> = listOf(javaSource, mavenSource)

        val results = MockitoRunnerToMockitoExtension().doNext(AddMockitoDependency()).run(sources,
                InMemoryExecutionContext { t: Throwable? -> fail<Any>("Recipe threw an exception", t) }
                )

        assertThat(results).`as`("Recipe must make changes").isNotEmpty
        assertThat(results).hasSize(2)
        val javaResult = results.find { it.before === javaSource }
        val mavenResult = results.find { it.before === mavenSource }

        assertThat(javaResult).isNotNull
        assertThat(javaResult!!.after).isNotNull
        assertThat(javaResult.after!!.printTrimmed())
                .isEqualTo("""
            package org.openrewrite.java.testing.junit5;

            import org.junit.Test;
            import org.junit.jupiter.api.extension.ExtendWith;
            import org.mockito.Mock;
            import org.mockito.junit.jupiter.MockitoExtension;

            import java.util.List;

            @ExtendWith(MockitoExtension.class)
            public class MockitoRunnerTest {

                @Mock
                private List<Integer> list;

                @Test
                public void shouldDoSomething() {
                    list.add(100);
                }
            }
        """.trimIndent())

        assertThat(mavenResult).isNotNull
        assertThat(mavenResult!!.after).isNotNull
        assertThat(mavenResult.after!!.printTrimmed())
                .isEqualTo("""
                    <project>
                      <modelVersion>4.0.0</modelVersion>
                      <groupId>com.mycompany.app</groupId>
                      <artifactId>my-app</artifactId>
                      <version>1</version>
                      <dependencies>
                        <dependency>
                          <groupId>org.mockito</groupId>
                          <artifactId>mockito-junit-jupiter</artifactId>
                          <version>3.7.7</version>
                          <scope>test</scope>
                        </dependency>
                      </dependencies>
                    </project>
                """.trimIndent())
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/22")
    @Test
    fun leavesUnrelatedSourcesAlone() {
        val shouldBeRefactored = """
            package org.openrewrite.java.testing.junit5;
            
            import org.junit.Test;
            import org.junit.runner.RunWith;
            import org.mockito.Mock;
            import org.mockito.runners.MockitoJUnitRunner;
            
            import java.util.List;
            
            @RunWith(MockitoJUnitRunner.class)
            public class ShouldBeRefactored {
            
                @Mock
                private List<Integer> list;
            
                @Test
                public void shouldDoSomething() {
                    list.add(100);
                }
            }
        """.trimIndent()
        val shouldBeLeftAlone = """
            package org.openrewrite.java.testing.junit5;
            
            import org.junit.Test;
            import org.mockito.Mock;
            
            import java.util.List;
            
            public class ShouldBeLeftAlone {
            
                @Mock
                private List<Integer> list;
            
                @Test
                public void shouldDoSomething() {
                    list.add(100);
                }
            }
        """.trimIndent()

        val sources = javaParser.parse(shouldBeRefactored, shouldBeLeftAlone)

        val results = MockitoRunnerToMockitoExtension().run(sources,
                InMemoryExecutionContext { t: Throwable? -> fail<Any>("Recipe threw an exception", t) },
        )

        assertThat(results.size).`as`("There should be exactly one change, made to class \"ShouldBeRefactored\"").isEqualTo(1)
        assertThat(results[0].before === sources[0])

        assertThat(results[0].after!!.printTrimmed()).isEqualTo("""
            package org.openrewrite.java.testing.junit5;
            
            import org.junit.Test;
            import org.junit.jupiter.api.extension.ExtendWith;
            import org.mockito.Mock;
            import org.mockito.junit.jupiter.MockitoExtension;
            
            import java.util.List;
            
            @ExtendWith(MockitoExtension.class)
            public class ShouldBeRefactored {
            
                @Mock
                private List<Integer> list;
            
                @Test
                public void shouldDoSomething() {
                    list.add(100);
                }
            }
        """.trimIndent())

    }

}
