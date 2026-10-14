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
package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.tree.J;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.kotlin.tree.K;

import static org.assertj.core.api.Assertions.assertThat;

class KotlinJUnit5MissingTypeTest {
    @Test
    void parameterizedClassWithoutTypeIsUnchanged() {
        ExecutionContext ctx = context();
        K.CompilationUnit source = withoutClassTypes(parse(ctx, """
                import org.junit.Test
                import org.junit.runner.RunWith
                import org.junit.runners.Parameterized

                @RunWith(Parameterized::class)
                class Example(private val value: Int) {
                    @Test fun test() {}
                    companion object {
                        @JvmStatic @Parameterized.Parameters fun values() = listOf(arrayOf(1))
                    }
                }
                """), ctx);
        assertThat(((K.CompilationUnit) new KotlinParameterizedRunnerVisitor().visitNonNull(source, ctx)).printAll())
                .isEqualTo(source.printAll());
    }

    @Test
    void junitParamsClassWithoutTypeIsUnchanged() {
        ExecutionContext ctx = context();
        K.CompilationUnit source = withoutClassTypes(parse(ctx, """
                import junitparams.Parameters
                import org.junit.Test

                class Example {
                    @Test @Parameters(method = "values") fun test(value: Int) {}
                    fun values() = arrayOf(1)
                }
                """), ctx);
        assertThat(((K.CompilationUnit) new KotlinJUnitParamsRunnerVisitor().visitNonNull(source, ctx)).printAll())
                .isEqualTo(source.printAll());
    }

    @Test
    void nestedFixtureWithoutTypeIsUnchanged() {
        ExecutionContext ctx = context();
        K.CompilationUnit parsed = parse(ctx, """
                import org.junit.jupiter.api.Nested

                class Example {
                    @Nested inner class Tests {
                        companion object { @JvmStatic fun helper() {} }
                    }
                }
                """);
        J.ClassDeclaration root = (J.ClassDeclaration) parsed.getStatements().get(0);
        K.CompilationUnit source = (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration clazz, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(clazz, ctx);
                return root.getId().equals(cd.getId()) ? cd : cd.withType(null);
            }
        }.visitNonNull(parsed, ctx);
        assertThat(((K.CompilationUnit) new KotlinNestedStaticFixturesVisitor(root.getId()).visitNonNull(source, ctx)).printAll())
                .isEqualTo(source.printAll());
    }

    private static ExecutionContext context() {
        return new InMemoryExecutionContext(t -> {
            throw new AssertionError(t);
        });
    }

    private static K.CompilationUnit parse(ExecutionContext ctx, String source) {
        return (K.CompilationUnit) KotlinParser.builder()
                .classpathFromResources(ctx, "junit-4", "junit-jupiter-api-5", "JUnitParams-1.1").build()
                .parse(ctx, source).findFirst().orElseThrow();
    }

    private static K.CompilationUnit withoutClassTypes(K.CompilationUnit source, ExecutionContext ctx) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration clazz, ExecutionContext ctx) {
                return super.visitClassDeclaration(clazz, ctx).withType(null);
            }
        }.visitNonNull(source, ctx);
    }
}
