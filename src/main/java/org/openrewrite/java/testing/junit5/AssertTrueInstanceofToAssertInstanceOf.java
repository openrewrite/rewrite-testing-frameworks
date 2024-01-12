/*
 * Copyright 2023 the original author or authors.
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
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

public class AssertTrueInstanceofToAssertInstanceOf extends Recipe {
    @Override
    public String getDisplayName() {
        return "assertTrue(x instanceof y) to assertInstanceOf(y.class, x)";
    }

    @Override
    public String getDescription() {
        return "Migration of JUnit4 (or potentially JUnit5) test case in form of assertTrue(x instanceof y) to assertInstanceOf(y.class, x).";
    }

    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                MethodMatcher junit5Matcher = new MethodMatcher("org.junit.jupiter.api.Assertions assertTrue(boolean, ..)");
                MethodMatcher junit4Matcher = new MethodMatcher("org.junit.Assert assertTrue(.., boolean)");

                J clazz;
                Expression expression;
                Expression reason;

                if (junit5Matcher.matches(mi)) {
                    maybeRemoveImport("org.junit.jupiter.api.Assertions.assertTrue");
                    Expression argument = mi.getArguments().get(0);
                    if (mi.getArguments().size() == 1) {
                        reason = null;
                    } else if (mi.getArguments().size() == 2) {
                        reason = mi.getArguments().get(1);
                    } else return mi;

                    if (argument instanceof J.InstanceOf) {
                        J.InstanceOf instanceOf = (J.InstanceOf) argument;
                        expression = instanceOf.getExpression();
                        clazz = instanceOf.getClazz();
                    } else {
                        return mi;
                    }
                } else if (junit4Matcher.matches(mi)) {
                    maybeRemoveImport("org.junit.Assert.assertTrue");
                    Expression argument;
                    if (mi.getArguments().size() == 1) {
                        reason = null;
                        argument = mi.getArguments().get(0);
                    } else if (mi.getArguments().size() == 2) {
                        reason = mi.getArguments().get(0);
                        argument = mi.getArguments().get(1);
                    } else return mi;

                    if (argument instanceof J.InstanceOf) {
                        J.InstanceOf instanceOf = (J.InstanceOf) argument;
                        expression = instanceOf.getExpression();
                        clazz = instanceOf.getClazz();
                    } else {
                        return mi;
                    }
                } else {
                    return mi;
                }


                JavaTemplate template = JavaTemplate
                    .builder("assertInstanceOf(#{}.class, #{any(java.lang.Object)}" + (reason != null ? ", #{any(java.lang.String)})" : ")"))
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5.9", "junit-4.13"))
                    .staticImports("org.junit.jupiter.api.Assertions.assertInstanceOf")
                    .build();

                J.MethodInvocation methodd = reason != null
                    ? template.apply(getCursor(), mi.getCoordinates().replace(), clazz.toString(), expression, reason)
                    : template.apply(getCursor(), mi.getCoordinates().replace(), clazz.toString(), expression);
                maybeAddImport("org.junit.jupiter.api.Assertions", "assertInstanceOf");
                return methodd;
            }
        };
    }
}
