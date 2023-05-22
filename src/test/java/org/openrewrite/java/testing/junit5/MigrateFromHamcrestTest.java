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
