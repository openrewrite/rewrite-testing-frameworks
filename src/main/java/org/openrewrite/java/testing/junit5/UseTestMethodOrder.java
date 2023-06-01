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
package org.openrewrite.java.testing.junit5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import java.util.Set;

public class UseTestMethodOrder extends Recipe {

    @Override
    public String getDisplayName() {
        return "Migrate from JUnit 4 `@FixedMethodOrder` to JUnit 5 `@TestMethodOrder`";
    }

    @Override
    public String getDescription() {
        return "JUnit optionally allows test method execution order to be specified. This replaces JUnit 4 test execution ordering annotations with JUnit 5 replacements.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.junit.FixMethodOrder", false), new JavaIsoVisitor<ExecutionContext>() {

            @Nullable
            private JavaParser.Builder<?, ?> javaParser;

            private JavaParser.Builder<?, ?> javaParser(ExecutionContext ctx) {
                if (javaParser == null) {
                    javaParser = JavaParser.fromJavaVersion()
                            .classpathFromResources(ctx, "junit-jupiter-api-5.9");
                }
                return javaParser;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = classDecl;

                //noinspection DataFlowIssue
                Set<J.Annotation> methodOrders = FindAnnotations.find(cd.withBody(null), "@org.junit.FixMethodOrder");

                if (!methodOrders.isEmpty()) {
                    maybeAddImport("org.junit.jupiter.api.TestMethodOrder");
                    maybeRemoveImport("org.junit.FixMethodOrder");
                    maybeRemoveImport("org.junit.runners.MethodSorters");

                    cd = JavaTemplate.builder("@TestMethodOrder(MethodName.class)")
                            .javaParser(javaParser(ctx))
                            .imports("org.junit.jupiter.api.TestMethodOrder",
                                    "org.junit.jupiter.api.MethodOrderer.*")
                            .build()
                            .apply(getCursor(), methodOrders.iterator().next().getCoordinates().replace());
                    maybeAddImport("org.junit.jupiter.api.MethodOrderer.MethodName");
                }

                return super.visitClassDeclaration(cd, ctx);
            }
        });
    }
}
