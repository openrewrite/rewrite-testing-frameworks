/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.testing.easymock;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RemoveExtendsEasyMockSupportTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "easymock-5.4.0"))
          .recipe(new RemoveExtendsEasyMockSupport());
    }

    @Test
    @DocumentExample
    public void shouldRemoveEasyMockSupportParentClass() {
        rewriteRun(
          //language=java
          java(
            """
            import org.easymock.EasyMockSupport;

            public class Test extends EasyMockSupport {
            }
            """,
            """
            public class Test {
            }
            """
          )
        );
    }

    @Test
    void shouldRemoveForInnerClassesEasyMockSupportParentClass() {
        rewriteRun(
          //language=java
          java(
            """
            import org.easymock.EasyMockSupport;

            public class Test {
                class InnerTest extends EasyMockSupport {
                    class InnerInnerTest extends EasyMockSupport {}
                }
            }""",
            """
            public class Test {
                class InnerTest {
                    class InnerInnerTest {}
                }
            }
            """
          )
        );
    }

    @Test
    void shouldLeaveClassesWithoutEasyMockSupportAlone() {
        rewriteRun(
          //language=java
          java(
            """
            public class Test {
            }
            """
          )
        );
    }

    @Test
    void shouldLeaveClassesWithDifferentExtendsAlone() {
        rewriteRun(
          //language=java
          java(
            """
            import javax.management.monitor.Monitor;

            public class Test extends Monitor {
            }
            """
          )
        );
    }
}
