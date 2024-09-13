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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.List;

public class MockitoWhenOnStaticToMockStatic extends Recipe {

    private static final MethodMatcher MOCKITO_WHEN = new MethodMatcher("org.mockito.Mockito when(..)");

    @Override
    public String getDisplayName() {
        return "Replace `Mockito.when` on static (non mock) with try-with-resource with MockedStatic";
    }

    @Override
    public String getDescription() {
        return "Replace `Mockito.when` on static (non mock) with try-with-resource with MockedStatic as Mockito4 no longer allows this.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.mockito.Mockito", true),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                        boolean rewrittenWhen = false;
                        List<Statement> statementsBeforeWhen = new ArrayList<>();
                        List<Statement> statementsAfterWhen = new ArrayList<>();
                        for (Statement stmt : m.getBody().getStatements()) {
                            if (stmt instanceof J.MethodInvocation &&
                                MOCKITO_WHEN.matches(((J.MethodInvocation) stmt).getSelect())) {
                                J.MethodInvocation when = (J.MethodInvocation) ((J.MethodInvocation) stmt).getSelect();
                                if (when.getArguments().get(0) instanceof J.MethodInvocation && ((J.MethodInvocation) when.getArguments().get(0)).getMethodType().getFlags().contains(Flag.Static)) {
                                    JavaType.FullyQualified arg_fq = TypeUtils.asFullyQualified(when.getArguments().get(0).getType());
                                    J.Identifier ident = (J.Identifier) ((J.MethodInvocation)when.getArguments().get(0)).getSelect();
                                    String template = String.format("try(MockedStatic<#{}> mock%s = mockStatic(#{}.class)){\n" +
                                                                    "    mock%s.when(#{any()}).thenReturn(#{any()});\n" +
                                                                    "}", arg_fq.getClassName(), arg_fq.getClassName());
                                    m = JavaTemplate.builder(template)
                                            .contextSensitive()
                                            .javaParser(JavaParser.fromJavaVersion())
                                            .imports("org.mockito.MockedStatic")
                                            .staticImports("org.mockito.Mockito.mockStatic")
                                            .build()
                                            .apply(getCursor(), stmt.getCoordinates().replace(), ident.getType(), ident.getType(), when.getArguments().get(0), ((J.MethodInvocation) stmt).getArguments().get(0));
                                    rewrittenWhen = true;
                                    maybeAddImport("org.mockito.MockedStatic", false);
                                    maybeAddImport("org.mockito.Mockito", "mockStatic");
                                    continue;
                                }
                            }
                            if (rewrittenWhen) {
                                statementsAfterWhen.add(stmt);
                            } else {
                                statementsBeforeWhen.add(stmt);
                            }
                        }
                        if (rewrittenWhen) {
                            J.Try try_catch = (J.Try) m.getBody().getStatements().get(statementsBeforeWhen.size());
                            return maybeAutoFormat(method, m.withBody(m.getBody().withStatements(ListUtils.concat(statementsBeforeWhen, try_catch.withBody(try_catch.getBody().withStatements(ListUtils.concatAll(try_catch.getBody().getStatements(), statementsAfterWhen)))))), ctx);
                        } else {
                            return m;
                        }
                    }
                });
    }
}
