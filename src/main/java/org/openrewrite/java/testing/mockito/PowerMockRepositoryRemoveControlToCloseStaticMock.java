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
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;

import static org.openrewrite.java.testing.mockito.PowerMockitoMockStaticToMockito.classLiteral;
import static org.openrewrite.java.testing.mockito.ScopedMocks.MOCKED_STATIC;

public class PowerMockRepositoryRemoveControlToCloseStaticMock extends Recipe {

    private static final MethodMatcher REMOVE_CONTROL =
            new MethodMatcher("org.powermock.core.MockRepository removeClassMethodInvocationControl(java.lang.Class)");

    @Getter
    final String displayName = "Replace `MockRepository.removeClassMethodInvocationControl()` with closing the static mock";

    @Getter
    final String description = "Replaces `MockRepository.removeClassMethodInvocationControl(Type.class)`, which ends the " +
            "static mocking of `Type` that `PowerMockito.mockStatic(Type.class)` started, with closing the `MockedStatic` " +
            "that `PowerMockitoMockStaticToMockito` assigns to a field in its place. Where the class holds no " +
            "`MockedStatic` for the type, the call is left as it is.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(REMOVE_CONTROL), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = super.visitBlock(block, ctx);
                J.ClassDeclaration enclosing = getCursor().firstEnclosing(J.ClassDeclaration.class);
                if (enclosing == null) {
                    return b;
                }
                return b.withStatements(ListUtils.map(b.getStatements(), statement -> {
                    if (!(statement instanceof J.MethodInvocation) || !REMOVE_CONTROL.matches((J.MethodInvocation) statement)) {
                        return statement;
                    }
                    J.MethodInvocation mi = (J.MethodInvocation) statement;
                    JavaType.FullyQualified mocked = classLiteral(mi.getArguments().get(0));
                    J.Identifier field = mocked == null ? null : staticMockField(enclosing, mocked);
                    if (field == null) {
                        return statement;
                    }
                    maybeRemoveImport("org.powermock.core.MockRepository");
                    return JavaTemplate.builder(
                                    "if (#{any(org.mockito.MockedStatic)} != null) {\n#{any(org.mockito.MockedStatic)}.closeOnDemand();\n}")
                            .contextSensitive()
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                            .build()
                            .<Statement>apply(new Cursor(new Cursor(getCursor().getParentOrThrow(), b), mi), mi.getCoordinates().replace(), field, field)
                            .withPrefix(mi.getPrefix());
                }));
            }

            private J.@Nullable Identifier staticMockField(J.ClassDeclaration classDecl, JavaType.FullyQualified mocked) {
                for (Statement statement : classDecl.getBody().getStatements()) {
                    if (!(statement instanceof J.VariableDeclarations)) {
                        continue;
                    }
                    for (J.VariableDeclarations.NamedVariable variable : ((J.VariableDeclarations) statement).getVariables()) {
                        JavaType.Parameterized type = TypeUtils.asParameterized(variable.getType());
                        if (type != null && TypeUtils.isOfClassType(type, MOCKED_STATIC) && type.getTypeParameters().size() == 1 &&
                            TypeUtils.isOfType(type.getTypeParameters().get(0), mocked)) {
                            return variable.getName();
                        }
                    }
                }
                return null;
            }
        });
    }
}
