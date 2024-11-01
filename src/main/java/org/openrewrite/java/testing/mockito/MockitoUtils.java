/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.testing.mockito;

import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.List;
import java.util.stream.Collectors;

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
                        .map(J.MethodDeclaration.class::cast).collect(Collectors.toList()));

        visitor.maybeAddImport(importToAdd);
        String tplStr = methodAnnotationToAdd + methodAnnotationParameters +
          (isPublic ? " public" : "") + " void " + methodName + "() {}";
        return JavaTemplate.builder(tplStr)
                .contextSensitive()
                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, additionalClasspathResource))
                .imports(importToAdd)
                .build()
                .apply(
                        new Cursor(visitor.getCursor().getParentOrThrow(), classDecl),
                        (firstTestMethod != null) ?
                                firstTestMethod.getCoordinates().before() :
                                classDecl.getBody().getCoordinates().lastStatement()
                );
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
