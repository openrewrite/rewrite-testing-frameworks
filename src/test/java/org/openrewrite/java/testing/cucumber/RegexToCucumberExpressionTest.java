package org.openrewrite.java.testing.cucumber;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.version;

@Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/264")
class RegexToCucumberExpressionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RegexToCucumberExpression());
        spec.parser(JavaParser.fromJavaVersion()
                .logCompilationWarningsAndErrors(true)
                .classpath("junit-jupiter-api", "cucumber-java"));
    }

    @Test
    void regex_to_cucumber_expression() {
        rewriteRun(version(java("""
                package com.example.app;

                import io.cucumber.java.Before;
                import io.cucumber.java.en.Given;
                import io.cucumber.java.en.Then;

                import static org.junit.jupiter.api.Assertions.assertEquals;

                public class CucumberJava8Definitions {

                    private int a;

                    @Before
                    public void before() {
                        a = 0;
                    }

                    @Given("^five cukes$")
                    public void five_cukes() {
                        a = 5;
                    }

                    @Then("^I expect (\\\\d+)$")
                    public void i_expect_int(Integer c) {
                        assertEquals(c, a);
                    }

                }""", """
                package com.example.app;

                import io.cucumber.java.Before;
                import io.cucumber.java.en.Given;
                import io.cucumber.java.en.Then;

                import static org.junit.jupiter.api.Assertions.assertEquals;

                public class CucumberJava8Definitions {

                    private int a;

                    @Before
                    public void before() {
                        a = 0;
                    }

                    @Given("five cukes")
                    public void five_cukes() {
                        a = 5;
                    }

                    @Then("^I expect (\\\\d+)$")
                    public void i_expect_int(Integer c) {
                        assertEquals(c, a);
                    }

                }"""),
                17));
    }

    @Nested
    @DisplayName("should convert")
    class ShouldConvert {

        @Test
        void only_leading_anchor() {
            rewriteRun(version(java("""
                    package com.example.app;

                    import io.cucumber.java.en.Given;

                    public class CucumberJava8Definitions {
                        @Given("^five cukes")
                        public void five_cukes() {
                        }
                    }""", """
                    package com.example.app;

                    import io.cucumber.java.en.Given;

                    public class CucumberJava8Definitions {
                        @Given("five cukes")
                        public void five_cukes() {
                        }
                    }"""),
                    17));
        }

        @Test
        void only_trailing_anchor() {
            rewriteRun(version(java("""
                    package com.example.app;

                    import io.cucumber.java.en.Given;

                    public class CucumberJava8Definitions {
                        @Given("five cukes$")
                        public void five_cukes() {
                        }
                    }""", """
                    package com.example.app;

                    import io.cucumber.java.en.Given;

                    public class CucumberJava8Definitions {
                        @Given("five cukes")
                        public void five_cukes() {
                        }
                    }"""),
                    17));
        }

        @Test
        void forward_slashes() {
            rewriteRun(version(java("""
                    package com.example.app;

                    import io.cucumber.java.en.Given;

                    public class CucumberJava8Definitions {
                        @Given("/five cukes/")
                        public void five_cukes() {
                        }
                    }""", """
                    package com.example.app;

                    import io.cucumber.java.en.Given;

                    public class CucumberJava8Definitions {
                        @Given("five cukes")
                        public void five_cukes() {
                        }
                    }"""),
                    17));
        }

    }

    @Nested
    @DisplayName("should not convert")
    class ShouldNotConvert {

        @Test
        @Disabled("non-standard matcher would need custom ParameterType which is out scope for now")
        void unrecognized_capturing_groups() {
            rewriteRun(version(java("""
                    package com.example.app;

                    import io.cucumber.java.en.Given;

                    public class CucumberJava8Definitions {
                        @Given("^some (foo|bar)$")
                        public void five_cukes() {
                        }
                    }"""),
                    17));
        }

        @Test
        @Disabled("needs method arguments to convert")
        void integer_matchers() {
            rewriteRun(version(java("""
                    package com.example.app;

                    import io.cucumber.java.en.Given;

                    public class CucumberJava8Definitions {
                        @Given("^(\\d+) cukes$")
                        public void int_cukes(int cukes) {
                        }
                    }""", """
                    package com.example.app;

                    import io.cucumber.java.en.Given;

                    public class CucumberJava8Definitions {
                        @Given("{int} cukes")
                        public void int_cukes(int cukes) {
                        }
                    }"""),
                    17));
        }

    }

}
