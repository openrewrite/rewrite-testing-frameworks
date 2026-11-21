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

import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.*;
import org.openrewrite.trait.Comments;

import java.nio.file.Path;
import java.util.*;

import static java.util.Collections.emptyMap;

final class SystemRules {
    static final String PACKAGE = "org.junit.contrib.java.lang.system";
    static final String RULE = "org.junit.Rule";
    static final String CLASS_RULE = "org.junit.ClassRule";
    static final String MAY_BE_USED_ELSEWHERE_COMMENT = " TODO Migrate by hand: other classes, such as subclasses, may use this rule, which this migration does not see.";

    private static final String TEST_RULE = "org.junit.rules.TestRule";
    private static final AnnotationMatcher RULE_ANNOTATION = new AnnotationMatcher("@org.junit.*Rule");

    private SystemRules() {
    }

    static boolean isRuleField(J.VariableDeclarations field) {
        return field.getVariables().size() == 1 &&
               field.getLeadingAnnotations().stream().anyMatch(RULE_ANNOTATION::matches);
    }

    /**
     * Records, for each rule-typed field, the source files that refer to it, so that a rule that a subclass in another
     * file reads can be told apart from one that only its own file uses.
     */
    static void scanFieldUsages(JavaSourceFile sourceFile, Map<String, Set<Path>> usages) {
        new JavaIsoVisitor<Map<String, Set<Path>>>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, Map<String, Set<Path>> acc) {
                JavaType.Variable variable = identifier.getFieldType();
                if (variable != null && isRuleTypedField(variable)) {
                    acc.computeIfAbsent(fieldKey(variable), k -> new HashSet<>()).add(sourceFile.getSourcePath());
                }
                return identifier;
            }
        }.visit(sourceFile, usages);
    }

    private static boolean isRuleTypedField(JavaType.Variable variable) {
        return variable.getOwner() instanceof JavaType.FullyQualified &&
               TypeUtils.isAssignableTo(TEST_RULE, variable.getType());
    }

    private static String fieldKey(JavaType.Variable variable) {
        JavaType.FullyQualified owner = TypeUtils.asFullyQualified(variable.getOwner());
        return (owner == null ? "" : owner.getFullyQualifiedName()) + "#" + variable.getName();
    }

    static boolean mayBeUsedElsewhere(J.VariableDeclarations field, Cursor cursor) {
        return mayBeUsedElsewhere(field, cursor, emptyMap());
    }

    static boolean mayBeUsedElsewhere(J.VariableDeclarations field, Cursor cursor, Map<String, Set<Path>> usages) {
        if (field.hasModifier(J.Modifier.Type.Private)) {
            return false;
        }
        JavaSourceFile sourceFile = cursor.firstEnclosing(JavaSourceFile.class);
        JavaType.Variable variable = field.getVariables().get(0).getVariableType();
        if (variable != null && sourceFile != null) {
            Set<Path> paths = usages.get(fieldKey(variable));
            if (paths != null && paths.stream().anyMatch(path -> !path.equals(sourceFile.getSourcePath()))) {
                return true;
            }
        }
        J.ClassDeclaration owner = cursor.firstEnclosing(J.ClassDeclaration.class);
        if (owner != null && owner.hasModifier(J.Modifier.Type.Abstract)) {
            return true;
        }
        return sourceFile != null && sourceFile.getMarkers().findFirst(JavaSourceSet.class)
                .map(sourceSet -> "main".equals(sourceSet.getName()))
                .orElse(false);
    }

    // Also covers rules declared as a supertype, as in `TestRule restore = new RestoreSystemProperties()`
    static @Nullable String fieldTypeName(J.VariableDeclarations field) {
        JavaType.FullyQualified declared = TypeUtils.asFullyQualified(field.getType());
        if (declared != null && declared.getFullyQualifiedName().startsWith(PACKAGE + ".")) {
            return declared.getFullyQualifiedName();
        }
        Expression initializer = field.getVariables().get(0).getInitializer();
        JavaType.FullyQualified initialized = initializer == null ? null : TypeUtils.asFullyQualified(initializer.getType());
        if (initialized != null && initialized.getFullyQualifiedName().startsWith(PACKAGE + ".")) {
            return initialized.getFullyQualifiedName();
        }
        return declared == null ? null : declared.getFullyQualifiedName();
    }

    static @Nullable String declaredSupertype(J.VariableDeclarations field) {
        JavaType.FullyQualified declared = TypeUtils.asFullyQualified(field.getType());
        return declared == null || declared.getFullyQualifiedName().startsWith(PACKAGE + ".") ?
                null : declared.getFullyQualifiedName();
    }

    static JavaType.@Nullable Variable referencedField(@Nullable Expression expression) {
        if (expression instanceof J.Identifier) {
            return ((J.Identifier) expression).getFieldType();
        }
        if (expression instanceof J.FieldAccess) {
            return ((J.FieldAccess) expression).getName().getFieldType();
        }
        return null;
    }

    static boolean isStringLiteral(Expression expression) {
        return expression instanceof J.Literal && ((J.Literal) expression).getValue() instanceof String;
    }

    static List<Expression> arguments(J.MethodInvocation method) {
        return ListUtils.filter(method.getArguments(), argument -> !(argument instanceof J.Empty));
    }

    static List<Expression> arguments(J.NewClass newClass) {
        return ListUtils.filter(newClass.getArguments(), argument -> !(argument instanceof J.Empty));
    }

    static List<Cursor> references(J tree, Cursor parent, JavaType.Variable field) {
        return new JavaIsoVisitor<List<Cursor>>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, List<Cursor> references) {
                if (TypeUtils.isOfType(field, identifier.getFieldType())) {
                    Cursor parentCursor = getCursor().getParentTreeCursor();
                    Object parentTree = parentCursor.getValue();
                    if (parentTree instanceof J.VariableDeclarations.NamedVariable &&
                        ((J.VariableDeclarations.NamedVariable) parentTree).getName() == identifier) {
                        return identifier;
                    }
                    if (parentTree instanceof J.FieldAccess && ((J.FieldAccess) parentTree).getName() == identifier) {
                        references.add(parentCursor);
                    } else {
                        references.add(getCursor());
                    }
                }
                return identifier;
            }
        }.reduce(tree, new ArrayList<>(), parent);
    }

    static J.@Nullable MethodInvocation invokedOn(Cursor reference) {
        Object parent = reference.getParentTreeCursor().getValue();
        if (parent instanceof J.MethodInvocation && ((J.MethodInvocation) parent).getSelect() == reference.getValue()) {
            return (J.MethodInvocation) parent;
        }
        return null;
    }

    static boolean isStatement(Cursor invocation) {
        return invocation.getParentTreeCursor().getValue() instanceof J.Block;
    }

    static J.VariableDeclarations withTodo(Cursor field, String todo) {
        Space prefix = field.<J.VariableDeclarations>getValue().getPrefix();
        String whitespace = prefix.getComments().isEmpty() ? prefix.getWhitespace() :
                prefix.getComments().get(prefix.getComments().size() - 1).getSuffix();
        String indent = whitespace.substring(Math.max(0, whitespace.lastIndexOf('\n')));
        return Comments.of(field).comment(todo, Comments.Placement.BEFORE, indent);
    }
}
