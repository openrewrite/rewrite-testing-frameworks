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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.MethodCall;

import java.util.regex.Pattern;

public class ReplaceGatlingDollarExpressionLanguage extends Recipe {

    // Kotlin needs the dollar escaped to keep it out of string templates
    private static final Pattern DOLLAR_EXPRESSION = Pattern.compile("\\\\?\\$\\{([^{}]*)}");

    @Getter
    final String displayName = "Replace Gatling `${}` Expression Language with `#{}`";

    @Getter
    final String description = "Gatling 3.7 deprecated the `${}` Expression Language syntax, as it clashes with " +
            "Scala and Kotlin string interpolation, and 3.11 stopped interpreting it altogether. Rewrite `${...}` " +
            "to `#{...}` in the plain string literals of Java, Kotlin and Scala sources importing Gatling, dropping " +
            "the backslash Kotlin needed as in `\"\\${id}\"`. Kotlin string templates and Scala interpolated " +
            "strings such as `s\"${name}\"` are left alone, as are strings only passed to methods of other libraries, " +
            "such as `System.getProperty(\"url\", \"${URL}\")`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(GatlingSources.importingGatling(true), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal l = super.visitLiteral(literal, ctx);
                // The parts of Scala interpolated strings and Kotlin string templates are literals too, but without quotes
                if (!(l.getValue() instanceof String) || l.getValueSource() == null ||
                    !l.getValueSource().startsWith("\"") || !l.getValueSource().contains("${") ||
                    onlyPassedToOtherLibraries(getCursor())) {
                    return l;
                }
                return l.withValue(replace((String) l.getValue()))
                        .withValueSource(replace(l.getValueSource()));
            }
        });
    }

    private static boolean onlyPassedToOtherLibraries(Cursor literal) {
        boolean otherLibrary = false;
        for (Cursor c = literal.getParentTreeCursor(); !isStatementBoundary(c.getValue()); c = c.getParentTreeCursor()) {
            if (c.getValue() instanceof MethodCall) {
                JavaType.Method methodType = ((MethodCall) c.getValue()).getMethodType();
                // Gatling's calls are untyped when it is missing from the classpath
                if (methodType == null || methodType.getDeclaringType().getFullyQualifiedName().startsWith("io.gatling.")) {
                    return false;
                }
                otherLibrary = true;
            }
        }
        return otherLibrary;
    }

    private static boolean isStatementBoundary(Object tree) {
        return tree instanceof J.Block || tree instanceof J.Lambda || tree instanceof JavaSourceFile;
    }

    private static String replace(String text) {
        return DOLLAR_EXPRESSION.matcher(text).replaceAll("#{$1}");
    }
}
