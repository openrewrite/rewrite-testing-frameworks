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
package org.openrewrite.java.testing.junit5;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypedTree;
import org.openrewrite.marker.Markers;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public class AssertTrueInstanceofToAssertInstanceOf extends Recipe {
    @Getter
    final String displayName = "`assertTrue(x instanceof y)` to `assertInstanceOf(y.class, x)`";

    @Getter
    final String description = "Migration of JUnit4 (or potentially JUnit5) test case in form of assertTrue(x instanceof y) to assertInstanceOf(y.class, x).";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                MethodMatcher junit5Matcher = new MethodMatcher("org.junit.jupiter.api.Assertions assertTrue(boolean, ..)");
                MethodMatcher junit4Matcher = new MethodMatcher("org.junit.Assert assertTrue(.., boolean)");

                TypedTree clazz;
                Expression expression;
                Expression reason;

                if (junit5Matcher.matches(mi)) {
                    maybeRemoveImport("org.junit.jupiter.api.Assertions.assertTrue");
                    Expression argument = mi.getArguments().get(0);
                    if (mi.getArguments().size() == 1) {
                        reason = null;
                    } else if (mi.getArguments().size() == 2) {
                        reason = mi.getArguments().get(1);
                    } else {
                        return mi;
                    }

                    if (argument instanceof J.InstanceOf) {
                        J.InstanceOf instanceOf = (J.InstanceOf) argument;
                        expression = instanceOf.getExpression();
                        clazz = (TypedTree) instanceOf.getClazz();
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
                    } else {
                        return mi;
                    }

                    if (argument instanceof J.InstanceOf) {
                        J.InstanceOf instanceOf = (J.InstanceOf) argument;
                        expression = instanceOf.getExpression();
                        clazz = (TypedTree) instanceOf.getClazz();
                    } else {
                        return mi;
                    }
                } else {
                    return mi;
                }


                JavaTemplate template = JavaTemplate
                    .builder("assertInstanceOf(#{any(java.lang.Class)}, #{any(java.lang.Object)}" + (reason != null ? ", #{any(java.lang.String)})" : ")"))
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5", "junit-4"))
                    .staticImports("org.junit.jupiter.api.Assertions.assertInstanceOf")
                    .build();

                maybeAddImport("org.junit.jupiter.api.Assertions", "assertInstanceOf");
                TypedTree rawClazz = clazz instanceof J.ParameterizedType ? ((J.ParameterizedType) clazz).getClazz() : clazz;
                Expression classLiteral = toClassLiteral(rawClazz);
                return reason != null ?
                    template.apply(getCursor(), mi.getCoordinates().replace(), classLiteral, expression, reason) :
                    template.apply(getCursor(), mi.getCoordinates().replace(), classLiteral, expression);
            }

            private Expression toClassLiteral(TypedTree clazz) {
                JavaType clazzType = clazz.getType();
                JavaType.Parameterized classType = new JavaType.Parameterized(null,
                    JavaType.ShallowClass.build("java.lang.Class"),
                    singletonList(clazzType != null ? clazzType : JavaType.Unknown.getInstance()));
                J.Identifier classKeyword = new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY,
                    emptyList(), "class", classType, null);
                Expression target = ((Expression) clazz).withPrefix(Space.EMPTY);
                return new J.FieldAccess(Tree.randomId(), Space.EMPTY, Markers.EMPTY,
                    target, JLeftPadded.build(classKeyword), classType);
            }
        };
    }
}
