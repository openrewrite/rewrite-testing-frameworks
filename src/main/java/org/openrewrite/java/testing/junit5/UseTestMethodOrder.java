/*
 * Copyright 2020 the original author or authors.
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
package org.openrewrite.java.testing.junit5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaCoordinates;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static org.openrewrite.Tree.randomId;

public class UseTestMethodOrder extends Recipe {
    private static final J.Block EMPTY_BLOCK = new J.Block(randomId(), Space.EMPTY,
            Markers.EMPTY, new JRightPadded<>(false, Space.EMPTY, Markers.EMPTY),
            Collections.emptyList(), Space.EMPTY);

    @Override
    public String getDisplayName() {
        return "Use `@TestMethodOrder` rather than `@FixedMethodOrder`";
    }

    @Override
    public String getDescription() {
        return "JUnit5 has expanded on the method ordering capabilities of JUnit 4.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            private final JavaParser testMethodOrderParser = JavaParser.fromJavaVersion()
                    .dependsOn(Arrays.asList(
                            Parser.Input.fromString("package org.junit.jupiter.api;\n" +
                                    "public interface MethodOrderer {\n" +
                                    "  public class MethodName {}\n" +
                                    "  public class Alphanumeric {}\n" +
                                    "}"),
                            Parser.Input.fromString("package org.junit.jupiter.api;\n" +
                                    "public @interface TestMethodOrder {}")
                    ))
                    .build();

            private final JavaTemplate.Builder testMethodOrder =
                    template("@TestMethodOrder(#{}.class)")
                            .javaParser(testMethodOrderParser)
                            .imports("org.junit.jupiter.api.TestMethodOrder",
                                    "org.junit.jupiter.api.MethodOrderer.*");

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = classDecl;

                Set<J.Annotation> methodOrders = FindAnnotations.find(c.withBody(EMPTY_BLOCK),
                        "@org.junit.FixMethodOrder");

                if (!methodOrders.isEmpty()) {
                    J.Annotation methodOrder = methodOrders.iterator().next();

                    String order = methodOrder.getArguments() == null || methodOrder.getArguments().isEmpty() ?
                            null : methodOrder.getArguments().get(0).printTrimmed();

                    JavaCoordinates replace = methodOrder.getCoordinates().replace();

                    maybeAddImport("org.junit.jupiter.api.TestMethodOrder");
                    maybeRemoveImport("org.junit.FixMethodOrder");
                    maybeRemoveImport("org.junit.runners.MethodSorters");

                    if (order == null || order.contains("DEFAULT")) {
                        c = c.withTemplate(testMethodOrder.build(), replace, "Alphanumeric");
                        maybeAddImport("org.junit.jupiter.api.MethodOrderer.Alphanumeric");
                    } else if (order.contains("NAME_ASCENDING")) {
                        c = c.withTemplate(testMethodOrder.build(), replace, "MethodName");
                        maybeAddImport("org.junit.jupiter.api.MethodOrderer.MethodName");
                    }
                }

                return super.visitClassDeclaration(c, ctx);
            }
        };
    }
}
