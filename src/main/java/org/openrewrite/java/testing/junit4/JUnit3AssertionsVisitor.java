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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;

import static java.util.Collections.emptyList;
import static org.openrewrite.Tree.randomId;

/**
 * Retargets the static assertion methods of {@code junit.framework.Assert} to {@code org.junit.Assert}.
 * Only calls are retargeted, so that other references to {@code TestCase} are left as they are.
 */
class JUnit3AssertionsVisitor extends JavaIsoVisitor<ExecutionContext> {

    private static final String JUNIT3_ASSERT = "junit.framework.Assert";
    private static final String JUNIT4_ASSERT = "org.junit.Assert";
    private static final MethodMatcher JUNIT3_ASSERT_MATCHER = new MethodMatcher(JUNIT3_ASSERT + " *(..)", true);
    private static final Set<String> SUPPORTED = new HashSet<>(Arrays.asList(
            "assertEquals", "assertNotNull", "assertNull", "assertSame", "assertNotSame",
            "assertTrue", "assertFalse", "fail"));

    private static final JavaType.ShallowClass JUNIT4_ASSERT_TYPE = JavaType.ShallowClass.build(JUNIT4_ASSERT);

    /**
     * JUnit 3 assertions throw {@code junit.framework.AssertionFailedError}, whereas those of JUnit 4 throw
     * {@code java.lang.AssertionError}, so a catch of the former would no longer catch a failed assertion.
     */
    boolean migrateFailureTypes;

    @Override
    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
        migrateFailureTypes = false;
        J.CompilationUnit c = super.visitCompilationUnit(cu, ctx);
        if (migrateFailureTypes) {
            doAfterVisit(new ChangeType("junit.framework.AssertionFailedError", "java.lang.AssertionError", true).getVisitor());
            doAfterVisit(new ChangeType("junit.framework.ComparisonFailure", "org.junit.ComparisonFailure", true).getVisitor());
        }
        return c;
    }

    /**
     * Whether the assertion at the cursor is retargeted.
     */
    boolean retargets() {
        return true;
    }

    @Override
    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
        J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
        JavaType.Method type = m.getMethodType();
        if (type == null || !retargets() || !SUPPORTED.contains(m.getSimpleName()) || !JUNIT3_ASSERT_MATCHER.matches(m) ||
            !type.hasFlags(Flag.Static)) {
            return m;
        }
        migrateFailureTypes = true;
        m = addDeltaToFloatingPointComparison(m);
        maybeRemoveImport(JUNIT3_ASSERT);
        maybeRemoveImport(JUNIT3_ASSERT + "." + m.getSimpleName());

        JavaType.Method retargeted = m.getMethodType() == null ? null : m.getMethodType().withDeclaringType(JUNIT4_ASSERT_TYPE);
        if (m.getSelect() == null && !hidesStaticImport(m.getSimpleName())) {
            maybeAddImport(JUNIT4_ASSERT, m.getSimpleName(), false);
            return m.withMethodType(retargeted).withName(m.getName().withType(retargeted));
        }
        Space prefix = m.getSelect() == null ? m.getPrefix() : m.getSelect().getPrefix();
        J.Identifier select = new J.Identifier(randomId(), prefix, Markers.EMPTY, emptyList(), "Assert", JUNIT4_ASSERT_TYPE, null);
        maybeAddImport(JUNIT4_ASSERT);
        if (m.getSelect() == null) {
            m = m.withPrefix(Space.EMPTY);
        }
        return m.getPadding().withSelect(JRightPadded.build((Expression) select))
                .withMethodType(retargeted).withName(m.getName().withType(retargeted));
    }

    /**
     * A method of the same name declared in the enclosing class or inherited from something other than JUnit 3
     * hides a static import of every overload of that name.
     */
    private boolean hidesStaticImport(String name) {
        for (Cursor c = getCursor(); c != null; c = c.getParent()) {
            Object value = c.getValue();
            if (value instanceof J.ClassDeclaration) {
                for (JavaType.FullyQualified t = ((J.ClassDeclaration) value).getType(); t != null; t = t.getSupertype()) {
                    if (t.getFullyQualifiedName().startsWith("junit.framework.") || "java.lang.Object".equals(t.getFullyQualifiedName())) {
                        continue;
                    }
                    for (JavaType.Method declared : t.getMethods()) {
                        if (declared.getName().equals(name)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * JUnit 3 has no {@code assertEquals(double, double)}, so such a call compared the boxed values.
     * JUnit 4 does have one, deprecated, and it fails unconditionally.
     */
    private J.MethodInvocation addDeltaToFloatingPointComparison(J.MethodInvocation m) {
        if (!"assertEquals".equals(m.getSimpleName())) {
            return m;
        }
        List<Expression> args = m.getArguments();
        int first = args.size() == 3 && TypeUtils.isString(args.get(0).getType()) ? 1 : 0;
        if (args.size() - first != 2) {
            return m;
        }
        m = castPrimitiveComparedToBoxedFloatingPoint(m, first);
        args = m.getArguments();
        JavaType.Primitive a = floatingPoint(args.get(first).getType());
        JavaType.Primitive b = floatingPoint(args.get(first + 1).getType());
        if (a == null || b == null) {
            return m;
        }
        boolean isFloat = a == JavaType.Primitive.Float && b == JavaType.Primitive.Float;
        J.Literal delta = new J.Literal(randomId(), Space.SINGLE_SPACE, Markers.EMPTY,
                isFloat ? (Object) 0.0f : (Object) 0.0d, isFloat ? "0.0f" : "0.0",
                null, isFloat ? JavaType.Primitive.Float : JavaType.Primitive.Double);
        List<Expression> newArgs = new ArrayList<>(args);
        newArgs.add(delta);
        return m.withArguments(newArgs);
    }

    /**
     * JUnit 3 compared a primitive with a boxed {@code Double} or {@code Float} as two objects. With
     * {@code assertEquals(double, double)} in JUnit 4 such a call is ambiguous, so the primitive is cast to
     * {@code Object}, which keeps the comparison of two boxed values.
     */
    private J.MethodInvocation castPrimitiveComparedToBoxedFloatingPoint(J.MethodInvocation m, int first) {
        List<Expression> args = m.getArguments();
        Expression left = args.get(first);
        Expression right = args.get(first + 1);
        int cast = isBoxedFloatingPoint(right.getType()) && left.getType() instanceof JavaType.Primitive &&
                   isNumeric((JavaType.Primitive) left.getType()) ? first :
                isBoxedFloatingPoint(left.getType()) && right.getType() instanceof JavaType.Primitive &&
                isNumeric((JavaType.Primitive) right.getType()) ? first + 1 : -1;
        if (cast < 0) {
            return m;
        }
        Expression arg = args.get(cast);
        JavaType.ShallowClass object = JavaType.ShallowClass.build("java.lang.Object");
        J.TypeCast typeCast = new J.TypeCast(randomId(), arg.getPrefix(), Markers.EMPTY,
                new J.ControlParentheses<>(randomId(), Space.EMPTY, Markers.EMPTY, JRightPadded.<TypeTree>build(
                        new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), "Object", object, null))),
                arg.withPrefix(Space.SINGLE_SPACE));
        List<Expression> newArgs = new ArrayList<>(args);
        newArgs.set(cast, typeCast);
        return m.withArguments(newArgs);
    }

    private static boolean isBoxedFloatingPoint(@Nullable JavaType type) {
        return TypeUtils.isOfClassType(type, "java.lang.Double") || TypeUtils.isOfClassType(type, "java.lang.Float");
    }

    private static boolean isNumeric(JavaType.Primitive type) {
        return type != JavaType.Primitive.Boolean && type != JavaType.Primitive.String && type != JavaType.Primitive.Null &&
               type != JavaType.Primitive.Void && type != JavaType.Primitive.None;
    }

    private static JavaType.@Nullable Primitive floatingPoint(@Nullable JavaType type) {
        return type == JavaType.Primitive.Double || type == JavaType.Primitive.Float ? (JavaType.Primitive) type : null;
    }
}
