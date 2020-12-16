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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.openrewrite.*
import org.openrewrite.java.JavaParser
import org.openrewrite.java.tree.J
import org.openrewrite.maven.MavenParser
import org.openrewrite.maven.tree.Maven

class MockitoRunnerToMockitoExtensionTest: RefactorVisitorTestForParser<J.CompilationUnit> {

    override val parser= JavaParser.fromJavaVersion()
        .classpath("junit", "mockito")
        .build()

    override val visitors = listOf(MockitoRunnerToMockitoExtension())

    @Test
    fun replacesAnnotationAddsDependency() {
        val mp = MavenParser.builder().build()
        val sources: List<SourceFile> = parser.parse("""
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
        """.trimIndent()) + mp.parse("""
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.mycompany.app</groupId>
              <artifactId>my-app</artifactId>
              <version>1</version>
            </project>
        """.trimIndent())

        val changes = Refactor()
                .visit(visitors)
                .fix(sources)


        val actualClass = changes.find { it.fixed is J.CompilationUnit }!!.fixed as J.CompilationUnit
        val expectedClass = """
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
        """.trimIndent()

        assertEquals(expectedClass, actualClass.printTrimmed())

        val actualPom = changes.find { it.fixed is Maven }!!.fixed as Maven
        val mockitoJunitJupiterDep = actualPom.model.dependencies.find { it.artifactId == "mockito-junit-jupiter" }
        assertNotNull(mockitoJunitJupiterDep)
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
        val expectedShouldBeRefactored = """
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
        """.trimIndent()

        val changes = Refactor()
            .visit(listOf(MockitoRunnerToMockitoExtension()))
            .fix(parser.parse(shouldBeRefactored, shouldBeLeftAlone))

        assertThat(changes.size).`as`("There should be exactly one change, made to class \"ShouldBeRefactored\"").isEqualTo(1)
        val actualShouldBeRefactored = changes.first().fixed!! as J.CompilationUnit
        assertThat(actualShouldBeRefactored.printTrimmed()).isEqualTo(expectedShouldBeRefactored)
    }
}
