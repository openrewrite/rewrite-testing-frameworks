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
class CucumberJava8HookDefinitionToCucumberJavaTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CucumberJava8HookDefinitionToCucumberJava());
        spec.parser(JavaParser.fromJavaVersion()
                .logCompilationWarningsAndErrors(true)
                .classpath("junit", "cucumber-java8", "cucumber-java"));
    }

    @Test
    void should_convert_cucumber_java8_hooks() {
        rewriteRun(version(java("""
                package com.example.app;

                import io.cucumber.java8.En;
                import io.cucumber.java8.Scenario;

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
                            scn.log("after scenario");
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
                    public void before_order_2(Scenario scn) {
                        a = 0;
                    }

                    @After
                    public void after(Scenario scn) {
                        scn.log("after scenario");
                    }

                    @After("abc")
                    public void after_tag_abc(Scenario scn) {
                        scn.log("after scenario");
                    }

                    @AfterStep
                    public void afterStep(Scenario scn) {
                        a = 0;
                    }

                }"""),
                17));
    }

    @Test
    void should_not_convert_anonymous_classes() {
        // For simplicity anonymous classes are not converted for now; it's not how cucumber-java8 usage was intended
        rewriteRun(version(java("""
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
                            public void accept() throws Throwable {
                                a = 0;
                            }
                        });

                        Before(new HookBody() {
                            @Override
                            public void accept(Scenario scenario) throws Throwable {
                                a = 0;
                            }
                        });
                    }

                }""","""
                package com.example.app;

                import io.cucumber.java8.En;
                import io.cucumber.java8.HookBody;
                import io.cucumber.java8.HookNoArgsBody;
                import io.cucumber.java8.Scenario;

                public class HookStepDefinitions implements En {

                    private int a;

                    public HookStepDefinitions() {
                        /*~~(TODO Migrate manually)~~>*/Before(new HookNoArgsBody() {
                            @Override
                            public void accept() throws Throwable {
                                a = 0;
                            }
                        });

                        /*~~(TODO Migrate manually)~~>*/Before(new HookBody() {
                            @Override
                            public void accept(Scenario scenario) throws Throwable {
                                a = 0;
                            }
                        });
                    }

                }"""),
                17));
    }

    @Test
    void should_not_convert_method_reference() {
        // For simplicity anonymous classes are not converted for now; it's not how cucumber-java8 usage was intended
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

                }""","""
                package com.example.app;

                import io.cucumber.java8.En;

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
