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
package org.openrewrite.java.testing.junit5;

import lombok.Getter;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.ShortenFullyQualifiedTypeReferences;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.*;

import static java.util.Collections.singletonList;
import static java.util.Comparator.comparing;
import static org.openrewrite.java.testing.junit5.SystemRules.*;

public class SystemPropertyRulesToPioneer extends Recipe {
    private static final String PROVIDE_SYSTEM_PROPERTY = PACKAGE + ".ProvideSystemProperty";
    private static final String CLEAR_SYSTEM_PROPERTIES = PACKAGE + ".ClearSystemProperties";
    private static final String RESTORE_SYSTEM_PROPERTIES = PACKAGE + ".RestoreSystemProperties";

    private static final String PIONEER_SET = "org.junitpioneer.jupiter.SetSystemProperty";
    private static final String PIONEER_CLEAR = "org.junitpioneer.jupiter.ClearSystemProperty";
    private static final String PIONEER_RESTORE = "org.junitpioneer.jupiter.RestoreSystemProperties";

    private static final MethodMatcher PROVIDE_AND = new MethodMatcher(PROVIDE_SYSTEM_PROPERTY + " and(String, String)");

    @Getter
    final String displayName = "Migrate System Rules system property rules to JUnit Pioneer annotations";

    @Getter
    final String description = "Replaces System Rules' `ProvideSystemProperty`, `ClearSystemProperties` and `RestoreSystemProperties` rules with JUnit Pioneer's `@SetSystemProperty`, `@ClearSystemProperty` and `@RestoreSystemProperties` class annotations. Only rules that take string literals and are not used elsewhere in the test are migrated; `SystemRulesToSystemStubs` handles the rest.";

    // Dependencies are only added once a later cycle scans the migrated code
    @Override
    public boolean causesAnotherCycle() {
        return true;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(PACKAGE + ".*", false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                return sourceFile instanceof J.CompilationUnit;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                JavaSourceFile sourceFile = getCursor().firstEnclosingOrThrow(JavaSourceFile.class);

                Map<Statement, List<PioneerAnnotation>> annotationsByField = new IdentityHashMap<>();
                List<PioneerAnnotation> annotations = new ArrayList<>();
                for (Statement statement : cd.getBody().getStatements()) {
                    if (statement instanceof J.VariableDeclarations) {
                        J.VariableDeclarations field = (J.VariableDeclarations) statement;
                        List<PioneerAnnotation> fieldAnnotations = pioneerAnnotations(field);
                        if (fieldAnnotations != null && isOnlyDeclared(field, sourceFile) &&
                            !mayBeUsedElsewhere(field, getCursor())) {
                            annotationsByField.put(field, fieldAnnotations);
                            annotations.addAll(fieldAnnotations);
                        }
                    }
                }
                if (annotations.isEmpty() || hasConflictingKeys(cd, annotations)) {
                    return cd;
                }

                cd = cd.withBody(cd.getBody().withStatements(
                        ListUtils.map(cd.getBody().getStatements(), s -> annotationsByField.containsKey(s) ? null : s)));
                maybeRemoveImport(RULE);
                maybeRemoveImport(CLASS_RULE);
                maybeRemoveImport(PROVIDE_SYSTEM_PROPERTY);
                maybeRemoveImport(CLEAR_SYSTEM_PROPERTIES);
                maybeRemoveImport(RESTORE_SYSTEM_PROPERTIES);
                for (Statement field : annotationsByField.keySet()) {
                    String supertype = declaredSupertype((J.VariableDeclarations) field);
                    if (supertype != null) {
                        maybeRemoveImport(supertype);
                    }
                }

                boolean restoring = hasAnnotation(cd, PIONEER_RESTORE);
                for (PioneerAnnotation annotation : annotations) {
                    if (annotation.fqn.equals(PIONEER_RESTORE)) {
                        if (restoring) {
                            continue;
                        }
                        restoring = true;
                    }
                    maybeAddImport(annotation.fqn);
                    cd = JavaTemplate.builder(annotation.template)
                            .imports(annotation.fqn)
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-pioneer-2", "junit-jupiter-api-5"))
                            .build()
                            .apply(updateCursor(cd), cd.getCoordinates().addAnnotation(comparing(J.Annotation::getSimpleName)));
                }
                // Pioneer's @RestoreSystemProperties shares its simple name with the System Rules import being removed
                for (J.Annotation annotation : cd.getLeadingAnnotations()) {
                    if (annotation.getAnnotationType() instanceof J.FieldAccess &&
                        TypeUtils.isOfClassType(annotation.getType(), PIONEER_RESTORE)) {
                        doAfterVisit(ShortenFullyQualifiedTypeReferences.modifyOnly(annotation));
                    }
                }
                return cd;
            }

            private boolean isOnlyDeclared(J.VariableDeclarations field, JavaSourceFile sourceFile) {
                JavaType.Variable variable = field.getVariables().get(0).getVariableType();
                return variable != null && references(sourceFile, new Cursor(null, Cursor.ROOT_VALUE), variable).isEmpty();
            }
        });
    }

    private static @Nullable List<PioneerAnnotation> pioneerAnnotations(J.VariableDeclarations field) {
        if (!isRuleField(field)) {
            return null;
        }
        String type = fieldTypeName(field);
        Expression initializer = field.getVariables().get(0).getInitializer();
        if (type == null || initializer == null) {
            return null;
        }
        switch (type) {
            case PROVIDE_SYSTEM_PROPERTY:
                return provideAnnotations(initializer);
            case CLEAR_SYSTEM_PROPERTIES:
                return clearAnnotations(initializer);
            case RESTORE_SYSTEM_PROPERTIES:
                return initializer instanceof J.NewClass ? singletonList(PioneerAnnotation.restore()) : null;
            default:
                return null;
        }
    }

    private static @Nullable List<PioneerAnnotation> provideAnnotations(Expression initializer) {
        Deque<List<Expression>> pairs = new ArrayDeque<>();
        Expression current = initializer;
        while (current instanceof J.MethodInvocation && PROVIDE_AND.matches((J.MethodInvocation) current)) {
            J.MethodInvocation and = (J.MethodInvocation) current;
            pairs.push(arguments(and));
            current = and.getSelect();
        }
        if (!(current instanceof J.NewClass)) {
            return null;
        }
        List<Expression> constructorArguments = arguments((J.NewClass) current);
        if (constructorArguments.size() == 2) {
            pairs.push(constructorArguments);
        } else if (!constructorArguments.isEmpty()) {
            return null;
        }

        List<PioneerAnnotation> annotations = new ArrayList<>();
        for (List<Expression> pair : pairs) {
            Expression key = pair.get(0);
            Expression value = pair.get(1);
            if (!isStringLiteral(key)) {
                return null;
            }
            if (J.Literal.isLiteralValue(value, null)) {
                annotations.add(PioneerAnnotation.clear((J.Literal) key));
            } else if (isStringLiteral(value)) {
                annotations.add(PioneerAnnotation.set((J.Literal) key, (J.Literal) value));
            } else {
                return null;
            }
        }
        return annotations.isEmpty() ? null : annotations;
    }

    private static @Nullable List<PioneerAnnotation> clearAnnotations(Expression initializer) {
        if (!(initializer instanceof J.NewClass)) {
            return null;
        }
        List<PioneerAnnotation> annotations = new ArrayList<>();
        for (Expression key : arguments((J.NewClass) initializer)) {
            if (!isStringLiteral(key)) {
                return null;
            }
            annotations.add(PioneerAnnotation.clear((J.Literal) key));
        }
        return annotations.isEmpty() ? null : annotations;
    }

    // Pioneer rejects a class that sets or clears the same key more than once, which System Rules allowed
    private static boolean hasConflictingKeys(J.ClassDeclaration cd, List<PioneerAnnotation> annotations) {
        Set<Object> keys = new HashSet<>();
        for (J.Annotation existing : cd.getLeadingAnnotations()) {
            if (TypeUtils.isOfClassType(existing.getType(), PIONEER_SET) ||
                TypeUtils.isOfClassType(existing.getType(), PIONEER_CLEAR)) {
                keys.add(keyOf(existing));
            }
        }
        for (PioneerAnnotation annotation : annotations) {
            if (annotation.key != null && !keys.add(annotation.key)) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable Object keyOf(J.Annotation annotation) {
        if (annotation.getArguments() != null) {
            for (Expression argument : annotation.getArguments()) {
                if (argument instanceof J.Assignment &&
                    "key".equals(((J.Assignment) argument).getVariable().toString()) &&
                    ((J.Assignment) argument).getAssignment() instanceof J.Literal) {
                    return ((J.Literal) ((J.Assignment) argument).getAssignment()).getValue();
                }
            }
        }
        return annotation;
    }

    private static boolean hasAnnotation(J.ClassDeclaration cd, String fqn) {
        return cd.getLeadingAnnotations().stream().anyMatch(a -> TypeUtils.isOfClassType(a.getType(), fqn));
    }

    @Value
    private static class PioneerAnnotation {
        String fqn;
        String template;
        @Nullable
        Object key;

        static PioneerAnnotation set(J.Literal key, J.Literal value) {
            return new PioneerAnnotation(PIONEER_SET,
                    "@SetSystemProperty(key = " + key.getValueSource() + ", value = " + value.getValueSource() + ")", key.getValue());
        }

        static PioneerAnnotation clear(J.Literal key) {
            return new PioneerAnnotation(PIONEER_CLEAR, "@ClearSystemProperty(key = " + key.getValueSource() + ")", key.getValue());
        }

        static PioneerAnnotation restore() {
            return new PioneerAnnotation(PIONEER_RESTORE, "@RestoreSystemProperties", null);
        }
    }
}
