/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.testing.assertj


import org.junit.jupiter.api.Test
import org.openrewrite.config.Environment
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class StaticImportsTest : JavaRecipeTest {
    @Test
    fun useAssertionsStaticImport() = assertChanged(
        parser = JavaParser.fromJavaVersion()
                .classpath("assertj-core")
                .build(),
        recipe = Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.testing.junit5")
            .build()
            .activateRecipes("org.openrewrite.java.testing.assertj.StaticImports"),
        before = """
            import java.util.List;
            import org.assertj.core.api.AssertionsForClassTypes;
            import org.assertj.core.api.AssertionsForInterfaceTypes;
            import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
            import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

            public class Test {
                List<String> exampleList;
                void method() {
                    AssertionsForInterfaceTypes.assertThat(exampleList).hasSize(0);
                    AssertionsForClassTypes.assertThat(true).isTrue();
                    assertThat(true).isTrue();
                    assertThat(exampleList).hasSize(0);
                }
            }
        """,
        after = """
            import java.util.List;
            
            import static org.assertj.core.api.Assertions.assertThat;

            public class Test {
                List<String> exampleList;
                void method() {
                    assertThat(exampleList).hasSize(0);
                    assertThat(true).isTrue();
                    assertThat(true).isTrue();
                    assertThat(exampleList).hasSize(0);
                }
            }
        """
    )
}
