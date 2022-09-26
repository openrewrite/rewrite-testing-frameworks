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

                            static class RpnCalculator {
                                void push(Object string) { }
                                public Double value() { return Double.NaN; }
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
                                void push(Object string) { }
                                public Double value() { return Double.NaN; }
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

    // TODO Test with method calls not in constructor
    // TODO Test with non string literal argument
    // TODO Test with non lambda second argument (method invocation, method reference, etc)
    // TODO Test with non StepDefinitionBody second argument 
    // TODO Test with before/after hooks
    // TODO Test with Parameter types
    // TODO Test with DataTable
    // TODO Test with DocStringType

}
