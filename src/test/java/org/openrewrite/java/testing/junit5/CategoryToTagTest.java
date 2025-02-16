/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class CategoryToTagTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4"))
          .recipe(new CategoryToTag());
    }

    @Test
    void categoriesHavingJAssignmentArguments() {
        //language=java
        rewriteRun(
          java("public interface FastTests {}"),
          java("public interface SlowTests {}"),
          java(
            """
              import org.junit.experimental.categories.Category;

              @Category(value = SlowTests.class)
              public class B {

              }
              @Category(value = {SlowTests.class, FastTests.class})
              public class C {

              }
              """,
            """
              import org.junit.jupiter.api.Tag;

              @Tag("SlowTests")
              public class B {

              }

              @Tag("SlowTests")
              @Tag("FastTests")
              public class C {

              }
              """
          )
        );
    }

    @Test
    void multipleCategoriesToTags() {
        //language=java
        rewriteRun(
          java("public interface FastTests {}"),
          java("public interface SlowTests {}"),
          java(
            """
              import org.junit.experimental.categories.Category;

              @Category({FastTests.class, SlowTests.class})
              public class B {

              }
              """,
            """
              import org.junit.jupiter.api.Tag;

              @Tag("FastTests")
              @Tag("SlowTests")
              public class B {

              }
              """
          )
        );
    }

    @Test
    void qualifiedCategory() {
        //language=java
        rewriteRun(
          java("package foo; public interface FastTests {}"),
          java(
            """
              import org.junit.experimental.categories.Category;

              @Category(foo.FastTests.class)
              public class B {

              }
              """,
            """
              import org.junit.jupiter.api.Tag;

              @Tag("FastTests")
              public class B {

              }
              """
          )
        );
    }

    @Test
    void changeCategoryToTagOnClassAndMethod() {
        //language=java
        rewriteRun(
          java("public interface FastTests {}"),
          java("public interface SlowTests {}"),
          java(
            """
              import org.junit.Test;
              import org.junit.experimental.categories.Category;

              @Category(SlowTests.class)
              public class B {

                  @Category(FastTests.class)
                  @Test
                  public void b() {
                  }

                  @Test
                  public void d() {
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.junit.jupiter.api.Tag;

              @Tag("SlowTests")
              public class B {

                  @Tag("FastTests")
                  @Test
                  public void b() {
                  }

                  @Test
                  public void d() {
                  }
              }
              """
          )
        );
    }

    @Test
    void maintainAnnotationPositionAmongOtherAnnotations() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java("public interface FastTests {}"),
          java("public interface SlowTests {}"),
          java(
            """
              import lombok.Data;
              import org.junit.experimental.categories.Category;

              import java.lang.annotation.Documented;

              @Documented
              @Category({FastTests.class, SlowTests.class})
              @Data
              public class B {

              }
              """,
            """
              import lombok.Data;
              import org.junit.jupiter.api.Tag;

              import java.lang.annotation.Documented;

              @Documented
              @Tag("FastTests")
              @Tag("SlowTests")
              @Data
              public class B {

              }
              """
          )
        );
    }

    @Test
    void removesDefunctImport() {
        //language=java
        rewriteRun(

          java(
            """
              package a;

              public interface FastTests {}
              """
          ),
          java(
            """
              package b;

              import a.FastTests;
              import org.junit.experimental.categories.Category;

              @Category({FastTests.class})
              public class B {
              }
              """,
            """
              package b;

              import org.junit.jupiter.api.Tag;

              @Tag("FastTests")
              public class B {
              }
              """
          )
        );
    }
}
