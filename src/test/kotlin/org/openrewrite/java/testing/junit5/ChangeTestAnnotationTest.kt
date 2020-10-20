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

import org.junit.jupiter.api.Test
import org.openrewrite.Parser
import org.openrewrite.RefactorVisitor
import org.openrewrite.RefactorVisitorTestForParser
import org.openrewrite.java.JavaParser
import org.openrewrite.java.tree.J

class ChangeTestAnnotationTest: RefactorVisitorTestForParser<J.CompilationUnit> {
    override val parser: Parser<J.CompilationUnit> = JavaParser.fromJavaVersion()
            .classpath("junit")
            .build()

    override val visitors: Iterable<RefactorVisitor<*>> = listOf(ChangeTestAnnotation())

    @Test
    fun assertThrowsSingleLine() = assertRefactored(
            before = """
                import org.junit.Test;
                
                public class A {
                    @Test(expected = IllegalArgumentException.class)
                    public void test() {
                        throw new IllegalArgumentException("boom");
                    }
                }
            """.trimIndent(),
            after = """
                import org.junit.jupiter.api.Test;
                
                import static org.junit.jupiter.api.Assertions.assertThrows;
                
                public class A {
                    @Test
                    public void test() {
                        assertThrows(IllegalArgumentException.class, () -> throw new IllegalArgumentException("boom"));
                    }
                }
            """.trimIndent()
    )
}
