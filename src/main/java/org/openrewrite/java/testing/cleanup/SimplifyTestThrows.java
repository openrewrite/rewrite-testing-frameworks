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
package org.openrewrite.java.testing.cleanup;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.time.Duration;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public class SimplifyTestThrows extends Recipe {

    private static final String FQN_JAVA_LANG_EXCEPTION = "java.lang.Exception";

    @Override
    public String getDisplayName() {
        return "Simplify `throws` statements of tests";
    }

    @Override
    public String getDescription() {
        return "Replace all thrown exception classes of test method signatures by `Exception`.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesType<>("org.junit.jupiter.api.Test", false),
                        new UsesType<>("org.junit.jupiter.api.TestTemplate", false),
                        new UsesType<>("org.junit.jupiter.api.RepeatedTest", false),
                        new UsesType<>("org.junit.jupiter.params.ParameterizedTest", false),
                        new UsesType<>("org.junit.jupiter.api.TestFactory", false)
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

                        // reject invalid methods
                        if (TypeUtils.isOverride(m.getMethodType()) ||
                            !hasJUnit5MethodAnnotation(m) ||
                            throwsNothingOrExceptionOrThrowable(m.getThrows())) {
                            return m;
                        }

                        // remove imports of the old exceptions
                        for (NameTree t : m.getThrows()) {
                            JavaType.FullyQualified type = TypeUtils.asFullyQualified(t.getType());
                            if (type != null) {
                                maybeRemoveImport(type);
                            }
                        }

                        // overwrite the throws declarations
                        J.Identifier exceptionIdentifier = new J.Identifier(Tree.randomId(),
                                Space.SINGLE_SPACE,
                                Markers.EMPTY,
                                emptyList(),
                                "Exception",
                                JavaType.ShallowClass.build(FQN_JAVA_LANG_EXCEPTION),
                                null);
                        return m.withThrows(singletonList(exceptionIdentifier));
                    }

                    /**
                     * @return true if the method has no throws clause or only throws Exception
                     */
                    @Contract("null -> true")
                    private boolean throwsNothingOrExceptionOrThrowable(@Nullable List<NameTree> th) {
                        if (th == null || th.isEmpty()) {
                            return true;
                        }
                        return th.size() == 1 &&
                                (TypeUtils.isOfClassType(th.get(0).getType(), FQN_JAVA_LANG_EXCEPTION) ||
                                        TypeUtils.isOfClassType(th.get(0).getType(), "java.lang.Throwable"));
                    }

                    private boolean hasJUnit5MethodAnnotation(J.MethodDeclaration method) {
                        for (J.Annotation a : method.getLeadingAnnotations()) {
                            if (TypeUtils.isOfClassType(a.getType(), "org.junit.jupiter.api.Test") ||
                                TypeUtils.isOfClassType(a.getType(), "org.junit.jupiter.api.TestTemplate") ||
                                TypeUtils.isOfClassType(a.getType(), "org.junit.jupiter.api.RepeatedTest") ||
                                TypeUtils.isOfClassType(a.getType(), "org.junit.jupiter.params.ParameterizedTest") ||
                                TypeUtils.isOfClassType(a.getType(), "org.junit.jupiter.api.TestFactory")) {
                                return true;
                            }
                        }
                        return false;
                    }
                });
    }
}
