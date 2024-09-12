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
        return "";
    }

    @Override
    public String getDescription() {
        return ".";
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
                                MOCKITO_WHEN.matches(((J.MethodInvocation)stmt).getSelect())) {
                                J.MethodInvocation when = (J.MethodInvocation)((J.MethodInvocation) stmt).getSelect();
                                if (when.getArguments().get(0) instanceof J.MethodInvocation && ((J.MethodInvocation)when.getArguments().get(0)).getMethodType().getFlags().contains(Flag.Static)){
                                    JavaType.FullyQualified arg_fq = TypeUtils.asFullyQualified(when.getArguments().get(0).getType());
                                    String template = String.format("try(MockedStatic<%s> mock%s = mockStatic(%s.class)){\n" +
                                                                    "mock%s.when(%s::%s).thenReturn(%s);\n" +
                                                                    "}", arg_fq.getClassName(), arg_fq.getClassName(), arg_fq.getClassName(), arg_fq.getClassName(), arg_fq.getClassName(), ((J.MethodInvocation)when.getArguments().get(0)).getSimpleName(), ((J.MethodInvocation) stmt).getArguments().get(0));
                                    m = JavaTemplate.builder(template)
                                            .contextSensitive()
                                            .javaParser(JavaParser.fromJavaVersion())
                                            .staticImports("org.mockito.Mockito.mockStatic")
                                            .build()
                                            .apply(getCursor(), stmt.getCoordinates().replace());
                                    rewrittenWhen = true;
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
