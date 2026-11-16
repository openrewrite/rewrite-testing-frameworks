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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;
import java.util.stream.Collectors;

import static org.openrewrite.Tree.randomId;

/**
 * A {@code suite()} method with a JUnit 4 equivalent goes: one that wraps the class in a {@code TestSetup} becomes
 * {@code @BeforeClass} and {@code @AfterClass} methods, and one that collects other test classes becomes
 * {@code @RunWith(Suite.class)}.
 */
class SuiteVisitor extends JUnit3MigrationVisitor {

    private static final AnnotationMatcher RUN_WITH = new AnnotationMatcher("@org.junit.runner.RunWith");
    private static final Comparator<J.Annotation> AFTER_OVERRIDE =
            Comparator.comparing(a -> "Override".equals(a.getSimpleName()) ? "" : a.getSimpleName());

    SuiteVisitor(JUnit3MigrationPlan plan) {
        super(plan);
    }

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
        String fqn = fqn(classDecl);
        SuiteShape suite = fqn != null && plan.suiteKindByFqn.containsKey(fqn) && !plan.isUnmigrated(fqn) ? suiteOf(classDecl) : null;
        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
        if (suite == null) {
            return cd;
        }
        if (plan.isMigrated(fqn)) {
            return suite.kind == SuiteShape.Kind.SETUP ? addOneTimeLifecycleMethods(cd, suite) : cd;
        }
        if (suite.kind == SuiteShape.Kind.AGGREGATE) {
            updateCursor(cd);
            cd = publicMemberSuiteClasses(cd, suite);
            updateCursor(cd);
            return addSuiteAnnotations(cd, suite);
        }
        return cd;
    }

    @Override
    public J.@Nullable MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
        if (SuiteShape.isSuiteMethod(method) && plan.suiteMethodIds.contains(method.getId()) &&
            plan.removesSuite(fqn(directlyDeclaringClass()))) {
            return null;
        }
        return super.visitMethodDeclaration(method, ctx);
    }

    private static @Nullable SuiteShape suiteOf(J.ClassDeclaration cd) {
        for (Statement s : cd.getBody().getStatements()) {
            if (s instanceof J.MethodDeclaration && SuiteShape.isSuiteMethod((J.MethodDeclaration) s)) {
                return SuiteShape.analyze((J.MethodDeclaration) s, fqn(cd));
            }
        }
        return null;
    }

    /**
     * A suite runner reaches a {@code suite()} method by reflection, which needs its class to be accessible.
     */
    private static J.ClassDeclaration publicMemberSuiteClasses(J.ClassDeclaration cd, SuiteShape suite) {
        Set<String> listed = new HashSet<>();
        for (Expression literal : suite.classes) {
            String qualified = memberClassLiteral(cd, literal);
            if (qualified != null) {
                listed.add(qualified.substring(cd.getSimpleName().length() + 1, qualified.length() - ".class".length()));
            }
        }
        if (listed.isEmpty()) {
            return cd;
        }
        return cd.withBody(cd.getBody().withStatements(ListUtils.map(cd.getBody().getStatements(), st -> {
            if (!(st instanceof J.ClassDeclaration) || !listed.contains(((J.ClassDeclaration) st).getSimpleName()) ||
                ((J.ClassDeclaration) st).hasModifier(J.Modifier.Type.Public)) {
                return st;
            }
            J.ClassDeclaration member = (J.ClassDeclaration) st;
            List<J.Modifier> modifiers = ListUtils.map(member.getModifiers(),
                    m -> m.getType() == J.Modifier.Type.Private || m.getType() == J.Modifier.Type.Protected ? m.withType(J.Modifier.Type.Public) : m);
            if (!member.hasModifier(J.Modifier.Type.Private) && !member.hasModifier(J.Modifier.Type.Protected)) {
                J.Modifier pub = new J.Modifier(randomId(), Space.EMPTY, Markers.EMPTY, null, J.Modifier.Type.Public, Collections.emptyList());
                if (modifiers.isEmpty()) {
                    return member.withModifiers(Collections.singletonList(pub)).getPadding()
                            .withKind(member.getPadding().getKind().withPrefix(Space.SINGLE_SPACE));
                }
                pub = pub.withPrefix(modifiers.get(0).getPrefix());
                modifiers = ListUtils.concat(pub, ListUtils.mapFirst(modifiers, m -> m.withPrefix(Space.SINGLE_SPACE)));
            }
            return member.withModifiers(modifiers);
        })));
    }

    private J.ClassDeclaration addSuiteAnnotations(J.ClassDeclaration cd, SuiteShape suite) {
        if (cd.getLeadingAnnotations().stream().anyMatch(RUN_WITH::matches)) {
            return cd;
        }
        maybeAddImport("org.junit.runner.RunWith");
        maybeAddImport("org.junit.runners.Suite");
        // the annotation sits outside the class body, so a member class of this class needs its outer class's name
        List<String> parts = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        for (Expression literal : suite.classes) {
            String qualified = memberClassLiteral(cd, literal);
            if (qualified != null) {
                parts.add(qualified);
            } else {
                parts.add("#{any()}");
                args.add(literal);
            }
        }
        return template("@RunWith(Suite.class)\n@Suite.SuiteClasses({" + String.join(", ", parts) + "})")
                .imports("org.junit.runner.RunWith", "org.junit.runners.Suite").build()
                .apply(getCursor(), cd.getCoordinates().addAnnotation(AFTER_OVERRIDE), args.toArray());
    }

    private static @Nullable String memberClassLiteral(J.ClassDeclaration outer, Expression literal) {
        if (!(literal instanceof J.FieldAccess) || ((J.FieldAccess) literal).getTarget() instanceof J.FieldAccess ||
            outer.getType() == null) {
            return null;
        }
        JavaType.FullyQualified target = TypeUtils.asFullyQualified(((J.FieldAccess) literal).getTarget().getType());
        if (target == null || target.getOwningClass() == null ||
            !target.getOwningClass().getFullyQualifiedName().equals(outer.getType().getFullyQualifiedName())) {
            return null;
        }
        return outer.getSimpleName() + "." + target.getClassName().substring(target.getClassName().lastIndexOf('.') + 1) + ".class";
    }

    /**
     * What a {@code TestSetup} did around a suite is done once per class, and in JUnit 4 that is {@code @BeforeClass}.
     */
    private J.ClassDeclaration addOneTimeLifecycleMethods(J.ClassDeclaration cd, SuiteShape suite) {
        J.ClassDeclaration result = cd;
        if (suite.setUp != null) {
            result = addOneTimeMethod(result, suite.setUp, "BeforeClass", "oneTimeSetUp");
        }
        if (suite.tearDown != null) {
            result = addOneTimeMethod(result, suite.tearDown, "AfterClass", "oneTimeTearDown");
        }
        return result;
    }

    private J.ClassDeclaration addOneTimeMethod(J.ClassDeclaration cd, J.MethodDeclaration original, String annotation, String name) {
        J.Block body = (J.Block) new JavaIsoVisitor<Integer>() {
            @Override
            public J.@Nullable MethodInvocation visitMethodInvocation(J.MethodInvocation m, Integer p) {
                if (m.getSelect() instanceof J.Identifier && "super".equals(((J.Identifier) m.getSelect()).getSimpleName()) &&
                    ("setUp".equals(m.getSimpleName()) || "tearDown".equals(m.getSimpleName()))) {
                    return null;
                }
                return super.visitMethodInvocation(m, p);
            }
        }.visit(original.getBody(), 0, getCursor());
        String throwsClause = original.getThrows() == null || original.getThrows().isEmpty() ? "" :
                " throws " + original.getThrows().stream().map(t -> t.printTrimmed(getCursor())).collect(Collectors.joining(", "));
        String unique = name;
        for (int i = 2; hasMethod(cd, unique); i++) {
            unique = name + i;
        }
        maybeAddImport("org.junit." + annotation);
        J.Block block = cd.getBody();
        return cd.withBody(template("@" + annotation + "\npublic static void " + unique + "()" + throwsClause + " " + body.printTrimmed(getCursor()))
                .imports("org.junit." + annotation).contextSensitive().build()
                .apply(new Cursor(getCursor(), block), block.getCoordinates().lastStatement()));
    }

    private static boolean hasMethod(J.ClassDeclaration cd, String name) {
        for (Statement s : cd.getBody().getStatements()) {
            if (s instanceof J.MethodDeclaration && ((J.MethodDeclaration) s).getSimpleName().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
