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
import org.openrewrite.ExecutionContext;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.search.UsesJavaVersion;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.List;

import static org.openrewrite.Preconditions.and;
import static org.openrewrite.Preconditions.not;
import static org.openrewrite.Preconditions.or;

final class SystemRules {
    static final String PACKAGE = "org.junit.contrib.java.lang.system";
    static final String RULE = "org.junit.Rule";
    static final String CLASS_RULE = "org.junit.ClassRule";
    static final String MAY_BE_USED_ELSEWHERE_COMMENT = " TODO Migrate by hand: other classes, such as subclasses, may use this rule, which this migration does not see.";

    private static final AnnotationMatcher RULE_ANNOTATION = new AnnotationMatcher("@org.junit.*Rule");
    private static final String[] JUNIT4_TYPES = {
            "org.junit.Test", "org.junit.Before", "org.junit.After", "org.junit.BeforeClass", "org.junit.AfterClass",
            "org.junit.runner.RunWith"};

    private SystemRules() {
    }

    // System Stubs and JUnit Pioneer 2.x are JUnit Jupiter extensions that require Java 11
    static TreeVisitor<?, ExecutionContext> jupiterClassesUsing(String typePattern) {
        List<TreeVisitor<?, ExecutionContext>> junit4 = new ArrayList<>();
        for (String type : JUNIT4_TYPES) {
            junit4.add(new UsesType<>(type, false));
        }
        return and(
                new UsesType<>(typePattern, false),
                not(or(junit4.toArray(new TreeVisitor[0]))),
                not(new UsesJavaVersion<>(1, 10)));
    }

    static boolean isRuleField(J.VariableDeclarations field) {
        return field.getVariables().size() == 1 &&
               field.getLeadingAnnotations().stream().anyMatch(RULE_ANNOTATION::matches);
    }

    static boolean mayBeUsedElsewhere(J.VariableDeclarations field, Cursor cursor) {
        if (field.hasModifier(J.Modifier.Type.Private)) {
            return false;
        }
        J.ClassDeclaration owner = cursor.firstEnclosing(J.ClassDeclaration.class);
        if (owner != null && owner.hasModifier(J.Modifier.Type.Abstract)) {
            return true;
        }
        JavaSourceFile sourceFile = cursor.firstEnclosing(JavaSourceFile.class);
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
        return withoutEmpty(method.getArguments());
    }

    static List<Expression> arguments(J.NewClass newClass) {
        return withoutEmpty(newClass.getArguments());
    }

    private static List<Expression> withoutEmpty(List<Expression> arguments) {
        List<Expression> result = new ArrayList<>(arguments.size());
        for (Expression argument : arguments) {
            if (!(argument instanceof J.Empty)) {
                result.add(argument);
            }
        }
        return result;
    }

    static List<Cursor> references(J tree, Cursor parent, JavaType.Variable field) {
        List<Cursor> references = new ArrayList<>();
        new JavaIsoVisitor<Integer>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, Integer p) {
                if (field.equals(identifier.getFieldType())) {
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
        }.visit(tree, 0, parent);
        return references;
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

    static J.VariableDeclarations withTodo(J.VariableDeclarations field, String todo) {
        Space prefix = field.getPrefix();
        for (Comment comment : prefix.getComments()) {
            if (comment instanceof TextComment && todo.equals(((TextComment) comment).getText())) {
                return field;
            }
        }
        String whitespace = prefix.getComments().isEmpty() ? prefix.getWhitespace() :
                prefix.getComments().get(prefix.getComments().size() - 1).getSuffix();
        String indent = whitespace.substring(Math.max(0, whitespace.lastIndexOf('\n')));
        return field.withPrefix(prefix.withComments(ListUtils.concat(prefix.getComments(),
                new TextComment(false, todo, indent, Markers.EMPTY))));
    }
}
