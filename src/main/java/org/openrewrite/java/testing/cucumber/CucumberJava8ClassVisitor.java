/*
 * Copyright 2022 the original author or authors.
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
package org.openrewrite.java.testing.cucumber;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.java.tree.JavaType.Class;
import org.openrewrite.java.tree.JavaType.FullyQualified;

@RequiredArgsConstructor
class CucumberJava8ClassVisitor extends JavaIsoVisitor<ExecutionContext> {

    private static final String IO_CUCUMBER_JAVA8 = "io.cucumber.java8";

    private final FullyQualified stepDefinitionsClass;
    private final String replacementImport;
    private final String template;
    private final Object[] templateParameters;

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, ExecutionContext p) {
        J.ClassDeclaration classDeclaration = super.visitClassDeclaration(cd, p);
        if (!TypeUtils.isOfType(classDeclaration.getType(), stepDefinitionsClass)) {
            // We aren't looking at the specified class so return without making any modifications
            return classDeclaration;
        }

        // Remove implement of Java8 interfaces & imports; return retained
        List<TypeTree> retained = filterImplementingInterfaces(classDeclaration);

        // Import Given/When/Then or Before/After as applicable
        maybeAddImport(replacementImport);

        // Replace any Scenario imports
        maybeRemoveImport(IO_CUCUMBER_JAVA8 + ".Scenario");
        maybeAddImport("io.cucumber.java.Scenario");

        // Update implements & add new methods last
        return maybeAutoFormat(
                classDeclaration,
                classDeclaration
                        .withImplements(retained)
                        .withTemplate(JavaTemplate.builder(this::getCursor, template)
                                .javaParser(() -> JavaParser.fromJavaVersion().classpath("junit", "cucumber-java")
                                        .build())
                                .imports(replacementImport, "io.cucumber.java.Scenario")
                                .build(),
                                classDeclaration.getBody().getCoordinates().lastStatement(),
                                templateParameters),
                p);
    }

    @Override
    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration md, ExecutionContext p) {
        // Remove empty constructor which might be left over after removing method invocations
        J.MethodDeclaration methodDeclaration = (J.MethodDeclaration) super.visitMethodDeclaration(md, p);
        // TODO Should we also remove now empty methods? And how to remove callers?
        if (methodDeclaration.isConstructor() && methodDeclaration.getBody().getStatements().isEmpty()) {
            return null;
        }
        return methodDeclaration;
    }

    private List<TypeTree> filterImplementingInterfaces(J.ClassDeclaration classDeclaration) {
        List<TypeTree> retained = new ArrayList<>();
        for (TypeTree typeTree : Optional.ofNullable(classDeclaration.getImplements())
                .orElse(Collections.emptyList())) {
            if (typeTree.getType() instanceof JavaType.Class) {
                JavaType.Class clazz = (Class) typeTree.getType();
                if (IO_CUCUMBER_JAVA8.equals(clazz.getPackageName())) {
                    maybeRemoveImport(clazz.getFullyQualifiedName());
                    continue;
                }
            }
            retained.add(typeTree);
        }
        return retained;
    }
}
