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
package org.openrewrite.java.testing.gatling;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;

import java.util.List;

public class MigrateGatlingChoice extends Recipe {

    private static final String CHOICE = "io.gatling.javaapi.core.Choice";
    private static final String CORE_DSL = "io.gatling.javaapi.core.CoreDsl";

    private static final MethodMatcher WITH_KEY = new MethodMatcher(CHOICE + " withKey(..)");
    private static final MethodMatcher WITH_WEIGHT = new MethodMatcher(CHOICE + " withWeight(..)");

    @Getter
    final String displayName = "Migrate Gatling `Choice.withKey` and `Choice.withWeight`";

    @Getter
    final String description = "Gatling 3.11 removed the Java SDK's `Choice.withKey` and `Choice.withWeight` used " +
            "with `doSwitch` and `randomSwitch`. Rewrite `Choice.withKey(key, chain)` to `onCase(key).then(chain)` " +
            "and `Choice.withWeight(weight, chain)` to `percent(weight).then(chain)`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(new UsesMethod<>(WITH_KEY), new UsesMethod<>(WITH_WEIGHT)),
                new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J j = super.visitMethodInvocation(method, ctx);
                        if (!(j instanceof J.MethodInvocation)) {
                            return j;
                        }
                        J.MethodInvocation m = (J.MethodInvocation) j;
                        String replacement;
                        if (WITH_KEY.matches(m)) {
                            replacement = "onCase";
                        } else if (WITH_WEIGHT.matches(m)) {
                            replacement = "percent";
                        } else {
                            return m;
                        }

                        // The key or weight goes to the new method, and the chains to `then`
                        List<Expression> arguments = m.getArguments();
                        StringBuilder code = new StringBuilder(replacement).append("(#{any()}).then(");
                        for (int i = 1; i < arguments.size(); i++) {
                            code.append(i == 1 ? "" : ", ").append("#{any()}");
                        }
                        code.append(')');

                        // Removing the type from a wildcard import would unfold it into single type imports
                        if (importsChoiceExplicitly()) {
                            maybeRemoveImport(CHOICE);
                        }
                        maybeRemoveImport(CHOICE + "." + m.getSimpleName());
                        if (!importsCoreDslMember(replacement)) {
                            maybeAddImport(CORE_DSL, replacement);
                        }
                        J.MethodInvocation replaced = JavaTemplate.builder(code.toString())
                                .staticImports(CORE_DSL + "." + replacement)
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "gatling-core-3.16", "gatling-core-java-3.16"))
                                .build()
                                .apply(getCursor(), m.getCoordinates().replace(), arguments.toArray());
                        return replaced.withPrefix(m.getPrefix());
                    }

                    private boolean importsChoiceExplicitly() {
                        JavaSourceFile sourceFile = getCursor().firstEnclosing(JavaSourceFile.class);
                        return sourceFile != null && sourceFile.getImports().stream()
                                .anyMatch(anImport -> !anImport.isStatic() && CHOICE.equals(anImport.getTypeName()));
                    }

                    // Kotlin imports members without `static`, which `AddImport` does not see as covering them
                    private boolean importsCoreDslMember(String member) {
                        JavaSourceFile sourceFile = getCursor().firstEnclosing(JavaSourceFile.class);
                        return sourceFile != null && sourceFile.getImports().stream()
                                .map(anImport -> anImport.getQualid().printTrimmed())
                                .anyMatch(name -> name.equals(CORE_DSL + ".*") || name.equals(CORE_DSL + "." + member));
                    }
                });
    }
}
