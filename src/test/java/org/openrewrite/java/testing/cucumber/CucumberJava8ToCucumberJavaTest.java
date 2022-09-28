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
                .classpath("cucumber-java", "cucumber-java8"));
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
                                scn.log("failed: " + a);
                            }
                        });

                    }

                }
                """, """
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
                    public void after(Scenario scn) {
                        if (scn.getStatus() == Status.FAILED) {
                            scn.log("failed: " + a);
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

}
