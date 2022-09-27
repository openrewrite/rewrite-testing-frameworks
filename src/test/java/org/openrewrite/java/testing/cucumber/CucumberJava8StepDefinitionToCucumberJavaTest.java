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

import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import org.junit.jupiter.api.Test;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.version;

@Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/259")
class CucumberJava8StepDefinitionToCucumberJavaTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CucumberJava8StepDefinitionToCucumberJava());
        spec.parser(JavaParser.fromJavaVersion()
                .logCompilationWarningsAndErrors(true)
                .classpath("junit", "cucumber-java8", "cucumber-java"));
    }

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
        rewriteRun(version(java("""
                package com.example.app;

                import io.cucumber.java8.En;

                public class CalculatorStepDefinitions implements En {
                    public CalculatorStepDefinitions() {
                        Given("{int} plus {int}", (
                        // A
                        Integer a,
                        Integer b) -> {
                            // C
                            int c = a + b;
                        });
                    }
                }""", """
                package com.example.app;

                import io.cucumber.java.en.Given;

                public class CalculatorStepDefinitions {
                    @Given("{int} plus {int}")
                    public void int_plus_int(
                        // A
                        Integer a,
                        Integer b) {
                        // C
                        int c = a + b;
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
                }"""),
                17));
    }

}
