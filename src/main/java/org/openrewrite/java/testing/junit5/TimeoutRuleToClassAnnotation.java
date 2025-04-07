/*
 * Copyright 2025 the original author or authors.
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

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class TimeoutRuleToClassAnnotation extends Recipe {

    @Override
    public String getDisplayName() {
        return "JUnit 4 `@Rule Timeout` to JUnit Jupiter's `Timeout`";
    }

    @Override
    public String getDescription() {
        return "Replace usages of JUnit 4's `@Rule Timeout` with JUnit 5 `Timeout` class annotation.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.junit.rules.Timeout", false), new TimeoutRuleToClassAnnotationVisitor());
    }

    public static class TimeoutRuleToClassAnnotationVisitor extends JavaIsoVisitor<ExecutionContext> {

        private JavaParser.@Nullable Builder<?, ?> javaParser;

        private JavaParser.Builder<?, ?> javaParser(ExecutionContext ctx) {
            if (javaParser == null) {
                javaParser = JavaParser.fromJavaVersion()
                        .classpathFromResources(ctx, "junit-jupiter-api-5", "hamcrest-3");
            }
            return javaParser;

        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

            AtomicReference<Expression> initializer = new AtomicReference<>();

            cd = cd.withBody(cd.getBody().withStatements(ListUtils.map(cd.getBody().getStatements(), statement -> {
                if (statement instanceof J.VariableDeclarations) {
                    //noinspection ConstantConditions
                    if (TypeUtils.isOfClassType(((J.VariableDeclarations) statement).getTypeExpression().getType(),
                            "org.junit.rules.Timeout")) {
                        List<J.VariableDeclarations.NamedVariable> variables = ((J.VariableDeclarations) statement).getVariables();
                        if (!variables.isEmpty()) {
                            initializer.set(variables.get(0).getInitializer());
                        }
                        return null;
                    }
                }
                return statement;
            })));

            if (initializer.get() != null) {
                cd = insertTimeoutAnnotation(initializer.get(), cd, ctx);
                maybeRemoveImport("org.junit.Rule");
                maybeRemoveImport("org.junit.rules.Timeout");
            }
            return cd;
        }

        private J.ClassDeclaration insertTimeoutAnnotation(Expression ex, J.ClassDeclaration cd, ExecutionContext ctx) {
            JavaTemplate.Builder builder;
            Object[] params;
            if (ex instanceof J.NewClass) {
                List<Expression> arguments = ((J.NewClass) ex).getArguments();
                if (arguments.size() == 2) {
                    builder = JavaTemplate.builder("@Timeout(value = #{any(long)}, unit = #{any(TimeUnit)})");
                    params = new Object[]{arguments.get(0), arguments.get(1)};
                } else {
                    builder = JavaTemplate.builder("@Timeout(value = #{any(long)}, unit = TimeUnit.MILLISECONDS)");
                    params = new Object[]{arguments.get(0)};
                }
            } else if (ex instanceof J.MethodInvocation) {
                String simpleName = ((J.MethodInvocation) ex).getName().getSimpleName();
                String units = simpleName.equals("millis") ? "MILLISECONDS" : "SECONDS";
                builder = JavaTemplate.builder("@Timeout(value = #{any(long)}, unit = TimeUnit." + units + ")");
                params = new Object[]{((J.MethodInvocation) ex).getArguments().get(0)};
            } else {
                return cd;
            }

            cd = builder.javaParser(javaParser(ctx))
                    .imports("org.junit.jupiter.api.Timeout", "java.util.concurrent.TimeUnit")
                    .build()
                    .apply(
                            updateCursor(cd),
                            cd.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)),
                            params
                    );
            maybeAddImport("org.junit.jupiter.api.Timeout");
            maybeAddImport("java.util.concurrent.TimeUnit");
            return cd;
        }
    }
}
