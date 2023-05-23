/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class MigrateFromHamcrestTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4.13.+", "hamcrest-2.2"))
          .recipe(new MigrateFromHamcrest());
    }

    @Test
    public void test() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.Matcher;
                          
              public class BiscuitTest {
                  @Test
                  public void testEquals() {
                      Biscuit theBiscuit = new Biscuit("Ginger");
                      Biscuit myBiscuit = new Biscuit("Ginger");
                      assertThat(theBiscuit, equalTo(myBiscuit));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import static org.junit.jupiter.api.Assertions.assertEquals;
                          
              public class BiscuitTest {
                  @Test\s
                  public void testEquals() {\s
                      Biscuit theBiscuit = new Biscuit("Ginger");\s
                      Biscuit myBiscuit = new Biscuit("Ginger");\s
                      assertEquals(theBiscuit, myBiscuit);\s
                  }\s
              }
              """
          ));
    }
}
