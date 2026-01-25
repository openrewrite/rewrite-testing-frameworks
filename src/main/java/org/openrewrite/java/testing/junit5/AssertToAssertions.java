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

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.ChangeMethodTargetToStatic;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;

public class AssertToAssertions extends Recipe {

    @Getter
    final String displayName = "JUnit 4 `Assert` To JUnit Jupiter `Assertions`";

    @Getter
    final String description = "Change JUnit 4's `org.junit.Assert` into JUnit Jupiter's `org.junit.jupiter.api.Assertions`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.junit.Assert", false), new AssertToAssertionsVisitor());
    }

    public static class AssertToAssertionsVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final JavaType ASSERTION_TYPE = JavaType.buildType("org.junit.Assert");

        @Override
        public @Nullable J preVisit(J tree, ExecutionContext ctx) {
            if (tree instanceof JavaSourceFile) {
                JavaSourceFile c = (JavaSourceFile) tree;
                boolean hasWildcardAssertImport = false;
                for (J.Import imp : c.getImports()) {
                    if ("org.junit.Assert.*".equals(imp.getQualid().toString())) {
                        hasWildcardAssertImport = true;
                        break;
                    }
                }
                if (hasWildcardAssertImport) {
                    maybeRemoveImport("org.junit.Assert.*");
                    maybeAddImport("org.junit.jupiter.api.Assertions", "*", false);
                }
            }
            return tree;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
            if (!isJunitAssertMethod(m)) {
                return m;
            }
            doAfterVisit(new ChangeMethodTargetToStatic("org.junit.Assert " + m.getSimpleName() + "(..)",
                    "org.junit.jupiter.api.Assertions", null, null, true)
                    .getVisitor());

            List<JRightPadded<Expression>> args = m.getPadding().getArguments().getPadding().getElements();
            Expression firstArg = args.get(0).getElement();
            // Suppress arg-switching for Assertions.assertEquals(String, String)
            if (args.size() == 2) {
                if ("assertSame".equals(m.getSimpleName()) ||
                    "assertNotSame".equals(m.getSimpleName()) ||
                    "assertEquals".equals(m.getSimpleName()) ||
                    "assertNotEquals".equals(m.getSimpleName())) {
                    return m;
                }
            }
            if (TypeUtils.isString(firstArg.getType())) {
                // Move the first arg to be the last argument
                List<JRightPadded<Expression>> newArgs = new ArrayList<>(args);
                JRightPadded<Expression> first = newArgs.remove(0);

                // Adjust spacing and comments after shifting the first argument to the end
                List<Space> prefixes = args.stream().map(JRightPadded::getElement).map(Expression::getPrefix).collect(toList());
                List<Space> afters = args.stream().map(JRightPadded::getAfter).collect(toList());
                newArgs = ListUtils.map(
                        newArgs,
                        (i, arg) ->
                            arg.withElement(arg.getElement().withPrefix(prefixes.get(i))).withAfter(Space.EMPTY));
                if (!afters.get(afters.size() - 1).getComments().isEmpty()) {
                    newArgs.add(first
                        .withElement(first.getElement()
                            .withPrefix(afters.get(afters.size() - 1)
                                .withComments(ListUtils.mapLast(
                                    afters.get(afters.size() - 1).getComments(),
                                    c -> c.withSuffix(first.getElement().getPrefix().getWhitespace()))
                                )
                            )
                        )
                        .withAfter(first.getAfter()
                            .withWhitespace(afters.get(afters.size() - 1).getComments().get(afters.get(afters.size() - 1).getComments().size() -1).getSuffix())
                        )
                    );
                } else {
                    newArgs.add(
                        first
                            .withElement(first.getElement().withPrefix(prefixes.get(prefixes.size() - 1)))
                            .withAfter(first.getAfter().withWhitespace(afters.get(afters.size() - 1).getWhitespace()))
                    );
                }

                m = m.getPadding().withArguments(
                        m.getPadding().getArguments().getPadding().withElements(newArgs)
                );
            }

            return m;
        }

        private static boolean isJunitAssertMethod(J.MethodInvocation method) {
            if (method.getMethodType() != null && TypeUtils.isOfType(ASSERTION_TYPE, method.getMethodType().getDeclaringType())) {
                return !"assertThat".equals(method.getSimpleName());
            }
            if (!(method.getSelect() instanceof J.Identifier)) {
                return false;
            }
            J.Identifier receiver = (J.Identifier) method.getSelect();
            if (!(receiver.getType() instanceof JavaType.FullyQualified)) {
                return false;
            }
            JavaType.FullyQualified receiverType = (JavaType.FullyQualified) receiver.getType();
            return "org.junit.Assert".equals(receiverType.getFullyQualifiedName());
        }
    }
}
