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
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;
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

    private static final ThreadLocal<JavaParser> TEST_METHOD_ORDER_PARSER = ThreadLocal.withInitial(() ->
            JavaParser.fromJavaVersion()
                    .dependsOn(Arrays.asList(
                            Parser.Input.fromString("package org.junit.jupiter.api;\n" +
                                    "public interface MethodOrderer {\n" +
                                    "  public class MethodName {}\n" +
                                    "}"),
                            Parser.Input.fromString("package org.junit.jupiter.api;\n" +
                                    "public @interface TestMethodOrder {}")
                    ))
                    .build()
    );

    @Override
    public String getDisplayName() {
        return "Migrate from JUnit4 `@FixedMethodOrder` to JUnit5 `@TestMethodOrder`";
    }

    @Override
    public String getDescription() {
        return "JUnit optionally allows test method execution order to be specified. This Recipe replaces JUnit4 test execution ordering annotations with JUnit5 replacements.";
    }

    @Override
    protected @Nullable TreeVisitor<?, ExecutionContext> getApplicableTest() {
        return new UsesType<>("org.junit.FixMethodOrder");
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            private final JavaTemplate testMethodOrder =
                    template("@TestMethodOrder(#{}.class)")
                            .javaParser(TEST_METHOD_ORDER_PARSER.get())
                            .imports("org.junit.jupiter.api.TestMethodOrder",
                                    "org.junit.jupiter.api.MethodOrderer.*")
                            .build();

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = classDecl;

                Set<J.Annotation> methodOrders = FindAnnotations.find(c.withBody(EMPTY_BLOCK),
                        "@org.junit.FixMethodOrder");

                if (!methodOrders.isEmpty()) {
                    maybeAddImport("org.junit.jupiter.api.TestMethodOrder");
                    maybeRemoveImport("org.junit.FixMethodOrder");
                    maybeRemoveImport("org.junit.runners.MethodSorters");

                    c = c.withTemplate(testMethodOrder,
                            methodOrders.iterator().next().getCoordinates().replace(),
                            "MethodName");
                    maybeAddImport("org.junit.jupiter.api.MethodOrderer.MethodName");
                }

                return super.visitClassDeclaration(c, ctx);
            }
        };
    }
}
