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
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

@Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/264")
class CucumberAnnotationToSuiteTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
                .scanRuntimeClasspath("org.openrewrite.java.testing.cucumber")
                .build()
                .activateRecipes("org.openrewrite.java.testing.cucumber.CucumberToJunitPlatformSuite"));
        spec.parser(JavaParser.fromJavaVersion()
                .logCompilationWarningsAndErrors(true)
                .classpath(
                        "cucumber-junit-platform-engine",
                        "junit-platform-suite-api"));
    }

@Test
void should_replace_cucumber_annotation_with_suite_with_selected_classpath_resource() {
    rewriteRun(
        mavenProject("project",
            srcTestJava(
                    java("""
                        package com.example.app;
        
                        import io.cucumber.junit.platform.engine.Cucumber;
        
                        @Cucumber
                        public class CucumberJava8Definitions {
                        }
                        """,
                        """
                        package com.example.app;
        
                        import org.junit.platform.suite.api.SelectClasspathResource;
                        import org.junit.platform.suite.api.Suite;
        
                        @Suite
                        @SelectClasspathResource("com/example/app")
                        public class CucumberJava8Definitions {
                        }
                        """)
            ),
            pomXml(
                """
                <project>
                    <groupId>org.example</groupId>
                    <artifactId>cucmber-test</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <dependencies>
                        <dependency>
                            <groupId>io.cucumber</groupId>
                            <artifactId>cucumber-junit-platform-engine</artifactId>
                            <version>6.11.0</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """,
                """
                <project>
                    <groupId>org.example</groupId>
                    <artifactId>cucmber-test</artifactId>
                    <version>1.0-SNAPSHOT</version>
                    <dependencies>
                        <dependency>
                            <groupId>io.cucumber</groupId>
                            <artifactId>cucumber-junit-platform-engine</artifactId>
                            <version>6.11.0</version>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.junit.platform</groupId>
                            <artifactId>junit-platform-suite</artifactId>
                            <version>1.9.1</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """)
    )
);
}

}
