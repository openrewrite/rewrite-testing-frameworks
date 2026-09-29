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
package org.openrewrite.java.testing.mockito;

import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Tree;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class MockitoUtils {
    public static J.ClassDeclaration maybeAddMethodWithAnnotation(
            JavaVisitor visitor,
            J.ClassDeclaration classDecl,
            ExecutionContext ctx,
            boolean isPublic,
            String methodName,
            String methodAnnotationSignature,
            String methodAnnotationToAdd,
            String additionalClasspathResource,
            String importToAdd,
            String methodAnnotationParameters
    ) {
        if (hasMethodWithAnnotation(classDecl, new AnnotationMatcher(methodAnnotationSignature))) {
            return classDecl;
        }

        J.MethodDeclaration firstTestMethod = getFirstTestMethod(
                classDecl.getBody().getStatements().stream().filter(J.MethodDeclaration.class::isInstance)
                        .map(J.MethodDeclaration.class::cast).collect(toList()));

        visitor.maybeAddImport(importToAdd);
        String tplStr = methodAnnotationToAdd + methodAnnotationParameters +
          (isPublic ? " public" : "") + " void " + methodName + "() {}";
        return separateAddedMembers(classDecl, JavaTemplate.builder(tplStr)
                .contextSensitive()
                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, additionalClasspathResource))
                .imports(importToAdd)
                .build()
                .apply(
                        new Cursor(visitor.getCursor().getParentOrThrow(), classDecl),
                        firstTestMethod != null ?
                                firstTestMethod.getCoordinates().before() :
                                classDecl.getBody().getCoordinates().lastStatement()
                ));
    }

    static J.ClassDeclaration separateAddedMembers(J.ClassDeclaration original, J.ClassDeclaration modified) {
        Set<UUID> originalIds = original.getBody().getStatements().stream().map(Tree::getId).collect(toSet());
        List<Statement> statements = modified.getBody().getStatements();
        return modified.withBody(modified.getBody().withStatements(ListUtils.map(statements, (i, statement) -> {
            if (i == 0 || !originalIds.contains(statement.getId())) {
                return statement;
            }
            Statement previous = statements.get(i - 1);
            boolean betweenFields = previous instanceof J.VariableDeclarations && statement instanceof J.VariableDeclarations;
            String whitespace = statement.getPrefix().getWhitespace();
            if (originalIds.contains(previous.getId()) || betweenFields || whitespace.contains("\n\n")) {
                return statement;
            }
            return statement.withPrefix(statement.getPrefix().withWhitespace("\n" + whitespace));
        })));
    }

    /// Wrapping a call to such a method in a lambda would leave a surrounding `catch` of that exception
    /// unreachable, whereas `Mockito.when(Type.method())` also stubs an active static mock.
    public static boolean throwsCheckedException(JavaType.@Nullable Method method) {
        if (method == null) {
            return false;
        }
        for (JavaType thrown : method.getThrownExceptions()) {
            if (!TypeUtils.isAssignableTo("java.lang.RuntimeException", thrown) &&
                !TypeUtils.isAssignableTo("java.lang.Error", thrown)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMethodWithAnnotation(J.ClassDeclaration classDecl, AnnotationMatcher annotationMatcher) {
        for (Statement statement : classDecl.getBody().getStatements()) {
            if (statement instanceof J.MethodDeclaration) {
                J.MethodDeclaration methodDeclaration = (J.MethodDeclaration) statement;
                List<J.Annotation> allAnnotations = methodDeclaration.getAllAnnotations();
                for (J.Annotation annotation : allAnnotations) {
                    if (annotationMatcher.matches(annotation)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static J.@Nullable MethodDeclaration getFirstTestMethod(List<J.MethodDeclaration> methods) {
        for (J.MethodDeclaration methodDeclaration : methods) {
            for (J.Annotation annotation : methodDeclaration.getLeadingAnnotations()) {
                if ("Test".equals(annotation.getSimpleName())) {
                    return methodDeclaration;
                }
            }
        }
        return null;
    }
}
