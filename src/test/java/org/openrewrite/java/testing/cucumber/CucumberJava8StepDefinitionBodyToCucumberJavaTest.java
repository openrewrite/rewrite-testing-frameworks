package org.openrewrite.java.testing.cucumber;

import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import org.junit.jupiter.api.Test;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.version;

class CucumberJava8StepDefinitionBodyToCucumberJavaTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CucumberJava8StepDefinitionBodyToCucumberJava());
        spec.parser(JavaParser.fromJavaVersion()
                .logCompilationWarningsAndErrors(true)
                .classpath("junit", "cucumber-java8", "cucumber-java"));
    }

    @Test
    void should_convert_cucumber_java8_sample_to_java_sample() {
        rewriteRun(version(java(
                """
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
                        }""",
                """
                        package com.example.app;

                        import io.cucumber.java.en.Given;
                        import io.cucumber.java.en.Then;
                        import io.cucumber.java.en.When;

                        import static org.junit.jupiter.api.Assertions.assertEquals;

                        public class CalculatorStepDefinitions {
                            private RpnCalculator calc;

                            static class RpnCalculator {
                                void push(Object string) {
                                }

                                public Double value() {
                                    return Double.NaN;
                                }
                            }

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
                        }"""),
                17));
    }

    @Test
    void should_convert_method_invocations_outside_constructor() {
        rewriteRun(version(java(
                """
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
                        }""",
                """
                        package com.example.app;

                        import io.cucumber.java.en.Given;

                        public class CalculatorStepDefinitions {
                            private int cakes = 0;

                            public CalculatorStepDefinitions() {
                                delegated();
                            }
                            
                            private void delegated() {
                            }

                            @Given("{int} cakes")
                            public void int_cakes(Integer i) {
                                cakes = i;
                            }
                        }"""),
                17));
    }

    @Test
    void should_retain_whitespace_and_comments_around_lambda_arguments() {
        rewriteRun(version(java(
                """
                        package com.example.app;

                        import io.cucumber.java8.En;

                        public class CalculatorStepDefinitions implements En {
                            public CalculatorStepDefinitions() {
                                Given("{int} plus {int}", (
                                // A
                                Integer a,
                                Integer b) -> {
                                    int c = a + b;
                                });
                            }
                        }""",
                """
                        package com.example.app;

                        import io.cucumber.java.en.Given;

                        public class CalculatorStepDefinitions {
                            @Given("{int} plus {int}")
                            public void int_plus_int(
                                // A
                                Integer a,
                                Integer b) {
                                int c = a + b;
                            }
                        }"""),
                17));
    }

    // TODO Test with non string literal argument
    // TODO Test with non lambda second argument (method invocation, method reference, etc)
    // TODO Test with non StepDefinitionBody second argument 
    // TODO Test with before/after hooks
    // TODO Test with Parameter types
    // TODO Test with DataTable
    // TODO Test with DocStringType

}
