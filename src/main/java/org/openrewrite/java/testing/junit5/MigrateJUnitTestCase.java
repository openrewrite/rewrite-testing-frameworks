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
import org.openrewrite.Preconditions;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.UseStaticImport;
import org.openrewrite.java.search.DeclaresMethod;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import java.util.*;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

public class MigrateJUnitTestCase extends ScanningRecipe<MigrateJUnitTestCase.Accumulator> {

    private static final AnnotationMatcher JUNIT_TEST_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.Test");
    private static final AnnotationMatcher JUNIT_AFTER_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.*After*");
    private static final AnnotationMatcher JUNIT_BEFORE_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.*Before*");
    private static final MethodMatcher TEST_CASE_RUN_MATCHER = new MethodMatcher("junit.framework.TestCase run(junit.framework.TestResult)", true);
    private static final MethodMatcher SET_NAME_MATCHER = new MethodMatcher("junit.framework.TestCase setName(String)");
    private static final MethodMatcher SET_UP_MATCHER = new MethodMatcher("junit.framework.TestCase setUp()");
    private static final MethodMatcher TEAR_DOWN_MATCHER = new MethodMatcher("junit.framework.TestCase tearDown()");
    private static final MethodMatcher SUITE_MATCHER = new MethodMatcher("*..* suite()");

    private static boolean isSupertypeTestCase(JavaType.@Nullable FullyQualified fullyQualified) {
        if (fullyQualified == null || fullyQualified.getSupertype() == null || "java.lang.Object".equals(fullyQualified.getFullyQualifiedName())) {
            return false;
        }

        JavaType.FullyQualified fqType = TypeUtils.asFullyQualified(fullyQualified);
        if (fqType != null && "junit.framework.TestCase".equals(fqType.getFullyQualifiedName())) {
            return true;
        }
        return isSupertypeTestCase(fullyQualified.getSupertype());
    }

    @Getter
    final String displayName = "Migrate JUnit 4 `TestCase` to JUnit Jupiter";

    @Getter
    final String description = "Convert JUnit 4 `TestCase` to JUnit Jupiter.";

    static class Accumulator {
        final Map<String, Constructor> constructors = new HashMap<>();
        /**
         * Signatures declared more than once, as when two projects declare a class with the same fully qualified name,
         * whose callers cannot be told apart.
         */
        final Set<String> ambiguous = new HashSet<>();
        final Map<String, Boolean> references = new HashMap<>();
        final Set<String> nonJavaReferencedClasses = new HashSet<>();
        final Map<String, List<Constructor>> constructorsByClass = new HashMap<>();
        private @Nullable Set<String> redundant;

        void declare(Constructor constructor) {
            if (constructors.put(constructor.signature, constructor) != null) {
                ambiguous.add(constructor.signature);
            }
            constructorsByClass.computeIfAbsent(constructor.declaringClass, ignored -> new ArrayList<>()).add(constructor);
        }

        Set<String> redundantConstructors() {
            if (redundant != null) {
                return redundant;
            }
            Set<String> retained = new HashSet<>(ambiguous);
            for (Map.Entry<String, Boolean> reference : references.entrySet()) {
                if (reference.getValue()) {
                    retained.add(reference.getKey());
                }
            }
            for (Constructor constructor : constructors.values()) {
                if (nonJavaReferencedClasses.contains(constructor.declaringClass)) {
                    retained.add(constructor.signature);
                }
            }
            Set<String> redundant = new HashSet<>();
            boolean changed = true;
            while (changed) {
                changed = false;
                for (Constructor declaration : constructors.values()) {
                    if (declaration.removable && !retained.contains(declaration.signature) &&
                        !redundant.contains(declaration.signature) && hasRedundantBody(declaration, redundant)) {
                        changed |= redundant.add(declaration.signature);
                    }
                }
            }
            changed = true;
            while (changed) {
                Set<String> rejected = new HashSet<>();
                for (String signature : redundant) {
                    Constructor declaration = constructors.get(signature);
                    List<Constructor> siblings = constructorsByClass.get(declaration.declaringClass);
                    long redundantCount = siblings.stream()
                            .filter(constructor -> redundant.contains(constructor.signature))
                            .count();
                    boolean retainedNoArgumentConstructor = siblings.stream().anyMatch(constructor ->
                            constructor.noArguments && !redundant.contains(constructor.signature));
                    boolean incompatibleConstructors = redundantCount > 1 && siblings.stream()
                            .filter(constructor -> redundant.contains(constructor.signature))
                            .anyMatch(constructor -> constructor.annotated || !constructor.visibility.equals(declaration.visibility));
                    if (retainedNoArgumentConstructor || incompatibleConstructors || !hasRedundantBody(declaration, redundant)) {
                        rejected.add(signature);
                    }
                }
                changed = redundant.removeAll(rejected);
            }
            this.redundant = redundant;
            return redundant;
        }

        private static boolean hasRedundantBody(Constructor declaration, Set<String> redundant) {
            switch (declaration.body) {
                case EMPTY:
                    return true;
                case SAFE_DELEGATION:
                    return declaration.delegatesToTestCase || redundant.contains(declaration.delegationSignature);
                default:
                    return false;
            }
        }
    }

    private enum ConstructorBody {
        NONE, EMPTY, SAFE_DELEGATION, OTHER
    }

    /**
     * What the redundancy analysis needs to know about a constructor declaration, kept as plain values
     * so that the accumulator holds no trees.
     */
    static class Constructor {
        final String signature;
        final String declaringClass;
        /**
         * Declared in a TestCase subclass nested only in other TestCase subclasses, as the migration does not visit others,
         * with a signature that could be dropped: no thrown exceptions, varargs, or generic parameters.
         */
        final boolean removable;
        final boolean noArguments;
        final boolean annotated;
        final Set<J.Modifier.Type> visibility;
        final ConstructorBody body;
        /**
         * For a body that is a single constructor invocation with safe arguments, whether it calls a TestCase constructor.
         */
        final boolean delegatesToTestCase;
        /**
         * For a body that is a single constructor invocation with safe arguments, the signature of the invoked constructor.
         */
        final @Nullable String delegationSignature;

        Constructor(J.MethodDeclaration method, JavaType.Method type, boolean migrated) {
            signature = MethodMatcher.methodPattern(type);
            declaringClass = type.getDeclaringType().getFullyQualifiedName();
            removable = migrated && type.getThrownExceptions().isEmpty() &&
                        !type.hasFlags(Flag.Varargs) &&
                        type.getParameterTypes().stream().noneMatch(MigrateJUnitTestCase::hasGenericParameter);
            noArguments = type.getParameterTypes().isEmpty();
            annotated = !method.getLeadingAnnotations().isEmpty();
            visibility = EnumSet.noneOf(J.Modifier.Type.class);
            for (J.Modifier.Type modifier : asList(J.Modifier.Type.Public, J.Modifier.Type.Protected, J.Modifier.Type.Private)) {
                if (method.hasModifier(modifier)) {
                    visibility.add(modifier);
                }
            }
            List<Statement> statements = method.getBody() == null ? null : method.getBody().getStatements();
            if (statements == null) {
                body = ConstructorBody.NONE;
                delegatesToTestCase = false;
                delegationSignature = null;
            } else if (statements.isEmpty()) {
                body = ConstructorBody.EMPTY;
                delegatesToTestCase = false;
                delegationSignature = null;
            } else if (statements.size() == 1 && statements.get(0) instanceof J.MethodInvocation &&
                       safeArguments(((J.MethodInvocation) statements.get(0)).getArguments())) {
                J.MethodInvocation delegation = (J.MethodInvocation) statements.get(0);
                body = ConstructorBody.SAFE_DELEGATION;
                delegatesToTestCase = TestCaseVisitor.TEST_CASE_SUPER_MATCHER.matches(delegation);
                delegationSignature = constructorSignature(delegation.getMethodType());
            } else {
                body = ConstructorBody.OTHER;
                delegatesToTestCase = false;
                delegationSignature = null;
            }
        }
    }

    private static @Nullable String constructorSignature(JavaType.@Nullable Method method) {
        return method == null ? null : MethodMatcher.methodPattern(method);
    }

    private static @Nullable String constructorSignature(J.NewClass newClass) {
        JavaType.Method type = newClass.getConstructorType();
        if (type == null) {
            return null;
        }
        // An anonymous class gets its own constructor, which passes its arguments to the superclass constructor
        JavaType.FullyQualified supertype = type.getDeclaringType().getSupertype();
        return newClass.getBody() == null || supertype == null ? MethodMatcher.methodPattern(type) :
                MethodMatcher.methodPattern(type.withDeclaringType(supertype));
    }

    private static boolean hasGenericParameter(JavaType type) {
        if (type instanceof JavaType.GenericTypeVariable) {
            return true;
        }
        if (type instanceof JavaType.Array) {
            return hasGenericParameter(((JavaType.Array) type).getElemType());
        }
        return type instanceof JavaType.Parameterized &&
               ((JavaType.Parameterized) type).getTypeParameters().stream().anyMatch(MigrateJUnitTestCase::hasGenericParameter);
    }

    private static boolean safeArguments(List<Expression> arguments) {
        return arguments.stream().allMatch(argument -> argument instanceof J.Empty || argument instanceof J.Literal ||
                argument instanceof J.Identifier && ((J.Identifier) argument).getFieldType() != null &&
                ((J.Identifier) argument).getFieldType().getOwner() instanceof JavaType.Method);
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                if (!(tree instanceof JavaSourceFile)) {
                    return tree;
                }
                if (tree instanceof J.CompilationUnit) {
                    boolean skipped = new DeclaresMethod<>(TEST_CASE_RUN_MATCHER).visit(tree, ctx) != tree;
                    return constructorScanner(acc, skipped).visitNonNull(tree, ctx, getCursor().getParentOrThrow());
                }
                for (JavaType.Method method : ((JavaSourceFile) tree).getTypesInUse().getUsedMethods()) {
                    if (method.isConstructor()) {
                        acc.nonJavaReferencedClasses.add(method.getDeclaringType().getFullyQualifiedName());
                    }
                }
                return tree;
            }
        };
    }

    private JavaIsoVisitor<ExecutionContext> constructorScanner(Accumulator acc, boolean skipped) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                JavaType.Method type = method.getMethodType();
                if (method.isConstructor() && type != null) {
                    boolean migrated = getCursor().getPathAsStream()
                            .filter(J.ClassDeclaration.class::isInstance)
                            .allMatch(c -> isSupertypeTestCase(((J.ClassDeclaration) c).getType()));
                    Constructor constructor = new Constructor(method, type, migrated);
                    acc.declare(constructor);
                    if (skipped) {
                        acc.references.put(constructor.signature, true);
                    }
                }
                return super.visitMethodDeclaration(method, ctx);
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                String signature = constructorSignature(newClass);
                if (signature != null) {
                    acc.references.merge(signature, skipped || !safeArguments(newClass.getArguments()), Boolean::logicalOr);
                }
                return super.visitNewClass(newClass, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                JavaType.Method type = method.getMethodType();
                if (type != null && type.isConstructor()) {
                    acc.references.merge(MethodMatcher.methodPattern(type), skipped || !safeArguments(method.getArguments()), Boolean::logicalOr);
                }
                return super.visitMethodInvocation(method, ctx);
            }

            @Override
            public J.MemberReference visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
                JavaType.Method type = memberRef.getMethodType();
                if (type != null && type.isConstructor()) {
                    acc.references.put(MethodMatcher.methodPattern(type), true);
                }
                return super.visitMemberReference(memberRef, ctx);
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        Set<String> redundant = acc.redundantConstructors();
        // A TestCase overriding run(TestResult) participates in the JUnit 3 execution model,
        // which has no Jupiter equivalent, so leave it untouched rather than half-migrate it
        return Preconditions.check(Preconditions.not(new DeclaresMethod<>(TEST_CASE_RUN_MATCHER)), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                J.CompilationUnit c = redundant.isEmpty() ? cu :
                        (J.CompilationUnit) constructorVisitor(redundant).visitNonNull(cu, ctx, getCursor().getParentOrThrow());
                return (J.CompilationUnit) migrationVisitor(redundant).visitNonNull(c, ctx, getCursor().getParentOrThrow());
            }
        });
    }

    private JavaIsoVisitor<ExecutionContext> constructorVisitor(Set<String> redundant) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                if (method.getBody() == null || !redundant.contains(constructorSignature(method.getMethodType()))) {
                    return super.visitMethodDeclaration(method, ctx);
                }
                return method.withBody(method.getBody().withStatements(emptyList()));
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass n = super.visitNewClass(newClass, ctx);
                JavaType.Method type = n.getConstructorType();
                if (type == null || type.getParameterTypes().isEmpty() || !redundant.contains(constructorSignature(n))) {
                    return n;
                }
                return n.withArguments(emptyList())
                        .withConstructorType(type.withParameterTypes(emptyList()).withParameterNames(emptyList()));
            }

            @Override
            public J.@Nullable MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                JavaType.Method type = m.getMethodType();
                if (type == null || type.getParameterTypes().isEmpty() || !redundant.contains(MethodMatcher.methodPattern(type))) {
                    return m;
                }
                if ("super".equals(m.getSimpleName())) {
                    return null;
                }
                return m.withArguments(emptyList())
                        .withMethodType(type.withParameterTypes(emptyList()).withParameterNames(emptyList()));
            }
        };
    }

    private TreeVisitor<?, ExecutionContext> migrationVisitor(Set<String> redundant) {
        return Preconditions.check(Preconditions.or(
                        new UsesType<>("junit.framework.TestCase", false),
                        new UsesType<>("junit.framework.Assert", false)
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                        J.CompilationUnit c = super.visitCompilationUnit(cu, ctx);
                        doAfterVisit(new TestCaseVisitor(redundant));
                        // ChangeType for org.junit.Assert method invocations because TestCase extends org.junit.Assert
                        doAfterVisit(new ChangeType("junit.framework.TestCase", "org.junit.Assert", true).getVisitor());
                        doAfterVisit(new ChangeType("junit.framework.Assert", "org.junit.Assert", true).getVisitor());
                        doAfterVisit(new AssertToAssertions.AssertToAssertionsVisitor());
                        doAfterVisit(new UseStaticImport("org.junit.jupiter.api.Assertions assert*(..)").getVisitor());
                        doAfterVisit(new UseStaticImport("org.junit.jupiter.api.Assertions fail*(..)").getVisitor());
                        return c;
                    }

                    @Override
                    public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                        J.Block b = super.visitBlock(block, ctx);
                        // setName has no Jupiter equivalent, so the call is left behind as a comment
                        List<Statement> statements = new ArrayList<>(b.getStatements());
                        Space end = b.getEnd();
                        for (int i = statements.size() - 1; i >= 0; i--) {
                            Statement statement = statements.get(i);
                            if (statement instanceof J.MethodInvocation && SET_NAME_MATCHER.matches((J.MethodInvocation) statement)) {
                                statements.remove(i);
                                if (i < statements.size()) {
                                    statements.set(i, statements.get(i).withPrefix(commentOut(statement, statements.get(i).getPrefix())));
                                } else {
                                    end = commentOut(statement, end);
                                }
                            }
                        }
                        return statements.size() == b.getStatements().size() ? b : b.withStatements(statements).withEnd(end);
                    }

                    private Space commentOut(Statement statement, Space next) {
                        List<Comment> comments = new ArrayList<>(statement.getPrefix().getComments());
                        comments.add(new TextComment(false, statement.withPrefix(Space.EMPTY).printTrimmed(getCursor()) + ";",
                                next.getWhitespace(), Markers.EMPTY));
                        comments.addAll(next.getComments());
                        return statement.getPrefix().withComments(comments);
                    }

                    @Override
                    public J.@Nullable MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                        // setUp and tearDown will be invoked via Before and After annotations
                        if (SET_UP_MATCHER.matches(mi) || TEAR_DOWN_MATCHER.matches(mi)) {
                            return null;
                        }
                        return mi;
                    }
                });
    }

    private static class TestCaseVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final AnnotationMatcher OVERRIDE_ANNOTATION_MATCHER = new AnnotationMatcher("@java.lang.Override");
        private static final MethodMatcher TEST_CASE_SUPER_MATCHER = new MethodMatcher("junit.framework.TestCase <constructor>(..)");
        private static final Set<String> SUPERTYPES_REMOVED_BY_MIGRATION = new HashSet<>(asList(
                "junit.framework.TestCase", "junit.framework.Assert", "junit.framework.Test"));

        private final Set<String> redundant;

        TestCaseVisitor(Set<String> redundant) {
            this.redundant = redundant;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            if (!isSupertypeTestCase(classDecl.getType())) {
                return classDecl;
            }
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
            if (cd.getExtends() != null && cd.getExtends().getType() != null) {
                JavaType.FullyQualified fullQualifiedExtension = TypeUtils.asFullyQualified(cd.getExtends().getType());
                if (fullQualifiedExtension != null && "junit.framework.TestCase".equals(fullQualifiedExtension.getFullyQualifiedName())) {
                    cd = cd.withExtends(null);
                }
            }
            maybeRemoveImport("junit.framework.TestCase");
            return cd;
        }

        @Override
        public J.@Nullable MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
            updateCursor(md);

            JavaType.Method constructorType = md.getMethodType();
            if (md.isConstructor() && constructorType != null && redundant.contains(MethodMatcher.methodPattern(constructorType))) {
                J.ClassDeclaration owner = getCursor().firstEnclosingOrThrow(J.ClassDeclaration.class);
                J.MethodDeclaration noArgumentConstructor = owner.getBody().getStatements().stream()
                        .filter(J.MethodDeclaration.class::isInstance)
                        .map(J.MethodDeclaration.class::cast)
                        .filter(other -> other.isConstructor() && redundant.contains(constructorSignature(other.getMethodType())))
                        .min(Comparator.comparingInt(other -> other.getMethodType() == null ? Integer.MAX_VALUE : other.getMethodType().getParameterTypes().size()))
                        .orElse(md);
                if (!md.getId().equals(noArgumentConstructor.getId())) {
                    return null;
                }
                boolean hasOtherConstructors = owner.getBody().getStatements().stream()
                        .filter(J.MethodDeclaration.class::isInstance)
                        .map(J.MethodDeclaration.class::cast)
                        .anyMatch(other -> other.isConstructor() && !redundant.contains(constructorSignature(other.getMethodType())));
                if (!hasOtherConstructors && md.hasModifier(J.Modifier.Type.Public) && md.getLeadingAnnotations().isEmpty()) {
                    return null;
                }
                md = md.withParameters(emptyList())
                        .withMethodType(constructorType.withParameterTypes(emptyList()).withParameterNames(emptyList()));
            }

            // Remove suite() methods that return junit.framework.Test or TestSuite
            if (md.hasModifier(J.Modifier.Type.Static) &&
                SUITE_MATCHER.matches(md.getMethodType()) &&
                (TypeUtils.isOfClassType(md.getMethodType().getReturnType(), "junit.framework.Test") ||
                 TypeUtils.isOfClassType(md.getMethodType().getReturnType(), "junit.framework.TestSuite"))) {
                maybeRemoveImport("junit.framework.Test");
                maybeRemoveImport("junit.framework.TestSuite");
                return null;
            }

            if (md.getSimpleName().startsWith("test") && md.getLeadingAnnotations().stream().noneMatch(JUNIT_TEST_ANNOTATION_MATCHER::matches)) {
                md = updateMethodDeclarationAnnotationAndModifier(md, "@Test", "org.junit.jupiter.api.Test", ctx);
            } else if ("setUp".equals(md.getSimpleName()) && md.getLeadingAnnotations().stream().noneMatch(JUNIT_BEFORE_ANNOTATION_MATCHER::matches)) {
                md = updateMethodDeclarationAnnotationAndModifier(md, "@BeforeEach", "org.junit.jupiter.api.BeforeEach", ctx);
            } else if ("tearDown".equals(md.getSimpleName()) && md.getLeadingAnnotations().stream().noneMatch(JUNIT_AFTER_ANNOTATION_MATCHER::matches)) {
                md = updateMethodDeclarationAnnotationAndModifier(md, "@AfterEach", "org.junit.jupiter.api.AfterEach", ctx);
            }
            return maybeRemoveOverrideAnnotation(md, ctx);
        }

        @Override
        public  J.@Nullable MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            // If the class no longer extends TestCase there should no longer be calls to TestCase.super()
            if (TEST_CASE_SUPER_MATCHER.matches(method)) {
                return null;
            }
            return super.visitMethodInvocation(method, ctx);
        }

        private J.MethodDeclaration updateMethodDeclarationAnnotationAndModifier(J.MethodDeclaration methodDeclaration, String annotation, String fullyQualifiedAnnotation, ExecutionContext ctx) {
            J.MethodDeclaration md = methodDeclaration;
            if (FindAnnotations.find(methodDeclaration.withBody(null), "@" + fullyQualifiedAnnotation).isEmpty()) {
                md = JavaTemplate.builder(annotation)
                        .javaParser(JavaParser.fromJavaVersion()
                                .classpathFromResources(ctx, "junit-jupiter-api-5"))
                        .imports(fullyQualifiedAnnotation).build()
                        .apply(getCursor(), methodDeclaration.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                md = maybeAddPublicModifier(md);
                maybeAddImport(fullyQualifiedAnnotation);
            }
            return md;
        }

        private J.MethodDeclaration maybeAddPublicModifier(J.MethodDeclaration md) {
            List<J.Modifier> modifiers = ListUtils.map(md.getModifiers(), modifier -> {
                if (modifier.getType() == J.Modifier.Type.Protected) {
                    return modifier.withType(J.Modifier.Type.Public);
                }
                return modifier;
            });
            return md.withModifiers(modifiers);
        }

        private J.MethodDeclaration maybeRemoveOverrideAnnotation(J.MethodDeclaration md, ExecutionContext ctx) {
            JavaType.Method methodType = md.getMethodType();
            if (methodType == null ||
                md.getLeadingAnnotations().stream().noneMatch(OVERRIDE_ANNOTATION_MATCHER::matches) ||
                stillOverridesAfterMigration(methodType)) {
                return md;
            }
            J.MethodDeclaration withoutBody = (J.MethodDeclaration) new RemoveAnnotationVisitor(OVERRIDE_ANNOTATION_MATCHER)
                    .visitNonNull(md.withBody(null), ctx, getCursor().getParentOrThrow());
            return withoutBody.withBody(md.getBody());
        }

        private static boolean stillOverridesAfterMigration(JavaType.Method method) {
            Optional<JavaType.Method> overridden = TypeUtils.findOverriddenMethod(method);
            while (overridden.isPresent()) {
                if (!SUPERTYPES_REMOVED_BY_MIGRATION.contains(overridden.get().getDeclaringType().getFullyQualifiedName())) {
                    return true;
                }
                overridden = TypeUtils.findOverriddenMethod(overridden.get());
            }
            return false;
        }
    }
}
