/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.cleanup;

import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.NameCaseConvention;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.java.tree.J.MethodDeclaration;
import org.openrewrite.marker.Markers;

import java.time.Duration;
import java.util.Arrays;
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
                new SimplifyTestThrowsVisitor());
    }

    private static class SimplifyTestThrowsVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public MethodDeclaration visitMethodDeclaration(MethodDeclaration method,
                                                        ExecutionContext ctx) {
            MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

            // reject invalid methods
            if (TypeUtils.isOverride(m.getMethodType())
                    || !hasJUnit5MethodAnnotation(method)
                    || throwsIsMinimal(method)) {
                return m;
            }

            // remove imports of the old exceptions
            m.getThrows().forEach(t -> {
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(t.getType());
                if (type != null) {
                    maybeRemoveImport(type);
                }
            });

            // overwrite the throws declarations
            J.Identifier exceptionIdentifier = new J.Identifier(Tree.randomId(),
                    Space.format(" "),
                    Markers.EMPTY,
                    emptyList(),
                    "Exception",
                    JavaType.ShallowClass.build(FQN_JAVA_LANG_EXCEPTION),
                    null);
            return m.withThrows(singletonList(exceptionIdentifier));
        }

        private boolean throwsIsMinimal(MethodDeclaration method) {
            @Nullable List<NameTree> th = method.getThrows();
            if (th == null || th.isEmpty()) {
                return true;
            }
            if (th.size() > 1) {
                return false;
            }
            @Nullable JavaType exType = th.get(0).getType();
            return TypeUtils.isOfClassType(exType, FQN_JAVA_LANG_EXCEPTION);
        }

        private static boolean hasJUnit5MethodAnnotation(MethodDeclaration method) {
            for (J.Annotation a : method.getLeadingAnnotations()) {
                if (TypeUtils.isOfClassType(a.getType(), "org.junit.jupiter.api.Test")
                        || TypeUtils.isOfClassType(a.getType(), "org.junit.jupiter.api.TestTemplate")
                        || TypeUtils.isOfClassType(a.getType(), "org.junit.jupiter.api.RepeatedTest")
                        || TypeUtils.isOfClassType(a.getType(), "org.junit.jupiter.params.ParameterizedTest")
                        || TypeUtils.isOfClassType(a.getType(), "org.junit.jupiter.api.TestFactory")) {
                    return true;
                }
            }
            return false;
        }
    }

}
