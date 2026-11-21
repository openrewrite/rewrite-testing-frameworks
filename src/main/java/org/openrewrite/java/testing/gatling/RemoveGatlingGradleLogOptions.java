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
import org.openrewrite.gradle.IsBuildGradle;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RemoveGatlingGradleLogOptions extends Recipe {

    private static final Set<String> LOG_OPTIONS = new HashSet<>(Arrays.asList("logLevel", "logHttp"));

    @Getter
    final String displayName = "Remove the `logLevel` and `logHttp` options of the Gatling Gradle plugin";

    @Getter
    final String description = "Version 3.11 of the `io.gatling.gradle` plugin removed `logLevel` and `logHttp` " +
            "from the `gatling { }` extension, so a build script that sets them fails to evaluate. Configure logging " +
            "in a `logback.xml` or `logback-test.xml` in `src/gatling/resources` instead.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new IsBuildGradle<>(), new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (m.getSelect() != null || !"gatling".equals(m.getSimpleName()) || m.getArguments().size() != 1 ||
                    !(m.getArguments().get(0) instanceof J.Lambda)) {
                    return m;
                }
                J.Lambda closure = (J.Lambda) m.getArguments().get(0);
                if (!(closure.getBody() instanceof J.Block)) {
                    return m;
                }
                J.Block body = (J.Block) closure.getBody();
                List<Statement> original = body.getStatements();
                List<Statement> statements = ListUtils.map(original, (i, statement) -> {
                    if (isLogOption(statement)) {
                        return null;
                    }
                    return i > 0 && isLogOption(original.get(i - 1)) ? statement.withPrefix(withoutTrailingComment(statement.getPrefix())) : statement;
                });
                if (statements == original) {
                    return m;
                }
                J.Block newBody = body.withStatements(statements)
                        .withEnd(isLogOption(original.get(original.size() - 1)) ? withoutTrailingComment(body.getEnd()) : body.getEnd());
                return m.withArguments(ListUtils.mapFirst(m.getArguments(), a -> closure.withBody(newBody)));
            }
        });
    }

    // A comment on the same line as a removed statement belongs to the statement, but the parser keeps it in the prefix of what follows
    private static Space withoutTrailingComment(Space space) {
        if (space.getComments().isEmpty() || space.getWhitespace().contains("\n")) {
            return space;
        }
        Comment first = space.getComments().get(0);
        return Space.build(first.getSuffix(), space.getComments().subList(1, space.getComments().size()));
    }

    // Both `logLevel = 'WARN'` and `logLevel 'WARN'`, either of which the Groovy parser may wrap in a return
    private static boolean isLogOption(Statement statement) {
        Statement s = statement instanceof J.Return && ((J.Return) statement).getExpression() instanceof Statement ?
                (Statement) ((J.Return) statement).getExpression() : statement;
        if (s instanceof J.Assignment) {
            Expression variable = ((J.Assignment) s).getVariable();
            return variable instanceof J.Identifier && LOG_OPTIONS.contains(((J.Identifier) variable).getSimpleName());
        }
        return s instanceof J.MethodInvocation && ((J.MethodInvocation) s).getSelect() == null &&
               LOG_OPTIONS.contains(((J.MethodInvocation) s).getSimpleName());
    }
}
