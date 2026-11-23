/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.java.testing.selenium;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public class ReplaceFluentWaitUntilPredicate extends Recipe {

    private static final String PREDICATE = "com.google.common.base.Predicate";
    private static final MethodMatcher UNTIL_PREDICATE = new MethodMatcher(
            "org.openqa.selenium.support.ui.FluentWait until(" + PREDICATE + ")");
    private static final JavaType.FullyQualified FUNCTION = JavaType.ShallowClass.build("java.util.function.Function");

    @Getter
    final String displayName = "Wait on a `Function` rather than a Guava `Predicate`";

    @Getter
    final String description = "Selenium 3 removed the `FluentWait.until(Predicate)` overload of Selenium 2, which " +
            "took a Guava `Predicate`. An anonymous `Predicate` becomes a lambda, a lambda or method reference loses " +
            "any cast to `Predicate`, and any other `Predicate` is passed as a reference to its `apply` method.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(UNTIL_PREDICATE), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (!UNTIL_PREDICATE.matches(m)) {
                    return m;
                }
                Expression predicate = m.getArguments().get(0);
                Expression function = asFunction(predicate, getCursor());
                maybeRemoveImport(PREDICATE);
                // Such as the type argument of `Predicate<WebDriver>`, which the lambda no longer names
                for (JavaType.FullyQualified named : namedTypes(predicate)) {
                    maybeRemoveImport(named);
                }
                JavaType.Method type = m.getMethodType() == null ? null :
                        m.getMethodType().withParameterTypes(singletonList(FUNCTION));
                return SeleniumDurations.withArguments(m, singletonList(function))
                        .withName(m.getName().withType(type))
                        .withMethodType(type);
            }
        });
    }

    private static Expression asFunction(Expression predicate, Cursor cursor) {
        Expression unwrapped = predicate;
        while (unwrapped instanceof J.TypeCast ||
               unwrapped instanceof J.Parentheses && ((J.Parentheses<?>) unwrapped).getTree() instanceof Expression) {
            unwrapped = unwrapped instanceof J.TypeCast ? ((J.TypeCast) unwrapped).getExpression() :
                    (Expression) ((J.Parentheses<?>) unwrapped).getTree();
        }
        // A lambda or a method reference, which Selenium 2 code passes with explicit parameter types or as an exact
        // reference to a method returning `boolean`, takes `Function` as its target type as well
        if (unwrapped instanceof J.Lambda || unwrapped instanceof J.MemberReference) {
            return unwrapped.withPrefix(predicate.getPrefix());
        }
        if (predicate instanceof J.NewClass) {
            J.Lambda lambda = asLambda((J.NewClass) predicate, cursor);
            if (lambda != null) {
                return lambda;
            }
        }
        return applyReference(predicate);
    }

    /**
     * An anonymous `Predicate` that declares nothing but `apply`, and does not refer to itself, as a lambda. Unlike
     * the method of an anonymous class, a lambda cannot declare a name that a local variable around it already has.
     */
    private static J.@Nullable Lambda asLambda(J.NewClass newClass, Cursor cursor) {
        J.Block classBody = newClass.getBody();
        if (classBody == null || classBody.getStatements().size() != 1 ||
            !(classBody.getStatements().get(0) instanceof J.MethodDeclaration)) {
            return null;
        }
        J.MethodDeclaration apply = (J.MethodDeclaration) classBody.getStatements().get(0);
        J.Block body = apply.getBody();
        if (!"apply".equals(apply.getSimpleName()) || body == null || apply.getParameters().size() != 1 ||
            !(apply.getParameters().get(0) instanceof J.VariableDeclarations) || refersToThis(body)) {
            return null;
        }
        Set<String> declared = declaredNames(apply, null);
        J scope = cursor.firstEnclosing(J.MethodDeclaration.class);
        if (scope == null) {
            scope = cursor.firstEnclosing(J.Block.class);
        }
        if (scope != null && !Collections.disjoint(declared, declaredNames(scope, newClass))) {
            return null;
        }
        J.VariableDeclarations parameter = (J.VariableDeclarations) apply.getParameters().get(0);
        J.VariableDeclarations untypedParameter = parameter
                .withLeadingAnnotations(emptyList())
                .withModifiers(emptyList())
                .withTypeExpression(null)
                .withVariables(singletonList(parameter.getVariables().get(0).withPrefix(Space.EMPTY)))
                .withPrefix(Space.EMPTY);

        J lambdaBody = body.withPrefix(Space.SINGLE_SPACE);
        if (body.getStatements().size() == 1 && body.getStatements().get(0) instanceof J.Return &&
            body.getEnd().getComments().isEmpty()) {
            J.Return ret = (J.Return) body.getStatements().get(0);
            if (ret.getExpression() != null && ret.getPrefix().getComments().isEmpty()) {
                lambdaBody = ret.getExpression().withPrefix(Space.SINGLE_SPACE);
            }
        }
        // The body of `apply` is one level deeper than the anonymous class around it, which the lambda replaces
        String classIndent = indentation(classBody.getEnd());
        String methodIndent = indentation(apply.getPrefix());
        if (classIndent != null && methodIndent != null && methodIndent.length() > classIndent.length() &&
            methodIndent.startsWith(classIndent)) {
            lambdaBody = outdent(lambdaBody, methodIndent, classIndent);
        }
        return new J.Lambda(
                Tree.randomId(),
                newClass.getPrefix(),
                Markers.EMPTY,
                new J.Lambda.Parameters(Tree.randomId(), Space.EMPTY, Markers.EMPTY, false,
                        singletonList(JRightPadded.build(untypedParameter))),
                Space.SINGLE_SPACE,
                lambdaBody,
                FUNCTION);
    }

    private static @Nullable String indentation(Space space) {
        String whitespace = space.getComments().isEmpty() ? space.getWhitespace() :
                space.getComments().get(space.getComments().size() - 1).getSuffix();
        int newline = whitespace.lastIndexOf('\n');
        return newline < 0 ? null : whitespace.substring(newline + 1);
    }

    private static J outdent(J tree, String from, String to) {
        return new JavaVisitor<Integer>() {
            @Override
            public Space visitSpace(@Nullable Space space, Space.Location loc, Integer p) {
                if (space == null) {
                    return null;
                }
                return space
                        .withWhitespace(space.getWhitespace().replace("\n" + from, "\n" + to))
                        .withComments(ListUtils.map(space.getComments(),
                                comment -> comment.withSuffix(comment.getSuffix().replace("\n" + from, "\n" + to))));
            }
        }.visitNonNull(tree, 0);
    }

    /**
     * The names of the variables and parameters declared in a tree, other than in the part of it left out.
     */
    private static Set<String> declaredNames(J tree, @Nullable J leftOut) {
        Set<String> names = new HashSet<>();
        new JavaIsoVisitor<Set<String>>() {
            @Override
            public @Nullable J visit(@Nullable Tree t, Set<String> n) {
                return leftOut != null && t != null && t.getId().equals(leftOut.getId()) ? (J) t : super.visit(t, n);
            }

            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, Set<String> n) {
                n.add(variable.getSimpleName());
                return super.visitVariable(variable, n);
            }
        }.visit(tree, names);
        return names;
    }

    private static Set<JavaType.FullyQualified> namedTypes(J tree) {
        Set<JavaType.FullyQualified> named = new HashSet<>();
        new JavaIsoVisitor<Set<JavaType.FullyQualified>>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, Set<JavaType.FullyQualified> n) {
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(identifier.getType());
                if (type != null && identifier.getFieldType() == null) {
                    n.add(type);
                }
                return identifier;
            }
        }.visit(tree, named);
        return named;
    }

    /**
     * Whether the body refers to the predicate itself, as `this` or `super`, or by calling a method of the predicate
     * without saying on what, such as `toString()`, which in a lambda would be the method of the class around it.
     */
    private static boolean refersToThis(J body) {
        AtomicBoolean found = new AtomicBoolean();
        new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, AtomicBoolean f) {
                if ("this".equals(identifier.getSimpleName()) || "super".equals(identifier.getSimpleName())) {
                    f.set(true);
                }
                return identifier;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean f) {
                JavaType.Method type = method.getMethodType();
                if (method.getSelect() == null && type != null &&
                    (TypeUtils.isOfClassType(type.getDeclaringType(), "java.lang.Object") ||
                     TypeUtils.isOfClassType(type.getDeclaringType(), PREDICATE) ||
                     TypeUtils.isOfClassType(type.getDeclaringType(), "java.util.function.Predicate"))) {
                    f.set(true);
                }
                return super.visitMethodInvocation(method, f);
            }
        }.visit(body, found);
        return found.get();
    }

    /**
     * The `apply` method of the predicate, as a method reference.
     */
    private static J.MemberReference applyReference(Expression predicate) {
        Expression containing = predicate.withPrefix(Space.EMPTY);
        if (!(predicate instanceof J.Identifier || predicate instanceof J.FieldAccess ||
              predicate instanceof J.MethodInvocation || predicate instanceof J.NewClass ||
              predicate instanceof J.Parentheses)) {
            containing = new J.Parentheses<>(Tree.randomId(), Space.EMPTY, Markers.EMPTY, JRightPadded.build(containing));
        }
        return new J.MemberReference(
                Tree.randomId(),
                predicate.getPrefix(),
                Markers.EMPTY,
                JRightPadded.build(containing),
                null,
                JLeftPadded.build(new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), "apply", null, null)),
                FUNCTION,
                null,
                null);
    }
}
