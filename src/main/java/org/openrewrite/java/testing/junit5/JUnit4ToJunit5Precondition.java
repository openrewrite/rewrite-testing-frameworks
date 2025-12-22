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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.trait.Annotated;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toMap;

/**
 * A search recipe that identifies JUnit 4 test classes migratable to JUnit 5 and marks them for
 * inclusion in the JUnit 4 to JUnit 5 migration recipe.
 *
 * <p>It marks classes that:
 *
 * <ul>
 *   <li>Use only supported rules and runners.
 *   <li>Either extend or are extended by classes that also use only supported rules and runners.
 *   <li>Do not use @Parameters annotations with class-type values for their source attributes
 *       (e.g., @Parameters(source = SomeClass.class)).
 * </ul>
 */
@EqualsAndHashCode(callSuper = false)
@Value
public class JUnit4ToJunit5Precondition extends ScanningRecipe<JUnit4ToJunit5Precondition.MigratabilityAccumulator> {
    private static final String HAS_UNSUPPORTED_RULE = "hasUnsupportedRule";
    private static final String HAS_UNSUPPORTED_RUNNER = "hasUnsupportedRunner";
    private static final String HAS_CLASS_TYPE_SOURCE_ATTRIBUTE = "hasClassTypeSourceAttribute";

    @Option(example = "TODO Provide a usage example for the docs", displayName = "Known migratable classes",
            description = "A list of classes which are migratable. These are the classes for which recipes already exist. In practical scenarios, these are parent test classes for which we already have JUnit 5 versions.")
    @Nullable Set<String> knownMigratableClasses;

    @Option(example = "TODO Provide a usage example for the docs", displayName = "Supported rules",
            description = "Rules for which migration recipes exist.")
    @Nullable Set<String> supportedRules;

    @Option(example = "TODO Provide a usage example for the docs", displayName = "Supported rule types",
            description = "Recipe exist for rule types and all their inheriting rules (e.g., ExternalRules).")
    @Nullable Set<String> supportedRuleTypes;

    @Option(example = "TODO Provide a usage example for the docs", displayName = "Supported runners",
            description = "Runners for which migration recipes exist.")
    @Nullable Set<String> supportedRunners;

    @Override
    public String getDisplayName() {
        return "JUnit 4 to 5 Precondition";
    }

    @Override
    public String getDescription() {
        return "Marks JUnit 4 test classes that can be migrated to JUnit 5 with current recipe " +
                "capabilities, including detection of unsupported rules, runners, and @Parameters annotations with " +
                "class-type source attributes.";
    }

    @Override
    public MigratabilityAccumulator getInitialValue(ExecutionContext ctx) {
        return new MigratabilityAccumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(MigratabilityAccumulator acc) {
        return new JUnit4ToJunit5PreconditionScanner(acc);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(MigratabilityAccumulator acc) {
        return new JUnit4ToJunit5PreconditionVisitor(acc);
    }


    private class JUnit4ToJunit5PreconditionVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final MigratabilityAccumulator accumulator;

        private JUnit4ToJunit5PreconditionVisitor(MigratabilityAccumulator accumulator) {
            this.accumulator = accumulator;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(
                J.ClassDeclaration classDecl, ExecutionContext ctx) {
            if (classDecl.getType() == null) { // missing type attribution, possibly parsing error.
                return classDecl;
            }
            String fullQualifiedClassName = classDecl.getType().getFullyQualifiedName();
            return accumulator.isMigratable(fullQualifiedClassName) ?
                    SearchResult.found(classDecl) :
                    classDecl;
        }

        boolean extendsSupportedJUnit4BaseTestClass(J.ClassDeclaration classDecl) {
            return emptySetIfNull(knownMigratableClasses).stream().
                    anyMatch(testBaseClass -> TypeUtils.isAssignableTo(testBaseClass, classDecl.getType()));
        }
    }

    /**
     * A visitor that implements the scanning logic for identifying JUnit 4 test classes that can be
     * migrated to JUnit 5.
     */
    private class JUnit4ToJunit5PreconditionScanner extends JavaIsoVisitor<ExecutionContext> {

        private final MigratabilityAccumulator accumulator;

        private JUnit4ToJunit5PreconditionScanner(MigratabilityAccumulator accumulator) {
            this.accumulator = accumulator;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(
                J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
            markSupportedRunner(cd, ctx);

            if (classDecl.getType() != null) {
                this.accumulator.registerClass(
                        classDecl.getType().getFullyQualifiedName(),
                        getParentClassName(classDecl),
                        !hasUnsupportedFeatures());
            }
            return classDecl;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(
                J.MethodDeclaration methodDecl, ExecutionContext ctx) {
            // Flag @Parameters annotations with class-type source attributes
            flagParametersAnnotationWithClassTypeSourceAttribute(methodDecl, ctx);
            if (hasJunit4Rules(methodDecl) && methodDecl.getMethodType() != null) {
                flagUnsupportedRule(methodDecl.getMethodType().getReturnType());
            }
            return methodDecl;
        }

        @Override
        public J.VariableDeclarations visitVariableDeclarations(
                J.VariableDeclarations variableDeclarations, ExecutionContext ctx) {
            if (!hasJunit4Rules(variableDeclarations)) {
                return variableDeclarations;
            }
            return super.visitVariableDeclarations(variableDeclarations, ctx);
        }

        @Override
        public J.VariableDeclarations.NamedVariable visitVariable(
                J.VariableDeclarations.NamedVariable variable, ExecutionContext ctx) {
            if (variable.getInitializer() != null) {
                flagUnsupportedRule(variable.getInitializer().getType());
            }
            return variable;
        }

        // Flag @Parameters annotations with class-type source attributes
        private void flagParametersAnnotationWithClassTypeSourceAttribute(
                J.MethodDeclaration methodDecl, ExecutionContext ctx) {
            Cursor methodCursor = getCursor();
            new Annotated.Matcher("junitparams.Parameters")
                    .asVisitor(a ->
                            (new JavaIsoVisitor<ExecutionContext>() {
                                @Override
                                public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ctx) {
                                    Expression variable = assignment.getVariable();
                                    if (variable instanceof J.Identifier) {
                                        J.Identifier identifier = (J.Identifier) variable;
                                        if ("source".equals(identifier.getSimpleName()) &&
                                                assignment.getAssignment() instanceof J.FieldAccess &&
                                                "class".equals(((J.FieldAccess) assignment.getAssignment()).getSimpleName())) {
                                            methodCursor.dropParentUntil(J.ClassDeclaration.class::isInstance)
                                                    .putMessage(HAS_CLASS_TYPE_SOURCE_ATTRIBUTE, true);
                                        }
                                    }
                                    return assignment;
                                }
                            }).visit(a.getTree(), ctx))
                    .visit(methodDecl, ctx);
        }

        private void flagUnsupportedRule(JavaType javaType) {
            JavaType.FullyQualified ruleType = TypeUtils.asFullyQualified(javaType);
            if (ruleType != null &&
                    !emptySetIfNull(supportedRules).contains(ruleType.getFullyQualifiedName()) &&
                    emptySetIfNull(supportedRuleTypes).stream().noneMatch(s -> TypeUtils.isAssignableTo(s, javaType))) {
                getCursor()
                        .dropParentUntil(J.ClassDeclaration.class::isInstance)
                        .putMessage(HAS_UNSUPPORTED_RULE, true);
            }
        }

        private void markSupportedRunner(
                J.ClassDeclaration classDecl, ExecutionContext ctx) {
            Cursor classCursor = getCursor();
            new Annotated.Matcher(Junit4Utils.RUN_WITH_ANNOTATION).asVisitor(a ->
                            (new JavaIsoVisitor<ExecutionContext>() {
                                @Override
                                public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                                    JavaType.FullyQualified type =
                                            TypeUtils.asFullyQualified(fieldAccess.getTarget().getType());
                                    if (type == null) { // missing type attribution, possibly parsing error
                                        return fieldAccess;
                                    }
                                    if (!emptySetIfNull(supportedRunners).contains(type.getFullyQualifiedName())) {
                                        classCursor.putMessage(HAS_UNSUPPORTED_RUNNER, true);
                                    }
                                    return SearchResult.found(fieldAccess);
                                }
                            }).visit(a.getTree(), ctx))
                    .visit(classDecl, ctx);
        }

        private boolean hasUnsupportedFeatures() {
            return Boolean.TRUE.equals(getCursor().getMessage(HAS_UNSUPPORTED_RULE)) ||
                    Boolean.TRUE.equals(getCursor().getMessage(HAS_UNSUPPORTED_RUNNER)) ||
                    Boolean.TRUE.equals(getCursor().getMessage(HAS_CLASS_TYPE_SOURCE_ATTRIBUTE));
        }

        private @Nullable String getParentClassName(J.ClassDeclaration classDecl) {
            if (classDecl.getExtends() != null) {
                JavaType.FullyQualified parentClass =
                        TypeUtils.asFullyQualified(classDecl.getExtends().getType());
                if (parentClass != null) {
                    return parentClass.getFullyQualifiedName();
                }
            }
            return null;
        }

        private boolean hasJunit4Rules(J j) {
            return matchRule(j, Junit4Utils.RULE) || matchRule(j, Junit4Utils.CLASS_RULE);
        }

        private boolean matchRule(J j, String rule) {
            return new Annotated.Matcher(rule)
                    .<AtomicBoolean>asVisitor((a, found) -> {
                        found.set(true);
                        return a.getTree();
                    }).reduce(j, new AtomicBoolean(false)).get();
        }
    }

    /** Accumulator to store migratability information of classes. */
    public class MigratabilityAccumulator {

        private final Map<String, String> classToParentMap = new HashMap<>();
        private final Map<String, Boolean> declaredMigratable = emptySetIfNull(knownMigratableClasses).stream()
                .collect(toMap(Function.identity(), key -> Boolean.TRUE));

        /** Registers a class with its parent and whether it is migratable. */
        public void registerClass(String className, @Nullable String parentClassName, boolean isMigratable) {
            classToParentMap.put(className, parentClassName);
            if (isMigratable) {
                declaredMigratable.put(className, true);
                if (parentClassName != null) {
                    declaredMigratable.putIfAbsent(parentClassName, false);
                }
            } else {
                // Mark this class and all its ancestors as non-migratable
                String currentClass = className;
                while (currentClass != null && !emptySetIfNull(knownMigratableClasses).contains(currentClass)) {
                    declaredMigratable.put(currentClass, false);
                    currentClass = classToParentMap.get(currentClass);
                }
            }
        }

        /** Returns true if the class and all of its ancestors are declared migratable. */
        public boolean isMigratable(String className) {
            String currentClass = className;
            while (currentClass != null) {
                Boolean isMigratable = declaredMigratable.get(currentClass);
                if (isMigratable == null || !isMigratable) {
                    return false;
                }
                currentClass = classToParentMap.get(currentClass);
            }
            return true;
        }
    }

    private static Set<String> emptySetIfNull(@Nullable Set<String> set) {
        return set == null ? emptySet() : set;
    }
}
