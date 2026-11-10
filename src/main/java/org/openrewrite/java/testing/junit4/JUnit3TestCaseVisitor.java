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
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.RemoveImport;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.testing.junit4.MigrateJUnit3TestCase.Accumulator;
import org.openrewrite.java.testing.junit4.table.UnmigratedJUnit3Tests;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;

import static java.util.Collections.singletonList;
import static org.openrewrite.Tree.randomId;

class JUnit3TestCaseVisitor extends JUnit3AssertionsVisitor {

    private static final AnnotationMatcher JUNIT4_TEST = new AnnotationMatcher("@org.junit.Test");
    private static final AnnotationMatcher JUNIT4_BEFORE = new AnnotationMatcher("@org.junit.Before");
    private static final AnnotationMatcher JUNIT4_AFTER = new AnnotationMatcher("@org.junit.After");
    private static final AnnotationMatcher RUN_WITH = new AnnotationMatcher("@org.junit.runner.RunWith");
    private static final AnnotationMatcher OVERRIDE = new AnnotationMatcher("@java.lang.Override");
    private static final Set<String> TEST_RUNNERS = new HashSet<>(Arrays.asList(
            "junit.textui.TestRunner", "junit.swingui.TestRunner", "junit.awtui.TestRunner"));
    private static final Set<String> SUPERTYPES_REMOVED_BY_MIGRATION = new HashSet<>(Arrays.asList(
            "junit.framework.TestCase", "junit.framework.Assert", "junit.framework.Test"));

    private static final Comparator<J.Annotation> AFTER_OVERRIDE =
            Comparator.comparing(a -> "Override".equals(a.getSimpleName()) ? "" : a.getSimpleName());

    private final Accumulator acc;
    private final UnmigratedJUnit3Tests unmigrated;

    JUnit3TestCaseVisitor(Accumulator acc, UnmigratedJUnit3Tests unmigrated) {
        this.acc = acc;
        this.unmigrated = unmigrated;
    }

    private JavaTemplate.Builder template(String code) {
        return JavaTemplate.builder(code).javaParser(JavaParser.fromJavaVersion().classpathFromResources(new org.openrewrite.InMemoryExecutionContext(), "junit-4"));
    }

    // ---- where we are

    /**
     * Whether the test class at the cursor, or one that encloses it, is migrated.
     */
    private boolean inMigratedClass() {
        for (Cursor c = getCursor(); c != null; c = c.getParent()) {
            if (c.getValue() instanceof J.ClassDeclaration) {
                JavaType.FullyQualified type = ((J.ClassDeclaration) c.getValue()).getType();
                if (type != null && acc.isMigrated(type.getFullyQualifiedName())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    boolean retargets() {
        return inMigratedClass();
    }

    private J.@Nullable ClassDeclaration directlyDeclaringClass() {
        Cursor parent = getCursor().getParentTreeCursor();
        if (parent.getValue() instanceof J.Block) {
            Cursor grandParent = parent.getParentTreeCursor();
            if (grandParent.getValue() instanceof J.ClassDeclaration) {
                return grandParent.getValue();
            }
        }
        return null;
    }

    private static @Nullable String fqn(J.@Nullable ClassDeclaration cd) {
        return cd == null || cd.getType() == null ? null : cd.getType().getFullyQualifiedName();
    }

    @Override
    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
        J.CompilationUnit c = super.visitCompilationUnit(cu, ctx);
        if (c == cu) {
            // nothing migrated; a file with incomplete type attribution would otherwise lose imports it still needs
            return c;
        }
        // right away, as an import of junit.framework.Test would keep org.junit.Test from being added
        for (String type : Arrays.asList("junit.framework.TestCase", "junit.framework.Test", "junit.framework.TestSuite",
                "junit.framework.JUnit4TestAdapter", "junit.extensions.TestSetup", "junit.textui.TestRunner")) {
            c = (J.CompilationUnit) new RemoveImport<ExecutionContext>(type).visitNonNull(c, ctx, getCursor().getParentOrThrow());
        }
        return c;
    }

    // ---- classes

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
        String fqn = fqn(classDecl);
        if (fqn == null) {
            return super.visitClassDeclaration(classDecl, ctx);
        }
        if (acc.classes.containsKey(fqn) && !acc.isMigrated(fqn)) {
            unmigrated.insertRow(ctx, new UnmigratedJUnit3Tests.Row(
                    getCursor().firstEnclosingOrThrow(JavaSourceFile.class).getSourcePath().toString(), fqn,
                    acc.pinnedBecause.get(fqn)));
            return super.visitClassDeclaration(classDecl, ctx);
        }
        boolean testCase = acc.isMigrated(fqn);
        migrateFailureTypes |= testCase;
        SuiteShape suite = acc.suiteKindByClass.containsKey(fqn) ? suiteOf(classDecl) : null;
        boolean aggregator = !testCase && suite != null && suite.kind == SuiteShape.Kind.AGGREGATE;

        J.ClassDeclaration cd = classDecl;
        if (testCase && usesGetName(cd)) {
            cd = addTestNameRule(cd);
            setCursor(new Cursor(getCursor().getParentOrThrow(), cd));
        }
        cd = super.visitClassDeclaration(cd, ctx);
        if (testCase) {
            if (cd.getExtends() != null && cd.getExtends().getType() != null &&
                Accumulator.TEST_CASE.equals(TypeUtils.asFullyQualified(cd.getExtends().getType()).getFullyQualifiedName())) {
                cd = cd.withExtends(null);
            }
            if (suite != null && suite.kind == SuiteShape.Kind.SETUP) {
                cd = addOneTimeLifecycleMethods(cd, suite, ctx);
            }
        } else if (aggregator) {
            updateCursor(cd);
            cd = publicMemberSuiteClasses(cd, suite);
            updateCursor(cd);
            cd = addSuiteAnnotations(cd, suite);
        }
        return cd;
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

    private static @Nullable SuiteShape suiteOf(J.ClassDeclaration cd) {
        for (Statement s : cd.getBody().getStatements()) {
            if (s instanceof J.MethodDeclaration && SuiteShape.isSuiteMethod((J.MethodDeclaration) s)) {
                return SuiteShape.analyze((J.MethodDeclaration) s, fqn(cd));
            }
        }
        return null;
    }

    private static boolean usesGetName(J.ClassDeclaration cd) {
        boolean[] found = {false};
        new JavaIsoVisitor<Integer>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation m, Integer p) {
                if (isTestCaseGetName(m)) {
                    found[0] = true;
                }
                return super.visitMethodInvocation(m, p);
            }
        }.visit(cd.getBody(), 0);
        return found[0];
    }

    private static boolean isTestCaseGetName(J.MethodInvocation m) {
        return "getName".equals(m.getSimpleName()) && m.getArguments().stream().allMatch(a -> a instanceof J.Empty) &&
               m.getMethodType() != null && Accumulator.TEST_CASE.equals(m.getMethodType().getDeclaringType().getFullyQualifiedName()) &&
               (m.getSelect() == null || m.getSelect() instanceof J.Identifier && "this".equals(((J.Identifier) m.getSelect()).getSimpleName()));
    }

    private static final String TEST_NAME_FIELD = "testName";

    private J.ClassDeclaration addTestNameRule(J.ClassDeclaration cd) {
        maybeAddImport("org.junit.Rule");
        maybeAddImport("org.junit.rules.TestName");
        J.Block body = template("@Rule\npublic TestName " + TEST_NAME_FIELD + " = new TestName();")
                .imports("org.junit.Rule", "org.junit.rules.TestName").build()
                .apply(new Cursor(getCursor(), cd.getBody()), cd.getBody().getCoordinates().firstStatement());
        return cd.withBody(body.withStatements(ListUtils.map(body.getStatements(), (i, st) ->
                i == 1 && st.getPrefix().getComments().isEmpty() && st.getPrefix().getWhitespace().chars().filter(c -> c == '\n').count() < 2 ?
                        st.withPrefix(Space.format("\n\n" + st.getPrefix().getIndent())) : st)));
    }

    @Override
    public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
        J.Block b = super.visitBlock(block, ctx);
        // a first member that is removed leaves its successor with a prefix that is meant for a later member
        if (!block.getStatements().isEmpty() && !b.getStatements().isEmpty() &&
            b.getStatements().get(0).getId() != block.getStatements().get(0).getId()) {
            Space original = block.getStatements().get(0).getPrefix();
            b = b.withStatements(ListUtils.mapFirst(b.getStatements(), st -> st.withPrefix(original)));
        }
        return b;
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
    private J.ClassDeclaration addOneTimeLifecycleMethods(J.ClassDeclaration cd, SuiteShape suite, ExecutionContext ctx) {
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
                " throws " + original.getThrows().stream().map(t -> t.printTrimmed(getCursor())).collect(java.util.stream.Collectors.joining(", "));
        String unique = name;
        for (int i = 2; hasMethod(cd, unique); i++) {
            unique = name + i;
        }
        maybeAddImport("org.junit." + annotation);
        J.Block block = cd.getBody();
        return cd.withBody(template("@" + annotation + "\npublic static void " + unique + "()" + throwsClause + " " + body.printTrimmed(getCursor()))
                .imports("org.junit." + annotation).contextSensitive().build()
                .apply(new Cursor(getCursor(), block), block.getCoordinates().addMethodDeclaration((a, b) -> 1)));
    }

    private static boolean hasMethod(J.ClassDeclaration cd, String name) {
        for (Statement s : cd.getBody().getStatements()) {
            if (s instanceof J.MethodDeclaration && ((J.MethodDeclaration) s).getSimpleName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    // ---- methods

    @Override
    public J.@Nullable MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
        J.ClassDeclaration declaring = directlyDeclaringClass();
        String declaringFqn = fqn(declaring);
        if (declaringFqn == null || method.getMethodType() == null) {
            return super.visitMethodDeclaration(method, ctx);
        }
        boolean testCase = acc.isMigrated(declaringFqn);
        if (SuiteShape.isSuiteMethod(method) && acc.suites.contains(method.getId()) && acc.removesSuite(declaringFqn)) {
            return null;
        }
        if (!testCase) {
            return super.visitMethodDeclaration(method, ctx);
        }
        J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
        updateCursor(md);
        if (md.isConstructor()) {
            return migrateConstructor(md, acc.classes.get(declaringFqn));
        }
        if (isNoArgVoid(md) && md.hasModifier(J.Modifier.Type.Static) && md.hasModifier(J.Modifier.Type.Public) &&
            md.getSimpleName().startsWith("test") && !calledInFile(md.getSimpleName())) {
            // JUnit 3 ran static test methods, whereas JUnit 4 rejects them
            md = md.withModifiers(ListUtils.map(md.getModifiers(), m -> m.getType() == J.Modifier.Type.Static ? null : m));
        }
        if (isNoArgVoid(md) && !md.hasModifier(J.Modifier.Type.Static) && !md.hasModifier(J.Modifier.Type.Abstract)) {
            if (md.getSimpleName().startsWith("test") && md.hasModifier(J.Modifier.Type.Public) &&
                md.getLeadingAnnotations().stream().noneMatch(JUNIT4_TEST::matches)) {
                md = addAnnotation(md, "@Test", "org.junit.Test", ctx);
            } else if ("setUp".equals(md.getSimpleName()) && md.getLeadingAnnotations().stream().noneMatch(JUNIT4_BEFORE::matches)) {
                md = makePublic(addAnnotation(md, "@Before", "org.junit.Before", ctx));
            } else if ("tearDown".equals(md.getSimpleName()) && md.getLeadingAnnotations().stream().noneMatch(JUNIT4_AFTER::matches)) {
                md = makePublic(addAnnotation(md, "@After", "org.junit.After", ctx));
            }
        }
        return removeObsoleteOverride(md, ctx);
    }

    private boolean calledInFile(String name) {
        J.CompilationUnit cu = getCursor().firstEnclosing(J.CompilationUnit.class);
        if (cu == null) {
            return true;
        }
        boolean[] called = {false};
        new JavaIsoVisitor<boolean[]>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation m, boolean[] c) {
                if (name.equals(m.getSimpleName())) {
                    c[0] = true;
                }
                return super.visitMethodInvocation(m, c);
            }
        }.visit(cu, called);
        return called[0];
    }

    /**
     * Whether some type of the file is named like the annotation, which would then be that type.
     */
    private boolean declaresTypeNamed(String simpleName) {
        J.CompilationUnit cu = getCursor().firstEnclosing(J.CompilationUnit.class);
        if (cu == null) {
            return false;
        }
        boolean[] found = {false};
        new JavaIsoVisitor<boolean[]>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, boolean[] f) {
                if (simpleName.equals(cd.getSimpleName())) {
                    f[0] = true;
                }
                return super.visitClassDeclaration(cd, f);
            }
        }.visit(cu, found);
        return found[0];
    }

    private static boolean isNoArgVoid(J.MethodDeclaration md) {
        return md.getParameters().stream().allMatch(p -> p instanceof J.Empty) &&
               md.getReturnTypeExpression() != null && md.getReturnTypeExpression().getType() == JavaType.Primitive.Void;
    }

    /**
     * Built by hand, as a template would resolve {@code @Test} to {@code junit.framework.Test} while that is imported.
     */
    private J.MethodDeclaration addAnnotation(J.MethodDeclaration md, String annotation, String fqn, ExecutionContext ctx) {
        boolean qualify = declaresTypeNamed(annotation.substring(1));
        if (!qualify) {
            maybeAddImport(fqn);
        }
        Space newLine = Space.format("\n" + md.getPrefix().getIndent());
        NameTree annotationType = qualify ?
                (NameTree) TypeTree.build(fqn).withType(JavaType.ShallowClass.build(fqn)) :
                new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, Collections.emptyList(), annotation.substring(1),
                        JavaType.ShallowClass.build(fqn), null);
        J.Annotation added = new J.Annotation(randomId(), md.getLeadingAnnotations().isEmpty() ? Space.EMPTY : newLine, Markers.EMPTY,
                annotationType, null);
        boolean first = md.getLeadingAnnotations().isEmpty();
        J.MethodDeclaration m = md.withLeadingAnnotations(ListUtils.concat(md.getLeadingAnnotations(), added));
        if (first) {
            if (!m.getModifiers().isEmpty()) {
                m = m.withModifiers(ListUtils.mapFirst(m.getModifiers(), mod -> mod.withPrefix(newLine)));
            } else if (m.getReturnTypeExpression() != null) {
                m = m.withReturnTypeExpression(m.getReturnTypeExpression().withPrefix(newLine));
            }
        }
        return m;
    }

    private static J.MethodDeclaration makePublic(J.MethodDeclaration md) {
        return md.withModifiers(ListUtils.map(md.getModifiers(), m -> m.getType() == J.Modifier.Type.Protected ? m.withType(J.Modifier.Type.Public) : m));
    }

    private J.MethodDeclaration removeObsoleteOverride(J.MethodDeclaration md, ExecutionContext ctx) {
        JavaType.Method type = md.getMethodType();
        if (type == null || md.getLeadingAnnotations().stream().noneMatch(OVERRIDE::matches) || stillOverrides(type)) {
            return md;
        }
        J.MethodDeclaration withoutBody = (J.MethodDeclaration) new RemoveAnnotationVisitor(OVERRIDE)
                .visitNonNull(md.withBody(null), ctx, getCursor().getParentOrThrow());
        return withoutBody.withBody(md.getBody());
    }

    private static boolean stillOverrides(JavaType.Method method) {
        Optional<JavaType.Method> overridden = TypeUtils.findOverriddenMethod(method);
        while (overridden.isPresent()) {
            if (!SUPERTYPES_REMOVED_BY_MIGRATION.contains(overridden.get().getDeclaringType().getFullyQualifiedName())) {
                return true;
            }
            overridden = TypeUtils.findOverriddenMethod(overridden.get());
        }
        return false;
    }

    private J.@Nullable MethodDeclaration migrateConstructor(J.MethodDeclaration ctor, MigrateJUnit3TestCase.ClassInfo info) {
        J.MethodDeclaration c = ctor;
        if (info.nameConstructor && c.getParameters().stream().anyMatch(p -> p instanceof J.VariableDeclarations)) {
            c = c.withParameters(singletonList(new J.Empty(randomId(), Space.EMPTY, Markers.EMPTY)));
        }
        if (c.getBody() != null) {
            // an explicit super() adds nothing
            J.Block body = c.getBody();
            List<Statement> statements = ListUtils.map(body.getStatements(), (i, s) ->
                    i == 0 && s instanceof J.MethodInvocation && "super".equals(((J.MethodInvocation) s).getSimpleName()) &&
                    ((J.MethodInvocation) s).getArguments().stream().allMatch(a -> a instanceof J.Empty) ? null : s);
            c = c.withBody(body.withStatements(statements));
            if (statements.isEmpty() && c.getParameters().stream().allMatch(p -> p instanceof J.Empty)) {
                return null;
            }
        }
        return c.withModifiers(ListUtils.map(c.getModifiers(), m ->
                m.getType() == J.Modifier.Type.Protected || m.getType() == J.Modifier.Type.Private ? m.withType(J.Modifier.Type.Public) : m));
    }

    // ---- calls

    @Override
    public J.@Nullable MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
        JavaType.Method type = method.getMethodType();
        if (type != null && inMigratedClass()) {
            String declaring = type.getDeclaringType().getFullyQualifiedName();
            Object parent = getCursor().getParentTreeCursor().getValue();
            boolean statement = parent instanceof J.Block;
            if (Accumulator.TEST_CASE.equals(declaring) && !type.hasFlags(Flag.Static)) {
                if (statement && ("setUp".equals(type.getName()) || "tearDown".equals(type.getName()) || "<constructor>".equals(type.getName()))) {
                    return null;
                }
                if (isTestCaseGetName(method)) {
                    return template(TEST_NAME_FIELD + ".getMethodName()").imports("org.junit.rules.TestName").contextSensitive().build()
                            .apply(getCursor(), method.getCoordinates().replace());
                }
            } else if (("super".equals(method.getSimpleName()) || "this".equals(method.getSimpleName())) &&
                       acc.dropsNameArgument(declaring) && method.getArguments().size() == 1) {
                return method.withArguments(singletonList(new J.Empty(randomId(), Space.EMPTY, Markers.EMPTY)));
            }
        }
        if (type != null && TEST_RUNNERS.contains(type.getDeclaringType().getFullyQualifiedName())) {
            J.MethodInvocation replaced = replaceTestRunner(method);
            if (replaced != null) {
                return replaced;
            }
        }
        return super.visitMethodInvocation(method, ctx);
    }

    private J.@Nullable MethodInvocation replaceTestRunner(J.MethodInvocation run) {
        if (!"run".equals(run.getSimpleName()) || run.getArguments().size() != 1) {
            return null;
        }
        Expression arg = run.getArguments().get(0).unwrap();
        Expression classLiteral = null;
        if (arg instanceof J.FieldAccess && acc.consumed.contains(arg.getId())) {
            classLiteral = arg;
        } else if (arg instanceof J.NewClass && ((J.NewClass) arg).getArguments().size() == 1 &&
                   ((J.NewClass) arg).getArguments().get(0).unwrap() instanceof J.FieldAccess) {
            classLiteral = ((J.NewClass) arg).getArguments().get(0).unwrap();
        } else if (arg instanceof J.MethodInvocation && "suite".equals(((J.MethodInvocation) arg).getSimpleName()) &&
                   ((J.MethodInvocation) arg).getMethodType() != null &&
                   acc.removesSuite(((J.MethodInvocation) arg).getMethodType().getDeclaringType().getFullyQualifiedName())) {
            J.MethodInvocation suite = (J.MethodInvocation) arg;
            Expression select = suite.getSelect();
            if (select == null) {
                JavaType.FullyQualified declaring = suite.getMethodType().getDeclaringType();
                select = new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, Collections.emptyList(),
                        SuiteShape.simpleName(declaring.getFullyQualifiedName()), declaring, null);
            }
            classLiteral = SuiteShape.classLiteralOf(select);
        }
        if (classLiteral == null) {
            return null;
        }
        // a test class that stays a JUnit 3 test is still accepted by the JUnit 3 runner
        if (!(arg instanceof J.MethodInvocation) && !acc.isMigrated(fqnOfClassLiteral((J.FieldAccess) classLiteral))) {
            return null;
        }
        maybeAddImport("org.junit.runner.JUnitCore");
        return template("JUnitCore.runClasses(#{any()})").imports("org.junit.runner.JUnitCore").build()
                .apply(getCursor(), run.getCoordinates().replace(), classLiteral.withPrefix(Space.EMPTY));
    }

    private static @Nullable String fqnOfClassLiteral(J.FieldAccess literal) {
        JavaType.FullyQualified t = TypeUtils.asFullyQualified(literal.getTarget().getType());
        return t == null ? null : t.getFullyQualifiedName();
    }

    @Override
    public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
        J.NewClass nc = super.visitNewClass(newClass, ctx);
        JavaType.FullyQualified created = TypeUtils.asFullyQualified(nc.getType());
        if (created != null && acc.dropsNameArgument(created.getFullyQualifiedName()) && nc.getArguments().size() == 1 &&
            !(nc.getArguments().get(0) instanceof J.Empty)) {
            return nc.withArguments(singletonList(new J.Empty(randomId(), Space.EMPTY, Markers.EMPTY)));
        }
        return nc;
    }
}
