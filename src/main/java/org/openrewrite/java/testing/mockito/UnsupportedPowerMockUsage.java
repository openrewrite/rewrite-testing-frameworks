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
package org.openrewrite.java.testing.mockito;

import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.config.Environment;
import org.openrewrite.config.YamlResourceLoader;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.*;

import static java.util.Arrays.asList;
import static org.openrewrite.java.testing.mockito.PowerMockitoMockStaticToMockito.classLiteral;

/// Finds PowerMock usages that `ReplacePowerMockitoUsages` leaves in place or migrates into code that behaves
/// differently. Migrating the other usages while the dependencies are replaced would break such tests, so the
/// repository is left on PowerMock instead.
///
/// Rather than restating which shapes each migration step supports, the steps are run on the source file, and the
/// PowerMock usages that remain are reported on the corresponding nodes of the original source file.
final class UnsupportedPowerMockUsage {

    private static final String POWER_MOCKITO = "org.powermock.api.mockito.PowerMockito";

    private static final List<String> MOCKITO_REFUSES_TO_MOCK_STATIC = asList(
            "java.lang.System", "java.lang.Thread", "java.lang.Class", "java.lang.ClassLoader",
            "java.util.concurrent.ConcurrentHashMap");

    private static final MethodMatcher MOCK_STATIC = new MethodMatcher(POWER_MOCKITO + " mockStatic(..)");
    private static final MethodMatcher SPY_CLASS = new MethodMatcher(POWER_MOCKITO + " spy(java.lang.Class)");
    private static final MethodMatcher MOCKITO_WHEN = new MethodMatcher("org.mockito.Mockito when(..)");
    private static final MethodMatcher MOCKITO_SPY = new MethodMatcher("org.mockito.Mockito spy(..)");
    private static final MethodMatcher MOCKITO_MOCK_STATIC = new MethodMatcher("org.mockito.Mockito mockStatic(..)");
    private static final List<MethodMatcher> CREATES_MOCK_OR_SPY = asList(
            new MethodMatcher("org.mockito.Mockito mock(..)"),
            MOCKITO_SPY,
            new MethodMatcher(POWER_MOCKITO + " mock(..)"),
            new MethodMatcher(POWER_MOCKITO + " spy(..)"));
    private static final List<String> MOCK_OR_SPY_ANNOTATIONS = asList("org.mockito.Mock", "org.mockito.Spy");

    private static final String POWERMOCKITO_YML = "/META-INF/rewrite/powermockito.yml";

    private static @Nullable List<Recipe> migrationSteps;

    private UnsupportedPowerMockUsage() {
    }

    /// Returns the ids of the nodes in the source file using PowerMock in a way that cannot be migrated, each with
    /// the reason.
    static Map<UUID, String> find(JavaSourceFile sourceFile, ExecutionContext ctx) {
        Map<UUID, String> unsupported = new LinkedHashMap<>();
        findChangedBehavior(sourceFile, unsupported);
        findRemainingUsage(sourceFile, migrate(sourceFile, ctx), unsupported);
        return unsupported;
    }

    private static JavaSourceFile migrate(JavaSourceFile sourceFile, ExecutionContext ctx) {
        Tree migrated = sourceFile;
        for (Recipe step : migrationSteps()) {
            TreeVisitor<?, ExecutionContext> visitor = step.getVisitor();
            if (!visitor.isAcceptable((JavaSourceFile) migrated, ctx)) {
                continue;
            }
            try {
                Tree visited = visitor.visit(migrated, ctx);
                if (visited != null) {
                    migrated = visited;
                }
            } catch (RuntimeException e) {
                // A step that fails on a source file leaves it unchanged in a recipe run as well
            }
        }
        return (JavaSourceFile) migrated;
    }

    private static synchronized List<Recipe> migrationSteps() {
        if (migrationSteps == null) {
            try (InputStream yaml = UnsupportedPowerMockUsage.class.getResourceAsStream(POWERMOCKITO_YML)) {
                Recipe replaceUsages = Environment.builder()
                        .load(new YamlResourceLoader(Objects.requireNonNull(yaml), URI.create(POWERMOCKITO_YML), null,
                                UnsupportedPowerMockUsage.class.getClassLoader()))
                        .build()
                        .activateRecipes("org.openrewrite.java.testing.mockito.ReplacePowerMockitoUsages");
                List<Recipe> steps = new ArrayList<>();
                addLeafRecipes(replaceUsages, steps);
                migrationSteps = steps;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return migrationSteps;
    }

    private static void addLeafRecipes(Recipe recipe, List<Recipe> leaves) {
        if (recipe.getRecipeList().isEmpty()) {
            leaves.add(recipe);
        }
        for (Recipe child : recipe.getRecipeList()) {
            addLeafRecipes(child, leaves);
        }
    }

    private static void findChangedBehavior(JavaSourceFile sourceFile, Map<UUID, String> unsupported) {
        Set<String> staticallyMocked = staticallyMockedTypes(sourceFile);
        List<JavaType.Variable> mocksAndSpies = mocksAndSpies(sourceFile);
        List<WhiteboxToReflectionVisitor> whiteboxMigrations = asList(
                new PowerMockWhiteboxGetInternalStateToJavaReflection.GetInternalStateVisitor(),
                new PowerMockWhiteboxSetInternalStateToJavaReflection.SetInternalStateVisitor(),
                new PowerMockWhiteboxInvokeMethodToJavaReflection.InvokeMethodVisitor());
        new JavaIsoVisitor<Map<UUID, String>>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Map<UUID, String> p) {
                String reason = mockStaticRefusedByMockito(method);
                if (reason == null) {
                    reason = inheritedStaticMethodOfMockedClass(method, staticallyMocked);
                }
                if (reason == null) {
                    reason = whiteboxOnRuntimeClass(method, getCursor(), whiteboxMigrations, mocksAndSpies);
                }
                if (reason != null) {
                    p.put(method.getId(), reason);
                }
                return super.visitMethodInvocation(method, p);
            }
        }.visit(sourceFile, unsupported);
    }

    // A list rather than a set, as JavaType.Variable overrides equals but not hashCode
    private static List<JavaType.Variable> mocksAndSpies(JavaSourceFile sourceFile) {
        return new JavaIsoVisitor<List<JavaType.Variable>>() {
            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, List<JavaType.Variable> p) {
                for (J.Annotation annotation : multiVariable.getLeadingAnnotations()) {
                    for (String mockOrSpy : MOCK_OR_SPY_ANNOTATIONS) {
                        if (TypeUtils.isOfClassType(annotation.getType(), mockOrSpy)) {
                            for (J.VariableDeclarations.NamedVariable variable : multiVariable.getVariables()) {
                                addIfNotNull(p, variable.getVariableType());
                            }
                        }
                    }
                }
                return super.visitVariableDeclarations(multiVariable, p);
            }

            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, List<JavaType.Variable> p) {
                if (createsMockOrSpy(variable.getInitializer())) {
                    addIfNotNull(p, variable.getVariableType());
                }
                return super.visitVariable(variable, p);
            }

            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, List<JavaType.Variable> p) {
                if (createsMockOrSpy(assignment.getAssignment())) {
                    addIfNotNull(p, variableOf(assignment.getVariable()));
                }
                return super.visitAssignment(assignment, p);
            }
        }.reduce(sourceFile, new ArrayList<>());
    }

    private static boolean createsMockOrSpy(@Nullable Expression expression) {
        if (expression instanceof J.MethodInvocation) {
            for (MethodMatcher matcher : CREATES_MOCK_OR_SPY) {
                if (matcher.matches(expression)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static JavaType.@Nullable Variable variableOf(Expression expression) {
        Expression unwrapped = expression.unwrap();
        if (unwrapped instanceof J.Identifier) {
            return ((J.Identifier) unwrapped).getFieldType();
        }
        if (unwrapped instanceof J.FieldAccess) {
            return ((J.FieldAccess) unwrapped).getName().getFieldType();
        }
        return null;
    }

    private static <T> void addIfNotNull(Collection<T> collection, @Nullable T element) {
        if (element != null) {
            collection.add(element);
        }
    }

    private static Set<String> staticallyMockedTypes(JavaSourceFile sourceFile) {
        return new JavaIsoVisitor<Set<String>>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Set<String> p) {
                if (MOCK_STATIC.matches(method) || SPY_CLASS.matches(method)) {
                    for (Expression argument : method.getArguments()) {
                        JavaType.FullyQualified type = classLiteral(argument);
                        if (type != null) {
                            p.add(type.getFullyQualifiedName());
                        }
                    }
                }
                return super.visitMethodInvocation(method, p);
            }
        }.reduce(sourceFile, new HashSet<>());
    }

    private static @Nullable String mockStaticRefusedByMockito(J.MethodInvocation mi) {
        if (!MOCK_STATIC.matches(mi)) {
            return null;
        }
        for (Expression argument : mi.getArguments()) {
            JavaType.FullyQualified mocked = classLiteral(argument);
            if (mocked != null && MOCKITO_REFUSES_TO_MOCK_STATIC.contains(mocked.getFullyQualifiedName())) {
                return "`mockStatic(" + mocked.getClassName() + ".class)` cannot be migrated, as Mockito does not " +
                       "mock the static methods of `" + mocked.getClassName() + "`";
            }
        }
        return null;
    }

    /// `Mockito.mockStatic(Type.class)` only intercepts static methods declared by `Type` itself, whereas PowerMock
    /// also intercepted those that `Type` inherits.
    private static @Nullable String inheritedStaticMethodOfMockedClass(J.MethodInvocation mi, Set<String> staticallyMocked) {
        JavaType.Method type = mi.getMethodType();
        if (type == null || !type.hasFlags(Flag.Static) || !(mi.getSelect() instanceof TypeTree)) {
            return null;
        }
        JavaType.FullyQualified receiver = TypeUtils.asFullyQualified(mi.getSelect().getType());
        if (receiver == null || !staticallyMocked.contains(receiver.getFullyQualifiedName()) ||
            staticallyMocked.contains(type.getDeclaringType().getFullyQualifiedName())) {
            return null;
        }
        return "Static mocking of `" + receiver.getClassName() + "." + mi.getSimpleName() + "()` cannot be migrated, as " +
               "`Mockito.mockStatic` does not intercept static methods inherited from `" + type.getDeclaringType().getClassName() + "`";
    }

    private static @Nullable String whiteboxOnRuntimeClass(J.MethodInvocation mi, Cursor cursor,
                                                           List<WhiteboxToReflectionVisitor> whiteboxMigrations,
                                                           List<JavaType.Variable> mocksAndSpies) {
        for (WhiteboxToReflectionVisitor visitor : whiteboxMigrations) {
            if (visitor.matches(mi)) {
                visitor.setCursor(cursor);
                if (!visitor.fallsBackToRuntimeClass(mi)) {
                    return null;
                }
                if (mocksAndSpies.contains(variableOf(mi.getArguments().get(0)))) {
                    return "`Whitebox." + mi.getSimpleName() + "` cannot be migrated, as the runtime class of a " +
                           "Mockito mock or spy does not declare the member it accesses";
                }
                if (visitor.declaredInSuperclassOfTarget(mi)) {
                    return "`Whitebox." + mi.getSimpleName() + "` cannot be migrated, as the member it accesses is " +
                           "declared in a superclass that the test cannot reference";
                }
                return null;
            }
        }
        return null;
    }

    private static void findRemainingUsage(JavaSourceFile original, JavaSourceFile migrated, Map<UUID, String> unsupported) {
        Set<UUID> originalIds = new JavaIsoVisitor<Set<UUID>>() {
            @Override
            public @Nullable J preVisit(J tree, Set<UUID> p) {
                p.add(tree.getId());
                return tree;
            }
        }.reduce(original, new HashSet<>());

        new JavaIsoVisitor<Map<UUID, String>>() {
            @Override
            public J.Import visitImport(J.Import anImport, Map<UUID, String> p) {
                return anImport;
            }

            @Override
            public J.Package visitPackage(J.Package pkg, Map<UUID, String> p) {
                return pkg;
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, Map<UUID, String> p) {
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(annotation.getType());
                if (isPowerMock(type)) {
                    report("`@" + type.getClassName() + "`", p);
                    return annotation;
                }
                return super.visitAnnotation(annotation, p);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Map<UUID, String> p) {
                JavaType.Method type = method.getMethodType();
                if (type != null && isPowerMock(type.getDeclaringType())) {
                    if (method.getSelect() instanceof J.MethodInvocation && startsPowerMockChain((J.MethodInvocation) method.getSelect())) {
                        visit(method.getSelect(), p);
                    } else {
                        report("`" + type.getDeclaringType().getClassName() + "." + method.getSimpleName() + "`", p);
                    }
                    return method;
                }
                String retargeted = powerMockOverloadRetargetedToMockito(method);
                if (retargeted != null) {
                    report(retargeted, p);
                    return method;
                }
                return super.visitMethodInvocation(method, p);
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, Map<UUID, String> p) {
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(newClass.getType());
                if (isPowerMock(type)) {
                    report("`" + type.getClassName() + "`", p);
                }
                return super.visitNewClass(newClass, p);
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, Map<UUID, String> p) {
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(fieldAccess.getType());
                if (isPowerMock(type)) {
                    report("`" + type.getClassName() + "`", p);
                    return fieldAccess;
                }
                return super.visitFieldAccess(fieldAccess, p);
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, Map<UUID, String> p) {
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(identifier.getType());
                if (isPowerMock(type) && !(getCursor().getParentTreeCursor().getValue() instanceof J.VariableDeclarations.NamedVariable) &&
                    !(getCursor().getParentTreeCursor().getValue() instanceof J.NewClass)) {
                    report("`" + type.getClassName() + "`", p);
                }
                return identifier;
            }

            private void report(String usage, Map<UUID, String> p) {
                for (Iterator<Object> path = getCursor().getPath(); path.hasNext(); ) {
                    Object node = path.next();
                    if (node instanceof J && originalIds.contains(((J) node).getId())) {
                        p.putIfAbsent(((J) node).getId(), usage + " could not be migrated automatically");
                        return;
                    }
                }
            }
        }.visit(migrated, unsupported);
    }

    private static boolean startsPowerMockChain(J.MethodInvocation mi) {
        return (mi.getMethodType() != null && isPowerMock(mi.getMethodType().getDeclaringType())) ||
               powerMockOverloadRetargetedToMockito(mi) != null;
    }

    private static boolean isPowerMock(JavaType.@Nullable FullyQualified type) {
        return type != null && type.getFullyQualifiedName().startsWith("org.powermock.");
    }

    /// `ChangeMethodTargetToStatic` moves every `PowerMockito` overload of a name to `Mockito`, including those that
    /// Mockito lacks, such as `when(Object, String, Object...)` and the static `spy(Class)` that returns nothing.
    private static @Nullable String powerMockOverloadRetargetedToMockito(J.MethodInvocation mi) {
        List<Expression> arguments = mi.getArguments();
        if (MOCKITO_WHEN.matches(mi) && arguments.size() > 1) {
            return "`PowerMockito.when` with more than one argument";
        }
        if (MOCKITO_SPY.matches(mi) && mi.getMethodType() != null && mi.getMethodType().getReturnType() == JavaType.Primitive.Void) {
            return "`PowerMockito.spy(Class)`";
        }
        if (MOCKITO_MOCK_STATIC.matches(mi) && arguments.size() > 1 && classLiteral(arguments.get(1)) != null) {
            return "`PowerMockito.mockStatic` of several classes";
        }
        return null;
    }
}
