/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.java.testing.hamcrest;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.Collections;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static org.openrewrite.java.Assertions.java;

@Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/1211")
class HamcrestClassOverloadTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new HamcrestIsMatcherToAssertJ())
          .parser(JavaParser.fromJavaVersion().classpath(
            "hamcrest-core-1.3", "hamcrest-library-1.3", "assertj-core"));
    }

    @Test
    void retainLegacyClassMatcher() {
        rewriteRun(
          java(
            """
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.CoreMatchers.is;

              class Example {
                  void check(String value) {
                      assertThat(value, is(String.class));
                  }
              }
              """
          )
        );
    }

    @Test
    void retainLegacyArrayClassMatcher() {
        rewriteRun(
          java(
            """
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.CoreMatchers.is;

              class Example {
                  void check(String[] value) {
                      assertThat(value, is(String[].class));
                  }
              }
              """
          )
        );
    }

    @Test
    void retainLegacyPropertyClassMatcher() {
        rewriteRun(
          spec -> spec.recipe(new HamcrestHasPropertyToAssertJ()),
          java(
            """
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.hasProperty;
              import static org.hamcrest.Matchers.is;

              class Example {
                  public String getValue() {
                      return "value";
                  }

                  void check() {
                      assertThat(this, hasProperty("value", is(String.class)));
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateLegacyExplicitObjectEquality() {
        rewriteRun(
          java(
            """
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.CoreMatchers.is;

              class Example {
                  void check() {
                      assertThat(String.class, is((Object) String.class));
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Example {
                  void check() {
                      assertThat(String.class).isEqualTo((Object) String.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateModernClassValueEquality() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "hamcrest-3", "assertj-core-3")),
          java(
            """
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.CoreMatchers.is;

              class Example {
                  void check() {
                      assertThat(String.class, is(String.class));
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Example {
                  void check() {
                      assertThat(String.class).isEqualTo(String.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void retainLegacyMatcherForClassObjects() {
        rewriteRun(
          java(
            """
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.CoreMatchers.is;

              class Example {
                  void check() {
                      assertThat(String.class, is(Class.class));
                  }
              }
              """
          )
        );
    }

    @Test
    void retainClassMatcherWhenDeclarationsAreUnavailable() {
        rewriteRun(
          java(
            """
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.CoreMatchers.is;

              class Example {
                  void check(String value) {
                      assertThat(value, is(String.class));
                  }
              }
              """,
            spec -> spec.mapBeforeRecipe(HamcrestClassOverloadTest::withoutMatcherDeclarations)
          )
        );
    }

    @Test
    void migrateModernClassValueWhenDeclarationsAreUnavailable() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "hamcrest-3", "assertj-core-3")),
          java(
            """
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.CoreMatchers.is;

              class Example {
                  void check() {
                      assertThat(String.class, is(String.class));
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Example {
                  void check() {
                      assertThat(String.class).isEqualTo(String.class);
                  }
              }
              """,
            spec -> spec.mapBeforeRecipe(HamcrestClassOverloadTest::withoutMatcherDeclarations)
          )
        );
    }

    @Test
    void retainLegacyMatcherWithPartialDeclarations() {
        rewriteRun(
          java(
            """
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.CoreMatchers.is;

              class Example {
                  void check(String value) {
                      assertThat(value, is(String.class));
                  }
              }
              """,
            spec -> spec.mapBeforeRecipe(HamcrestClassOverloadTest::withoutClassOverloadDeclaration)
          )
        );
    }

    @Test
    void retainLegacyPropertyMatcherWithPartialDeclarations() {
        rewriteRun(
          spec -> spec.recipe(new HamcrestHasPropertyToAssertJ()),
          java(
            """
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.hasProperty;
              import static org.hamcrest.Matchers.is;

              class Example {
                  public String getValue() {
                      return "value";
                  }

                  void check() {
                      assertThat(this, hasProperty("value", is(String.class)));
                  }
              }
              """,
            spec -> spec.mapBeforeRecipe(HamcrestClassOverloadTest::withoutClassOverloadDeclaration)
          )
        );
    }

    @Test
    void retainLegacyMatcherWithUnknownParameterType() {
        rewriteRun(
          java(
            """
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.CoreMatchers.is;

              class Example {
                  void check(String value) {
                      assertThat(value, is(String.class));
                  }
              }
              """,
            spec -> spec.mapBeforeRecipe(cu -> mutateMatcherMethods(cu, method ->
              method.withParameterTypes(Collections.singletonList(JavaType.Unknown.getInstance()))))
          )
        );
    }

    @Test
    void retainModernClassValueWhenMatcherValueTypeIsUnavailable() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "hamcrest-3", "assertj-core-3")),
          java(
            """
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.CoreMatchers.is;

              class Example {
                  void check() {
                      assertThat(String.class, is(String.class));
                  }
              }
              """,
            spec -> spec.mapBeforeRecipe(cu -> mutateMatcherMethods(cu, method ->
              method.withReturnType(((JavaType.Parameterized) method.getReturnType()).getType())))
          )
        );
    }

    private static J.CompilationUnit withoutMatcherDeclarations(J.CompilationUnit cu) {
        return mutateMatcherMethods(cu, method -> method.withDeclaringType(
          matcherClass(method).withMethods(Collections.emptyList())));
    }

    @Test
    void retainLegacyClassObjectMatcherWithErasedParameter() {
        rewriteRun(
          java(
            """
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.CoreMatchers.is;

              class Example {
                  void check() {
                      assertThat(String.class, is(Class.class));
                  }
              }
              """,
            spec -> spec.mapBeforeRecipe(HamcrestClassOverloadTest::withErasedClassParameter)
          )
        );
    }

    @Test
    void retainLegacyPropertyClassObjectMatcherWithErasedParameter() {
        rewriteRun(
          spec -> spec.recipe(new HamcrestHasPropertyToAssertJ()),
          java(
            """
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.hasProperty;
              import static org.hamcrest.Matchers.is;

              class Example {
                  public Class<?> getType() {
                      return String.class;
                  }

                  void check() {
                      assertThat(this, hasProperty("type", is(Class.class)));
                  }
              }
              """,
            spec -> spec.mapBeforeRecipe(HamcrestClassOverloadTest::withErasedClassParameter)
          )
        );
    }

    @Test
    void retainLegacyRawClassVariableMatcher() {
        rewriteRun(
          java(
            """
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.CoreMatchers.is;

              class Example {
                  void check() {
                      Class actual = String.class;
                      Class expected = Class.class;
                      assertThat(actual, is(expected));
                  }
              }
              """
          )
        );
    }

    @Test
    void retainLegacyGenericClassVariableMatcher() {
        rewriteRun(
          java(
            """
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.is;

              class Example {
                  void check() {
                      Class<String> expected = String.class;
                      assertThat("value", is(expected));
                  }
              }
              """
          )
        );
    }

    private static J.CompilationUnit withErasedClassParameter(J.CompilationUnit cu) {
        return mutateMatcherMethods(cu, method -> {
            JavaType parameter = method.getParameterTypes().get(0);
            if (!(parameter instanceof JavaType.Parameterized)) {
                throw new IllegalStateException("The fixture must have a parameterized Class parameter");
            }
            return method.withParameterTypes(Collections.singletonList(((JavaType.Parameterized) parameter).getType()));
        });
    }

    private static J.CompilationUnit withoutClassOverloadDeclaration(J.CompilationUnit cu) {
        MethodMatcher classOverload = new MethodMatcher("org.hamcrest.*Matchers is(java.lang.Class)");
        return mutateMatcherMethods(cu, method -> {
            JavaType.Class declaring = matcherClass(method);
            if (declaring.getMethods().stream().noneMatch(classOverload::matches)) {
                throw new IllegalStateException("The fixture must have the legacy Class overload");
            }
            return method.withDeclaringType(declaring.withMethods(declaring.getMethods().stream()
              .filter(declaration -> !classOverload.matches(declaration)).collect(Collectors.toList())));
        });
    }

    private static JavaType.Class matcherClass(JavaType.Method method) {
        JavaType.Class declaring = TypeUtils.asClass(method.getDeclaringType());
        if (declaring == null) {
            throw new IllegalStateException("The fixture must have an attributed matcher class");
        }
        return declaring;
    }

    private static J.CompilationUnit mutateMatcherMethods(J.CompilationUnit cu,
                                                         UnaryOperator<JavaType.Method> mutation) {
        return (J.CompilationUnit) new JavaIsoVisitor<Integer>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Integer p) {
                J.MethodInvocation m = super.visitMethodInvocation(method, p);
                if ("is".equals(m.getSimpleName()) && m.getMethodType() != null) {
                    m = m.withMethodType(mutation.apply(m.getMethodType()));
                }
                return m;
            }
        }.visitNonNull(cu, 0);
    }
}
