/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.testing.datafaker;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class JavaFakerToDataFakerTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResource("/META-INF/rewrite/datafaker.yml", "org.openrewrite.java.testing.datafaker.JavaFakerToDataFaker")
          .parser(JavaParser.fromJavaVersion().classpath("javafaker", "datafaker"));
    }

    @DocumentExample
    @Test
    void javaFakerToDataFaker() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.javafaker.Faker;
              class A {
                  void method() {
                      Faker faker = new Faker();
                      String name = faker.name().fullName();
                      String address = faker.address().fullAddress();
                      
                      String md5 = faker.crypto().md5();
                      String relationship = faker.relationships().sibling();
                  }
              }
              """,
            """
              import net.datafaker.Faker;
              class A {
                  void method() {
                      Faker faker = new Faker();
                      String name = faker.name().fullName();
                      String address = faker.address().fullAddress();
                      
                      String md5 = faker.hashing().md5();
                      String relationship = faker.relationships().sibling();
                  }
              }
              """
          )
        );
    }
}
