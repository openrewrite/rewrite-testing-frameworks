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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.openrewrite.java.testing.junit4.MigrateJUnit3TestCase.Accumulator;
import static org.openrewrite.java.testing.junit4.MigrateJUnit3TestCase.ClassInfo;

/**
 * Finds the {@code TestCase} subclasses in source and what in the code base would stop them from being migrated.
 */
class JUnit3Scanner extends JavaIsoVisitor<ExecutionContext> {

    private static final Set<String> JUNIT3_FRAMEWORK_TYPES_WITH_EQUIVALENT = new HashSet<>(Arrays.asList(
            "junit.framework.TestCase", "junit.framework.Assert", "junit.framework.Test", "junit.framework.TestSuite",
            "junit.framework.AssertionFailedError", "junit.framework.ComparisonFailure", "junit.framework.JUnit4TestAdapter",
            "junit.extensions.TestSetup", "junit.textui.TestRunner"));
    private static final Set<String> TEST_RUNNERS = new HashSet<>(Arrays.asList(
            "junit.textui.TestRunner", "junit.swingui.TestRunner", "junit.awtui.TestRunner"));
    private static final Set<String> OBJECT_METHODS = new HashSet<>(Arrays.asList("toString", "equals", "hashCode"));

    private final Accumulator acc;
    private final List<String> classesInFile = new ArrayList<>();
    private @Nullable String fileReason;

    JUnit3Scanner(Accumulator acc) {
        this.acc = acc;
    }

    @Override
    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
        classesInFile.clear();
        fileReason = null;
        for (JavaType type : cu.getTypesInUse().getTypesInUse()) {
            JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
            if (fq == null) {
                continue;
            }
            String name = fq.getFullyQualifiedName();
            if ((name.startsWith("junit.") && !name.startsWith("junit.framework.") && !JUNIT3_FRAMEWORK_TYPES_WITH_EQUIVALENT.contains(name) &&
                 !TEST_RUNNERS.contains(name)) ||
                "junit.framework.TestResult".equals(name) || "junit.framework.TestListener".equals(name) ||
                "junit.framework.Protectable".equals(name) || "junit.framework.TestFailure".equals(name)) {
                fileReason = "uses " + name + ", which has no JUnit 4 equivalent";
                break;
            }
        }
        J.CompilationUnit c = super.visitCompilationUnit(cu, ctx);
        if (fileReason != null) {
            for (String fqn : classesInFile) {
                acc.pin(fqn, fileReason);
            }
        }
        return c;
    }

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
        JavaType.FullyQualified type = classDecl.getType();
        if (type != null && isStrictTestCaseSubclass(type) && type.getSupertype() != null) {
            ClassInfo info = new ClassInfo(type.getFullyQualifiedName(), type.getSupertype().getFullyQualifiedName());
            acc.classes.put(info.fqn, info);
            classesInFile.add(info.fqn);
            analyzeConstructors(classDecl, info);
            pinIfTestCaseImplementsInterfaceMethod(type, info);
            if (classDecl.getLeadingAnnotations().stream().anyMatch(a -> "RunWith".equals(a.getSimpleName()))) {
                acc.pin(info.fqn, "a runner chosen with @RunWith decides which methods run, whereas the TestCase conventions do not apply to it");
            }
        }
        return super.visitClassDeclaration(classDecl, ctx);
    }

    /**
     * {@code TestCase} has public methods such as {@code getName()} that may be what an interface of the class asks for.
     */
    private void pinIfTestCaseImplementsInterfaceMethod(JavaType.FullyQualified type, ClassInfo info) {
        Set<String> testCaseMethods = new HashSet<>();
        List<JavaType.FullyQualified> interfaces = new ArrayList<>();
        for (JavaType.FullyQualified t = type; t != null; t = t.getSupertype()) {
            if (Accumulator.TEST_CASE.equals(t.getFullyQualifiedName())) {
                for (JavaType.Method m : t.getMethods()) {
                    if (m.hasFlags(Flag.Public) && !m.hasFlags(Flag.Static)) {
                        testCaseMethods.add(m.getName() + "/" + m.getParameterTypes().size());
                    }
                }
                break;
            }
            interfaces.addAll(t.getInterfaces());
        }
        Set<String> seen = new HashSet<>();
        while (!interfaces.isEmpty()) {
            JavaType.FullyQualified iface = interfaces.remove(interfaces.size() - 1);
            if (!seen.add(iface.getFullyQualifiedName()) || iface.getFullyQualifiedName().startsWith("junit.framework.")) {
                continue;
            }
            for (JavaType.Method m : iface.getMethods()) {
                if (m.hasFlags(Flag.Abstract) && testCaseMethods.contains(m.getName() + "/" + m.getParameterTypes().size())) {
                    acc.pin(info.fqn, "TestCase provides " + m.getName() + "(), which " + iface.getClassName() + " asks for");
                }
            }
            interfaces.addAll(iface.getInterfaces());
        }
    }

    static boolean isStrictTestCaseSubclass(JavaType.FullyQualified type) {
        for (JavaType.FullyQualified t = type.getSupertype(); t != null; t = t.getSupertype()) {
            if (Accumulator.TEST_CASE.equals(t.getFullyQualifiedName())) {
                return true;
            }
        }
        return false;
    }

    private void analyzeConstructors(J.ClassDeclaration cd, ClassInfo info) {
        J.MethodDeclaration nameConstructor = null;
        boolean hasNoArgConstructor = false;
        for (Statement s : cd.getBody().getStatements()) {
            if (!(s instanceof J.MethodDeclaration) || !((J.MethodDeclaration) s).isConstructor()) {
                continue;
            }
            J.MethodDeclaration ctor = (J.MethodDeclaration) s;
            List<Statement> params = new ArrayList<>();
            for (Statement p : ctor.getParameters()) {
                if (!(p instanceof J.Empty)) {
                    params.add(p);
                }
            }
            if (params.isEmpty()) {
                hasNoArgConstructor = true;
            } else if (params.size() == 1 && params.get(0) instanceof J.VariableDeclarations &&
                       TypeUtils.isString(((J.VariableDeclarations) params.get(0)).getType())) {
                nameConstructor = ctor;
            } else {
                acc.pin(info.fqn, "declares a constructor with parameters, which JUnit 4 cannot supply");
            }
        }
        if (nameConstructor == null || nameConstructor.getBody() == null) {
            return;
        }
        String name = ((J.VariableDeclarations) nameConstructor.getParameters().get(0)).getVariables().get(0).getSimpleName();
        List<Statement> body = nameConstructor.getBody().getStatements();
        List<Statement> rest = body;
        if (!body.isEmpty() && body.get(0) instanceof J.MethodInvocation) {
            J.MethodInvocation first = (J.MethodInvocation) body.get(0);
            if ("super".equals(first.getSimpleName())) {
                List<Expression> args = first.getArguments();
                if (args.size() == 1 && args.get(0) instanceof J.Identifier && name.equals(((J.Identifier) args.get(0)).getSimpleName())) {
                    info.callsSuperWithName = true;
                } else if (!(args.size() == 1 && args.get(0) instanceof J.Empty)) {
                    acc.pin(info.fqn, "its (String) constructor passes something other than the test name to super");
                    return;
                }
                rest = body.subList(1, body.size());
            } else if ("this".equals(first.getSimpleName())) {
                acc.pin(info.fqn, "its (String) constructor delegates to another constructor");
                return;
            }
        }
        if (usesIdentifier(rest, name)) {
            acc.pin(info.fqn, "its (String) constructor uses the test name");
        } else if (hasNoArgConstructor && !rest.isEmpty()) {
            acc.pin(info.fqn, "it declares a (String) and a no-argument constructor that differ");
        } else {
            info.nameConstructor = true;
        }
    }

    private static boolean usesIdentifier(List<Statement> statements, String name) {
        boolean[] found = {false};
        JavaIsoVisitor<Integer> finder = new JavaIsoVisitor<Integer>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, Integer p) {
                if (name.equals(identifier.getSimpleName())) {
                    found[0] = true;
                }
                return identifier;
            }
        };
        for (Statement s : statements) {
            finder.visit(s, 0);
        }
        return found[0];
    }

    /**
     * The test class that declares the method at the cursor directly or in a nested block.
     */
    private @Nullable String enclosingTestCase() {
        for (Cursor c = getCursor(); c != null; c = c.getParent()) {
            if (c.getValue() instanceof J.ClassDeclaration) {
                JavaType.FullyQualified type = ((J.ClassDeclaration) c.getValue()).getType();
                return type != null && acc.classes.containsKey(type.getFullyQualifiedName()) ? type.getFullyQualifiedName() : null;
            }
        }
        return null;
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

    @Override
    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
        J.ClassDeclaration declaring = directlyDeclaringClass();
        if (declaring != null && declaring.getType() != null && method.getMethodType() != null) {
            String declaringFqn = declaring.getType().getFullyQualifiedName();
            boolean testCase = acc.classes.containsKey(declaringFqn);
            if (SuiteShape.isSuiteMethod(method)) {
                SuiteShape shape = SuiteShape.analyze(method, declaringFqn);
                if (shape == null) {
                    if (testCase) {
                        acc.pin(declaringFqn, "its suite() method builds a suite that has no JUnit 4 equivalent");
                    }
                } else {
                    acc.suites.add(method.getId());
                    acc.suiteKindByClass.put(declaringFqn, shape.kind);
                    acc.consumed.addAll(shape.consumed);
                    acc.consumedBySuite.computeIfAbsent(declaringFqn, k -> new java.util.HashSet<>()).addAll(shape.consumed);
                    if (testCase && shape.kind == SuiteShape.Kind.AGGREGATE) {
                        acc.pin(declaringFqn, "its suite() method collects other test classes");
                    }
                }
            } else if (testCase && !method.isConstructor() && !"setUp".equals(method.getSimpleName()) &&
                       !"tearDown".equals(method.getSimpleName()) && !OBJECT_METHODS.contains(method.getSimpleName())) {
                for (JavaType.Method overridden = TypeUtils.findOverriddenMethod(method.getMethodType()).orElse(null);
                     overridden != null; overridden = TypeUtils.findOverriddenMethod(overridden).orElse(null)) {
                    if (Accumulator.TEST_CASE.equals(overridden.getDeclaringType().getFullyQualifiedName())) {
                        acc.pin(declaringFqn, "overrides TestCase." + method.getSimpleName() + "()");
                        break;
                    }
                }
            }
        }
        return super.visitMethodDeclaration(method, ctx);
    }

    @Override
    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
        JavaType.Method type = method.getMethodType();
        if (type != null) {
            recordArgumentsOfJUnit3Types(type, method.getArguments());
            String declaring = type.getDeclaringType().getFullyQualifiedName();
            String enclosing = enclosingTestCase();
            if (TEST_RUNNERS.contains(declaring)) {
                if (!recognizedTestRunnerCall(method)) {
                    fileReason = "calls " + declaring + "." + method.getSimpleName() + ", which has no JUnit 4 equivalent here";
                }
            } else if (Accumulator.TEST_CASE.equals(declaring) && enclosing != null && !type.hasFlags(Flag.Static)) {
                boolean ownOrSuper = method.getSelect() == null || isThisOrSuper(method.getSelect());
                // calls on some other object only need the type TestCase, which is still there
                if (ownOrSuper && !("setUp".equals(type.getName()) || "tearDown".equals(type.getName()) || "getName".equals(type.getName()) ||
                                    "<constructor>".equals(type.getName()))) {
                    acc.pin(enclosing, "calls TestCase." + type.getName() + "()");
                }
            } else if ("suite".equals(type.getName()) && method.getArguments().stream().allMatch(a -> a instanceof J.Empty)) {
                acc.suiteCalls.put(method.getId(), declaring);
            } else if ("super".equals(method.getSimpleName()) || "this".equals(method.getSimpleName())) {
                if (method.getArguments().size() == 1 && !isSafeNameArgument(method.getArguments().get(0))) {
                    acc.unsafeNameArgument.add(declaring);
                }
            }
        }
        return super.visitMethodInvocation(method, ctx);
    }

    private void recordArgumentsOfJUnit3Types(JavaType.Method type, List<Expression> args) {
        List<JavaType> parameters = type.getParameterTypes();
        for (int i = 0; i < args.size() && i < parameters.size(); i++) {
            JavaType.FullyQualified parameter = TypeUtils.asFullyQualified(parameters.get(i));
            JavaType.FullyQualified argument = TypeUtils.asFullyQualified(args.get(i).getType());
            if (parameter != null && argument != null && parameter.getFullyQualifiedName().startsWith("junit.framework.") &&
                !argument.getFullyQualifiedName().startsWith("junit.framework.")) {
                acc.passedAsJUnit3Type.add(argument.getFullyQualifiedName());
            }
        }
    }

    private static boolean isThisOrSuper(Expression e) {
        return e instanceof J.Identifier && ("this".equals(((J.Identifier) e).getSimpleName()) || "super".equals(((J.Identifier) e).getSimpleName()));
    }

    /**
     * {@code TestRunner.run(A.class)}, {@code TestRunner.run(new TestSuite(A.class))} or {@code TestRunner.run(suite())}
     * as a statement, which is {@code JUnitCore.runClasses(A.class)}.
     */
    private boolean recognizedTestRunnerCall(J.MethodInvocation run) {
        if (!"run".equals(run.getSimpleName()) || run.getArguments().size() != 1 ||
            !(getCursor().getParentTreeCursor().getValue() instanceof J.Block)) {
            return false;
        }
        Expression arg = run.getArguments().get(0).unwrap();
        if (arg instanceof J.FieldAccess && "class".equals(((J.FieldAccess) arg).getSimpleName())) {
            acc.consumed.add(arg.getId());
            return true;
        }
        if (arg instanceof J.MethodInvocation && "suite".equals(((J.MethodInvocation) arg).getSimpleName())) {
            acc.consumed.add(arg.getId());
            return true;
        }
        if (arg instanceof J.NewClass && ((J.NewClass) arg).getBody() == null && ((J.NewClass) arg).getArguments().size() == 1) {
            Expression inner = ((J.NewClass) arg).getArguments().get(0).unwrap();
            if (inner instanceof J.FieldAccess && "class".equals(((J.FieldAccess) inner).getSimpleName())) {
                acc.consumed.add(inner.getId());
                return true;
            }
        }
        return false;
    }

    @Override
    public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
        if (newClass.getConstructorType() != null) {
            recordArgumentsOfJUnit3Types(newClass.getConstructorType(), newClass.getArguments());
        }
        JavaType.FullyQualified created = TypeUtils.asFullyQualified(newClass.getType());
        if (created != null) {
            String fqn = created.getFullyQualifiedName();
            if (newClass.getArguments().size() == 1 && !isSafeNameArgument(newClass.getArguments().get(0))) {
                acc.unsafeNameArgument.add(fqn);
            }
            Object parent = getCursor().getParentTreeCursor().getValue();
            boolean directlyUsed = parent instanceof J.Block ||
                                   parent instanceof J.MethodInvocation && ((J.MethodInvocation) parent).getSelect() == newClass;
            if (!directlyUsed) {
                acc.pin(fqn, "instances are used as junit.framework.Test");
            }
        }
        return super.visitNewClass(newClass, ctx);
    }

    @Override
    public J.MemberReference visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
        JavaType.FullyQualified type = TypeUtils.asFullyQualified(memberRef.getContaining().getType());
        if ("new".equals(memberRef.getReference().getSimpleName()) && type != null) {
            acc.unsafeNameArgument.add(type.getFullyQualifiedName());
        }
        return super.visitMemberReference(memberRef, ctx);
    }

    @Override
    public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
        if ("class".equals(fieldAccess.getSimpleName()) && fieldAccess.getTarget().getType() != null) {
            Object parent = getCursor().getParentTreeCursor().getValue();
            JavaType.FullyQualified target = TypeUtils.asFullyQualified(fieldAccess.getTarget().getType());
            if (target != null) {
                String callee = null;
                if (parent instanceof J.MethodInvocation && ((J.MethodInvocation) parent).getMethodType() != null) {
                    callee = ((J.MethodInvocation) parent).getMethodType().getDeclaringType().getFullyQualifiedName();
                } else if (parent instanceof J.NewClass && ((J.NewClass) parent).getType() != null) {
                    callee = TypeUtils.asFullyQualified(((J.NewClass) parent).getType()) == null ? null :
                            TypeUtils.asFullyQualified(((J.NewClass) parent).getType()).getFullyQualifiedName();
                }
                if (callee != null && callee.startsWith("junit.")) {
                    acc.junitClassLiterals.put(fieldAccess.getId(), target.getFullyQualifiedName());
                }
            }
        }
        return super.visitFieldAccess(fieldAccess, ctx);
    }

    /**
     * An argument that dropping loses nothing: a literal, a name, or a combination of those.
     */
    static boolean isSafeNameArgument(Expression e) {
        Expression expr = e.unwrap();
        if (expr instanceof J.Literal || expr instanceof J.Identifier || expr instanceof J.Empty) {
            return true;
        }
        if (expr instanceof J.FieldAccess) {
            return isSafeNameArgument(((J.FieldAccess) expr).getTarget());
        }
        if (expr instanceof J.Binary) {
            return isSafeNameArgument(((J.Binary) expr).getLeft()) && isSafeNameArgument(((J.Binary) expr).getRight());
        }
        return false;
    }
}
