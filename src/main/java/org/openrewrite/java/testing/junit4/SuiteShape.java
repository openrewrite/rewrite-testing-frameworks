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
package org.openrewrite.java.testing.junit4;

import org.jspecify.annotations.Nullable;
import org.openrewrite.Tree;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;

/**
 * The shapes of a JUnit 3 {@code public static Test suite()} method that have a JUnit 4 equivalent.
 */
final class SuiteShape {

    enum Kind {
        /**
         * {@code return new TestSuite(ThisTest.class);} which JUnit 4 does on its own.
         */
        SELF,
        /**
         * {@code return new TestSetup(new TestSuite(ThisTest.class)) { setUp, tearDown };}
         */
        SETUP,
        /**
         * A suite of other test classes, which is {@code @RunWith(Suite.class)} in JUnit 4.
         */
        AGGREGATE
    }

    final Kind kind;
    /**
     * The class literals of the test classes of the suite, in order.
     */
    final List<Expression> classes;
    /**
     * Ids of the class literals and {@code suite()} calls that the shape takes care of.
     */
    final Set<UUID> consumedIds = new HashSet<>();
    final J.@Nullable MethodDeclaration setUp;
    final J.@Nullable MethodDeclaration tearDown;

    private SuiteShape(Kind kind, List<Expression> classes, J.@Nullable MethodDeclaration setUp, J.@Nullable MethodDeclaration tearDown) {
        this.kind = kind;
        this.classes = classes;
        this.setUp = setUp;
        this.tearDown = tearDown;
    }

    static boolean isSuiteMethod(J.MethodDeclaration md) {
        return "suite".equals(md.getSimpleName()) && md.hasModifier(J.Modifier.Type.Static) &&
               md.getParameters().stream().allMatch(p -> p instanceof J.Empty) && md.getBody() != null &&
               md.getMethodType() != null &&
               (TypeUtils.isOfClassType(md.getMethodType().getReturnType(), "junit.framework.Test") ||
                TypeUtils.isOfClassType(md.getMethodType().getReturnType(), "junit.framework.TestSuite"));
    }

    static @Nullable SuiteShape analyze(J.MethodDeclaration suite, @Nullable String enclosingClass) {
        List<Statement> statements = suite.getBody().getStatements();
        if (statements.isEmpty()) {
            return null;
        }
        Statement last = statements.get(statements.size() - 1);
        if (!(last instanceof J.Return) || ((J.Return) last).getExpression() == null) {
            return null;
        }
        Expression returned = ((J.Return) last).getExpression().unwrap();
        Set<UUID> consumedIds = new HashSet<>();

        if (statements.size() == 1 && returned instanceof J.NewClass) {
            J.NewClass nc = (J.NewClass) returned;
            if (isType(nc, "junit.extensions.TestSetup") && nc.getArguments().size() == 1 && nc.getBody() != null) {
                List<Expression> inner = suiteOfClasses(nc.getArguments().get(0), consumedIds);
                if (inner == null || inner.size() != 1 || !refersTo(inner.get(0), enclosingClass)) {
                    return null;
                }
                J.MethodDeclaration setUp = null;
                J.MethodDeclaration tearDown = null;
                for (Statement s : nc.getBody().getStatements()) {
                    if (!(s instanceof J.MethodDeclaration) || !((J.MethodDeclaration) s).getParameters().stream().allMatch(p -> p instanceof J.Empty)) {
                        return null;
                    }
                    J.MethodDeclaration m = (J.MethodDeclaration) s;
                    if ("setUp".equals(m.getSimpleName()) && setUp == null) {
                        setUp = m;
                    } else if ("tearDown".equals(m.getSimpleName()) && tearDown == null) {
                        tearDown = m;
                    } else {
                        return null;
                    }
                }
                SuiteShape shape = new SuiteShape(Kind.SETUP, inner, setUp, tearDown);
                shape.consumedIds.addAll(consumedIds);
                return shape;
            }
            List<Expression> classes = suiteOfClasses(returned, consumedIds);
            if (classes == null) {
                return null;
            }
            return of(classes, enclosingClass, consumedIds);
        }

        // TestSuite suite = new TestSuite(); suite.addTestSuite(A.class); ...; return suite;
        if (!(statements.get(0) instanceof J.VariableDeclarations)) {
            return null;
        }
        J.VariableDeclarations vd = (J.VariableDeclarations) statements.get(0);
        if (vd.getVariables().size() != 1 || vd.getVariables().get(0).getInitializer() == null) {
            return null;
        }
        String var = vd.getVariables().get(0).getSimpleName();
        Expression init = vd.getVariables().get(0).getInitializer().unwrap();
        List<Expression> classes = new ArrayList<>();
        if (init instanceof J.NewClass && isType((J.NewClass) init, "junit.framework.TestSuite") && ((J.NewClass) init).getBody() == null) {
            // TestSuite suite = new TestSuite(A.class); return suite;
            List<Expression> initial = suiteOfClasses(init, consumedIds);
            if (initial != null) {
                classes.addAll(initial);
            } else if (!((J.NewClass) init).getArguments().stream().allMatch(a -> a instanceof J.Empty || a.unwrap() instanceof J.Literal)) {
                return null;
            }
        } else {
            return null;
        }
        if (!(returned instanceof J.Identifier) || !var.equals(((J.Identifier) returned).getSimpleName())) {
            return null;
        }
        for (Statement s : statements.subList(1, statements.size() - 1)) {
            if (!(s instanceof J.MethodInvocation)) {
                return null;
            }
            J.MethodInvocation add = (J.MethodInvocation) s;
            if (!(add.getSelect() instanceof J.Identifier) || !var.equals(((J.Identifier) add.getSelect()).getSimpleName()) ||
                add.getArguments().size() != 1) {
                return null;
            }
            Expression arg = add.getArguments().get(0).unwrap();
            if ("addTestSuite".equals(add.getSimpleName())) {
                Expression literal = classLiteral(arg, consumedIds);
                if (literal == null) {
                    return null;
                }
                classes.add(literal);
            } else if ("addTest".equals(add.getSimpleName())) {
                if (arg instanceof J.MethodInvocation && "suite".equals(((J.MethodInvocation) arg).getSimpleName()) &&
                    ((J.MethodInvocation) arg).getArguments().stream().allMatch(a -> a instanceof J.Empty) &&
                    ((J.MethodInvocation) arg).getSelect() != null) {
                    consumedIds.add(arg.getId());
                    classes.add(classLiteralOf(((J.MethodInvocation) arg).getSelect()));
                } else {
                    List<Expression> inner = suiteOfClasses(arg, consumedIds);
                    if (inner == null) {
                        return null;
                    }
                    classes.addAll(inner);
                }
            } else {
                return null;
            }
        }
        if (classes.isEmpty()) {
            return null;
        }
        return of(classes, enclosingClass, consumedIds);
    }

    private static @Nullable SuiteShape of(List<Expression> classes, @Nullable String enclosingClass, Set<UUID> consumedIds) {
        SuiteShape shape;
        if (classes.size() == 1 && enclosingClass != null && refersTo(classes.get(0), enclosingClass)) {
            shape = new SuiteShape(Kind.SELF, classes, null, null);
        } else {
            shape = new SuiteShape(Kind.AGGREGATE, classes, null, null);
        }
        shape.consumedIds.addAll(consumedIds);
        return shape;
    }

    /**
     * {@code new TestSuite(A.class, B.class)}, {@code new JUnit4TestAdapter(A.class)}
     */
    private static @Nullable List<Expression> suiteOfClasses(Expression e, Set<UUID> consumedIds) {
        Expression expr = e.unwrap();
        if (!(expr instanceof J.NewClass) || ((J.NewClass) expr).getBody() != null) {
            return null;
        }
        J.NewClass nc = (J.NewClass) expr;
        if (!isType(nc, "junit.framework.TestSuite") && !isType(nc, "junit.framework.JUnit4TestAdapter")) {
            return null;
        }
        List<Expression> classes = new ArrayList<>();
        for (Expression arg : nc.getArguments()) {
            if (arg instanceof J.Empty) {
                continue;
            }
            Expression literal = classLiteral(arg.unwrap(), consumedIds);
            if (literal == null) {
                return null;
            }
            classes.add(literal);
        }
        return classes.isEmpty() ? null : classes;
    }

    private static @Nullable Expression classLiteral(Expression e, Set<UUID> consumedIds) {
        if (e instanceof J.FieldAccess && "class".equals(((J.FieldAccess) e).getSimpleName())) {
            consumedIds.add(e.getId());
            return e.withPrefix(Space.EMPTY);
        }
        return null;
    }

    /**
     * {@code Foo.class} for the expression {@code Foo}
     */
    static Expression classLiteralOf(Expression type) {
        return new J.FieldAccess(Tree.randomId(), Space.EMPTY, Markers.EMPTY, type.withPrefix(Space.EMPTY),
                JLeftPadded.build(new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, Collections.emptyList(), "class", null, null)),
                JavaType.ShallowClass.build("java.lang.Class"));
    }

    private static boolean refersTo(Expression classLiteral, @Nullable String fqn) {
        JavaType.FullyQualified t = classLiteral instanceof J.FieldAccess ? TypeUtils.asFullyQualified(((J.FieldAccess) classLiteral).getTarget().getType()) : null;
        return t != null && t.getFullyQualifiedName().equals(fqn);
    }

    private static boolean isType(J.NewClass nc, String fqn) {
        return nc.getClazz() != null && TypeUtils.isOfClassType(nc.getClazz().getType(), fqn);
    }

    static String simpleName(@Nullable String fqn) {
        if (fqn == null) {
            return "";
        }
        return fqn.substring(fqn.lastIndexOf('.') + 1).replace('$', '.');
    }
}
