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
package org.openrewrite.java.testing.mockito;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.List;

public class MockitoStubbingToLenient extends Recipe {

    @Getter
    final String displayName = "Wrap Mockito stubbing calls with `lenient()`";

    @Getter
    final String description = "Wrap Mockito stubbing calls (`when`, `doReturn`, `doThrow`, `doAnswer`, `doNothing`, `doCallRealMethod`) " +
            "with `lenient()` in test classes where strict stubbing is active. " +
            "This prevents `UnnecessaryStubbingException` from Mockito's strict stubbing, " +
            "which is the default in Mockito 3+.";

    private static final MethodMatcher WHEN_MATCHER = new MethodMatcher("org.mockito.Mockito when(..)");
    private static final MethodMatcher DO_RETURN_MATCHER = new MethodMatcher("org.mockito.Mockito doReturn(..)");
    private static final MethodMatcher DO_THROW_MATCHER = new MethodMatcher("org.mockito.Mockito doThrow(..)");
    private static final MethodMatcher DO_ANSWER_MATCHER = new MethodMatcher("org.mockito.Mockito doAnswer(..)");
    private static final MethodMatcher DO_NOTHING_MATCHER = new MethodMatcher("org.mockito.Mockito doNothing()");
    private static final MethodMatcher DO_CALL_REAL_MATCHER = new MethodMatcher("org.mockito.Mockito doCallRealMethod()");

    // Strict stubbing contexts (Strictness.STRICT_STUBS — throws UnnecessaryStubbingException)
    private static final AnnotationMatcher EXTEND_WITH_MOCKITO = new AnnotationMatcher(
            "@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)");
    private static final AnnotationMatcher RUN_WITH_MOCKITO_STRICT_STUBS = new AnnotationMatcher(
            "@org.junit.runner.RunWith(org.mockito.junit.MockitoJUnitRunner.StrictStubs.class)");

    // Not STRICT_STUBS — skip (LENIENT ignores unused stubs, WARN only logs warnings)
    private static final AnnotationMatcher MOCKITO_SETTINGS_LENIENT = new AnnotationMatcher(
            "@org.mockito.junit.jupiter.MockitoSettings(strictness=org.mockito.quality.Strictness.LENIENT)");
    private static final AnnotationMatcher MOCKITO_SETTINGS_WARN = new AnnotationMatcher(
            "@org.mockito.junit.jupiter.MockitoSettings(strictness=org.mockito.quality.Strictness.WARN)");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesMethod<>(WHEN_MATCHER),
                        new UsesMethod<>(DO_RETURN_MATCHER),
                        new UsesMethod<>(DO_THROW_MATCHER),
                        new UsesMethod<>(DO_ANSWER_MATCHER),
                        new UsesMethod<>(DO_NOTHING_MATCHER),
                        new UsesMethod<>(DO_CALL_REAL_MATCHER)
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        if (hasStrictStubbing(classDecl)) {
                            getCursor().putMessage("wrapWithLenient", true);
                        }
                        return super.visitClassDeclaration(classDecl, ctx);
                    }

                    private boolean hasStrictStubbing(J.ClassDeclaration cd) {
                        List<J.Annotation> annotations = cd.getLeadingAnnotations();

                        boolean hasStrictContext = annotations.stream().anyMatch(a ->
                                EXTEND_WITH_MOCKITO.matches(a) ||
                                RUN_WITH_MOCKITO_STRICT_STUBS.matches(a));

                        if (!hasStrictContext) {
                            return false;
                        }

                        boolean isAlreadyLenient = annotations.stream().anyMatch(a ->
                                MOCKITO_SETTINGS_LENIENT.matches(a) ||
                                MOCKITO_SETTINGS_WARN.matches(a));

                        return !isAlreadyLenient;
                    }

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

                        Boolean wrap = getCursor().getNearestMessage("wrapWithLenient");
                        if (!Boolean.TRUE.equals(wrap)) {
                            return mi;
                        }

                        String methodName = getMatchedMethodName(mi);
                        if (methodName != null) {
                            return wrapWithLenient(mi, methodName, ctx);
                        }
                        return mi;
                    }

                    private String getMatchedMethodName(J.MethodInvocation mi) {
                        if (WHEN_MATCHER.matches(mi)) {
                            return "when";
                        } else if (DO_RETURN_MATCHER.matches(mi)) {
                            return "doReturn";
                        } else if (DO_THROW_MATCHER.matches(mi)) {
                            return "doThrow";
                        } else if (DO_ANSWER_MATCHER.matches(mi)) {
                            return "doAnswer";
                        } else if (DO_NOTHING_MATCHER.matches(mi)) {
                            return "doNothing";
                        } else if (DO_CALL_REAL_MATCHER.matches(mi)) {
                            return "doCallRealMethod";
                        }
                        return null;
                    }

                    private J.MethodInvocation wrapWithLenient(J.MethodInvocation mi, String methodName, ExecutionContext ctx) {
                        List<Expression> args = mi.getArguments();
                        boolean emptyArgs = args.size() == 1 && args.get(0) instanceof J.Empty;

                        StringBuilder templateStr = new StringBuilder("lenient().");
                        templateStr.append(methodName).append("(");
                        Object[] templateArgs;

                        if (emptyArgs) {
                            templateArgs = new Object[0];
                        } else {
                            templateArgs = new Object[args.size()];
                            for (int i = 0; i < args.size(); i++) {
                                if (i > 0) {
                                    templateStr.append(", ");
                                }
                                templateStr.append("#{any()}");
                                templateArgs[i] = args.get(i);
                            }
                        }
                        templateStr.append(")");

                        maybeAddImport("org.mockito.Mockito", "lenient");
                        maybeRemoveImport("org.mockito.Mockito." + methodName);

                        return JavaTemplate.builder(templateStr.toString())
                                .staticImports("org.mockito.Mockito.lenient")
                                .javaParser(JavaParser.fromJavaVersion()
                                        .classpathFromResources(ctx, "mockito-core-3.12"))
                                .build()
                                .apply(getCursor(), mi.getCoordinates().replace(), templateArgs);
                    }
                }
        );
    }
}
