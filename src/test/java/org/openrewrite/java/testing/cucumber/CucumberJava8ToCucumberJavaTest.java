package org.openrewrite.java.testing.cucumber;

import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import org.junit.jupiter.api.Test;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.version;

class CucumberJava8ToCucumberJavaTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CucumberJava8ToCucumberJava());
        spec.parser(JavaParser.fromJavaVersion().classpath("cucumber-java8", "cucumber-java"));
    }

    @Test
    void run() {
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
                        }""",
                """
                        package com.example.app;

                        import io.cucumber.java.en.*;

                        import static org.junit.jupiter.api.Assertions.assertEquals;

                        public class CalculatorStepDefinitions {
                            private RpnCalculator calc;

                            @Given("a calculator I just turned on")
                            public void a_calculator_I_just_turned_on() {
                                calc = new RpnCalculator();
                            }

                            @When("I add {int} and {int}")
                            public void i_add_int_and_int(int arg1, int arg2) {
                                calc.push(arg1);
                                calc.push(arg2);
                                calc.push("+");
                            }

                            @Then("the result is {int}")
                            public void the_result_is_int(double expected) {
                                assertEquals(expected, calc.value());
                            }
                        }"""),
                17));
    }

}
