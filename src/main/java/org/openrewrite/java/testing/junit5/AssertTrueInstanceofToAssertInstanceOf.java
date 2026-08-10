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
import org.jspecify.annotations.Nullable;
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
import org.openrewrite.java.tree.TypeUtils;
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
                Expression select = mi.getSelect();
                Expression retainedSelect;
                String owner;

                if (junit5Matcher.matches(mi)) {
                    maybeRemoveImport("org.junit.jupiter.api.Assertions.assertTrue");
                    // Reuse the selector only when it names the `Assertions` class itself; there
                    // `assertInstanceOf` necessarily resolves to JUnit's declaration. A subtype can
                    // hide `assertInstanceOf` with a declaration of its own, and an instance-typed
                    // selector resolves against the variable's static type, so both fall back to
                    // the unqualified call with a static import, as for an unqualified input.
                    retainedSelect = isAssertionsClassReference(select) ? select : null;
                    owner = retainedSelect == null ? "" : "Assertions.";
                    if (retainedSelect == null && select != null) {
                        JavaType.FullyQualified selectType = TypeUtils.asFullyQualified(select.getType());
                        if (selectType != null) {
                            maybeRemoveImport(selectType.getFullyQualifiedName());
                        }
                    }
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
                    // The selector denotes `Assert`, which does not declare `assertInstanceOf`. A
                    // qualified call keeps an explicit owner as the fully qualified
                    // `org.junit.jupiter.api.Assertions.assertInstanceOf`, because a bare
                    // `Assertions` simple name could itself be shadowed by a member type, an
                    // inherited member type, a field, or a same-package type.
                    retainedSelect = null;
                    owner = select == null ? "" : "org.junit.jupiter.api.Assertions.";
                    if (select != null) {
                        maybeRemoveImport("org.junit.Assert");
                    }
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


                // An unqualified call is left unqualified; a same-named local declaration can still capture
                // it, which this change does not address (see the PR description).
                JavaTemplate.Builder templateBuilder = JavaTemplate
                    .builder(owner + "assertInstanceOf(#{any(java.lang.Class)}, #{any(java.lang.Object)}" + (reason != null ? ", #{any(java.lang.String)})" : ")"))
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5", "junit-4"));
                if (owner.isEmpty()) {
                    templateBuilder.staticImports("org.junit.jupiter.api.Assertions.assertInstanceOf");
                    maybeAddImport("org.junit.jupiter.api.Assertions", "assertInstanceOf");
                } else if (retainedSelect != null) {
                    templateBuilder.imports("org.junit.jupiter.api.Assertions");
                }

                TypedTree rawClazz = clazz instanceof J.ParameterizedType ? ((J.ParameterizedType) clazz).getClazz() : clazz;
                Expression classLiteral = toClassLiteral(rawClazz);
                JavaTemplate template = templateBuilder.build();
                J.MethodInvocation replacement = reason != null ?
                    template.apply(getCursor(), mi.getCoordinates().replace(), classLiteral, expression, reason) :
                    template.apply(getCursor(), mi.getCoordinates().replace(), classLiteral, expression);
                return retainedSelect == null ? replacement : replacement.withSelect(retainedSelect.withPrefix(Space.EMPTY));
            }

            /**
             * True only when the selector is a reference to the {@code org.junit.jupiter.api.Assertions}
             * class itself, spelled as a simple or fully qualified type name. A reference to a subtype or
             * to a variable is rejected, because the emitted {@code assertInstanceOf} would resolve
             * against that type, which may hide JUnit's method with a declaration of its own.
             */
            private boolean isAssertionsClassReference(@Nullable Expression select) {
                if (select instanceof J.Identifier) {
                    return ((J.Identifier) select).getFieldType() == null &&
                        TypeUtils.isOfClassType(select.getType(), "org.junit.jupiter.api.Assertions");
                }
                if (select instanceof J.FieldAccess) {
                    return ((J.FieldAccess) select).getName().getFieldType() == null &&
                        TypeUtils.isOfClassType(select.getType(), "org.junit.jupiter.api.Assertions");
                }
                return false;
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
