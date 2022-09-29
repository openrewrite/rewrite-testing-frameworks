/*
 * Copyright 2022 the original author or authors.
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
package org.openrewrite.java.testing.cucumber;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.version;

@Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/259")
class CucumberJava8ToCucumberJavaTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe("/META-INF/rewrite/cucumber.yml", "org.openrewrite.java.testing.cucumber.CucumberJava8ToJava");
        spec.parser(JavaParser.fromJavaVersion()
                .logCompilationWarningsAndErrors(true)
                .classpath("junit-jupiter-api", "cucumber-java", "cucumber-java8"));
    }

    @Test
    void should_convert_cucumber_java8_hooks_and_steps() {
        rewriteRun(version(java("""
                package com.example.app;

                import io.cucumber.java8.En;
                import io.cucumber.java8.Scenario;
                import io.cucumber.java8.Status;

                import static org.junit.jupiter.api.Assertions.assertEquals;

                public class CucumberJava8Definitions implements En {

                    private int a;

                    public CucumberJava8Definitions() {

                        Before(() -> {
                            a = 0;
                        });
                        When("I add {int}", (Integer b) -> {
                            a += b;
                        });
                        Then("I expect {int}", (Integer c) -> assertEquals(c, a));

                        After((Scenario scn) -> {
                            if (scn.getStatus() == Status.FAILED) {
                                scn.log("failed");
                            }
                        });

                    }

                }""", """
                package com.example.app;

                import io.cucumber.java.After;
                import io.cucumber.java.Before;
                import io.cucumber.java.en.Then;
                import io.cucumber.java.en.When;
                import io.cucumber.java.Scenario;
                import io.cucumber.java.Status;

                import static org.junit.jupiter.api.Assertions.assertEquals;

                public class CucumberJava8Definitions {

                    private int a;

                    @Before
                    public void before() {
                        a = 0;
                    }

                    @After
                    public void after(io.cucumber.java.Scenario scn) {
                        if (scn.getStatus() == Status.FAILED) {
                            scn.log("failed");
                        }
                    }

                    @When("I add {int}")
                    public void i_add_int(Integer b) {
                        a += b;
                    }

                    @Then("I expect {int}")
                    public void i_expect_int(Integer c) {
                        assertEquals(c, a);
                    }

                }"""),
                17));
    }

    @Nested
    class StepMigration {

        @Test
        void should_convert_cucumber_java8_sample_to_java_sample() {
            rewriteRun(version(java("""
                    package com.example.app;

                    import io.cucumber.java8.En;

                    import static org.junit.jupiter.api.Assertions.assertEquals;

                    public class CalculatorStepDefinitions implements En {
                        private RpnCalculator calc;

                        public CalculatorStepDefinitions() {
                            Given("a calculator I just turned on", () -> {
                                calc = new RpnCalculator();
                            });

                            When("I add {int} and {int}", (Integer arg1, Integer arg2) -> {
                                calc.push(arg1);
                                calc.push(arg2);
                                calc.push("+");
                            });

                            Then("the result is {double}", (Double expected) -> assertEquals(expected, calc.value()));
                        }

                        static class RpnCalculator {
                            void push(Object string) {
                            }

                            public Double value() {
                                return Double.NaN;
                            }
                        }
                    }""", """
                    package com.example.app;

                    import io.cucumber.java.en.Given;
                    import io.cucumber.java.en.Then;
                    import io.cucumber.java.en.When;

                    import static org.junit.jupiter.api.Assertions.assertEquals;

                    public class CalculatorStepDefinitions {
                        private RpnCalculator calc;

                        @Given("a calculator I just turned on")
                        public void a_calculator_i_just_turned_on() {
                            calc = new RpnCalculator();
                        }

                        @When("I add {int} and {int}")
                        public void i_add_int_and_int(Integer arg1, Integer arg2) {
                            calc.push(arg1);
                            calc.push(arg2);
                            calc.push("+");
                        }

                        @Then("the result is {double}")
                        public void the_result_is_double(Double expected) {
                            assertEquals(expected, calc.value());
                        }

                        static class RpnCalculator {
                            void push(Object string) {
                            }

                            public Double value() {
                                return Double.NaN;
                            }
                        }
                    }"""),
                    17));
        }

        @Test
        void should_convert_method_invocations_outside_constructor() {
            rewriteRun(version(java("""
                    package com.example.app;

                    import io.cucumber.java8.En;

                    public class CalculatorStepDefinitions implements En {
                        private int cakes = 0;

                        public CalculatorStepDefinitions() {
                            delegated();
                        }

                        private void delegated() {
                            Given("{int} cakes", (Integer i) -> {
                                cakes = i;
                            });
                        }
                    }""", """
                    package com.example.app;

                    import io.cucumber.java.en.Given;

                    public class CalculatorStepDefinitions {
                        private int cakes = 0;

                        public CalculatorStepDefinitions() {
                            delegated();
                        }

                        @Given("{int} cakes")
                        public void int_cakes(Integer i) {
                            cakes = i;
                        }

                        private void delegated() {
                        }
                    }"""),
                    17));
        }

        @Test
        void should_retain_whitespace_and_comments_in_lambda_body() {
            rewriteRun(version(java("""
                    package com.example.app;

                    import io.cucumber.java8.En;

                    public class CalculatorStepDefinitions implements En {
                        public CalculatorStepDefinitions() {
                            Given("{int} plus {int}", (Integer a, Integer b) -> {

                                // Lambda body comment
                                int c = a + b;
                            });
                        }
                    }""", """
                    package com.example.app;

                    import io.cucumber.java.en.Given;

                    public class CalculatorStepDefinitions {

                        @Given("{int} plus {int}")
                        public void int_plus_int(Integer a, Integer b) {

                            // Lambda body comment
                            int c = a + b;
                        }
                    }"""),
                    17));
        }

        @Test
        void should_retain_throws_exception() {
            rewriteRun(version(java("""
                    package com.example.app;

                    import io.cucumber.java8.En;

                    public class CalculatorStepDefinitions implements En {
                        public CalculatorStepDefinitions() {
                            Given("a thrown exception", () -> {
                                throw new Exception();
                            });
                        }
                    }""", """
                    package com.example.app;

                    import io.cucumber.java.en.Given;

                    public class CalculatorStepDefinitions {

                        @Given("a thrown exception")
                        public void a_thrown_exception() throws Exception {
                            throw new Exception();
                        }
                    }"""),
                    17));
        }

        @Test
        void should_not_replace_when_not_using_string_constant() {
            rewriteRun(version(java("""
                    package com.example.app;

                    import io.cucumber.java8.En;

                    public class CalculatorStepDefinitions implements En {
                        public CalculatorStepDefinitions() {
                            String expression = "{int} plus {int}";
                            Given(expression, (Integer a, Integer b) -> {
                                int c = a + b;
                            });
                        }
                    }""", """
                    package com.example.app;

                    import io.cucumber.java.En;

                    public class CalculatorStepDefinitions implements En {
                        public CalculatorStepDefinitions() {
                            String expression = "{int} plus {int}";
                            /*~~(TODO Migrate manually)~~>*/Given(expression, (Integer a, Integer b) -> {
                                int c = a + b;
                            });
                        }
                    }"""),
                    17));
        }

        @Test
        void should_not_replace_when_using_string_constant() {
            // For simplicity we only replace when using a String literal for now
            rewriteRun(version(java("""
                    package com.example.app;

                    import io.cucumber.java8.En;

                    public class CalculatorStepDefinitions implements En {
                        private static final String expression = "{int} plus {int}";
                        public CalculatorStepDefinitions() {
                            Given(expression, (Integer a, Integer b) -> {
                                int c = a + b;
                            });
                        }
                    }""", """
                    package com.example.app;

                    import io.cucumber.java.En;

                    public class CalculatorStepDefinitions implements En {
                        private static final String expression = "{int} plus {int}";
                        public CalculatorStepDefinitions() {
                            /*~~(TODO Migrate manually)~~>*/Given(expression, (Integer a, Integer b) -> {
                                int c = a + b;
                            });
                        }
                    }"""),
                    17));
        }

        @Test
        void should_not_replace_method_reference() {
            // For simplicity we only replace when using lambda for now
            rewriteRun(version(java("""
                    package com.example.app;

                    import io.cucumber.java8.En;

                    public class CalculatorStepDefinitions implements En {
                        public CalculatorStepDefinitions() {
                            Given("{int} plus {int}", Integer::sum);
                        }
                    }""", """
                    package com.example.app;

                    import io.cucumber.java.En;

                    public class CalculatorStepDefinitions implements En {
                        public CalculatorStepDefinitions() {
                            /*~~(TODO Migrate manually)~~>*/Given("{int} plus {int}", Integer::sum);
                        }
                    }"""),
                    17));
        }

    }

    @Nested
    class HookMigration {

        @Test
        void should_convert_cucumber_java8_hooks() {
            rewriteRun(version(java("""
                    package com.example.app;

                    import io.cucumber.java8.En;
                    import io.cucumber.java8.Scenario;
                    import io.cucumber.java8.Status;

                    public class HookStepDefinitions implements En {

                        private int a;

                        public HookStepDefinitions() {
                            Before(() -> {
                                a = 0;
                            });

                            Before("abc", () -> a = 0);

                            Before("not abc", 0, () -> {
                                a = 0;
                            });

                            Before(1, () -> {
                                a = 0;
                            });

                            Before(2, scn -> {
                                a = 0;
                            });

                            After((Scenario scn) -> {
                                if (scn.getStatus() == Status.FAILED) {
                                    scn.log("after scenario");
                                }
                            });

                            After("abc", (Scenario scn) -> {
                                scn.log("after scenario");
                            });

                            AfterStep(scn -> {
                                a = 0;
                            });
                        }

                    }""", """
                    package com.example.app;

                    import io.cucumber.java.After;
                    import io.cucumber.java.AfterStep;
                    import io.cucumber.java.Before;
                    import io.cucumber.java.Scenario;
                    import io.cucumber.java.Status;

                    public class HookStepDefinitions {

                        private int a;

                        @Before
                        public void before() {
                            a = 0;
                        }

                        @Before("abc")
                        public void before_tag_abc() {
                            a = 0;
                        }

                        @Before(order = 0, value = "not abc")
                        public void before_tag_not_abc_order_0() {
                            a = 0;
                        }

                        @Before(order = 1)
                        public void before_order_1() {
                            a = 0;
                        }

                        @Before(order = 2)
                        public void before_order_2(io.cucumber.java.Scenario scn) {
                            a = 0;
                        }

                        @After
                        public void after(io.cucumber.java.Scenario scn) {
                            if (scn.getStatus() == Status.FAILED) {
                                scn.log("after scenario");
                            }
                        }

                        @After("abc")
                        public void after_tag_abc(io.cucumber.java.Scenario scn) {
                            scn.log("after scenario");
                        }

                        @AfterStep
                        public void afterStep(io.cucumber.java.Scenario scn) {
                            a = 0;
                        }

                    }"""),
                    17));
        }

        @Test
        void should_not_convert_anonymous_classes() {
            // For simplicity anonymous classes are not converted for now; it's not how cucumber-java8 usage was
            // intended
            rewriteRun(spec -> spec.cycles(2), version(java("""
                    package com.example.app;

                    import io.cucumber.java8.En;
                    import io.cucumber.java8.HookBody;
                    import io.cucumber.java8.HookNoArgsBody;
                    import io.cucumber.java8.Scenario;

                    public class HookStepDefinitions implements En {

                        private int a;

                        public HookStepDefinitions() {
                            Before(new HookNoArgsBody() {
                                @Override
                                public void accept() {
                                    a = 0;
                                }
                            });

                            Before(new HookBody() {
                                @Override
                                public void accept(Scenario scenario) {
                                    a = 0;
                                }
                            });
                        }

                    }""", """
                    package com.example.app;

                    import io.cucumber.java.En;
                    import io.cucumber.java.HookBody;
                    import io.cucumber.java.HookNoArgsBody;
                    import io.cucumber.java.Scenario;

                    public class HookStepDefinitions implements En {

                        private int a;

                        public HookStepDefinitions() {
                            /*~~(TODO Migrate manually)~~>*/Before(new HookNoArgsBody() {
                                @Override
                                public void accept() {
                                    a = 0;
                                }
                            });

                            /*~~(TODO Migrate manually)~~>*/Before(new HookBody() {
                                @Override
                                public void accept(Scenario scenario) {
                                    a = 0;
                                }
                            });
                        }

                    }"""),
                    17));
        }

        @Test
        void should_not_convert_method_reference() {
            // For simplicity anonymous classes are not converted for now; it's not how cucumber-java8 usage was
            // intended
            rewriteRun(version(java("""
                    package com.example.app;

                    import io.cucumber.java8.En;

                    public class HookStepDefinitions implements En {

                        private int a;

                        public HookStepDefinitions() {
                            Before(this::connect);
                        }

                        private void connect() {
                        }

                    }""", """
                    package com.example.app;

                    import io.cucumber.java.En;

                    public class HookStepDefinitions implements En {

                        private int a;

                        public HookStepDefinitions() {
                            /*~~(TODO Migrate manually)~~>*/Before(this::connect);
                        }

                        private void connect() {
                        }

                    }"""),
                    17));
        }
    }
}
