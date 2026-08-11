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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.marker.JavaVersion;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.staticanalysis.kotlin.KotlinFileChecker;

import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singleton;

@Value
@EqualsAndHashCode(callSuper = false)
public class AddMissingNested extends Recipe {
    private static final String NESTED = "org.junit.jupiter.api.Nested";
    private static final List<String> TEST_ANNOTATIONS = Arrays.asList(
            "org.junit.jupiter.api.Test",
            "org.junit.jupiter.api.TestTemplate",
            "org.junit.jupiter.api.RepeatedTest",
            "org.junit.jupiter.params.ParameterizedTest",
            "org.junit.jupiter.api.TestFactory");

    @SuppressWarnings("unchecked")
    private static final TreeVisitor<?, ExecutionContext> PRECONDITION =
            Preconditions.or(TEST_ANNOTATIONS.stream().map(r -> new UsesType<>(r, false)).toArray(UsesType[]::new));

    String displayName = "JUnit 5 inner test classes should be annotated with `@Nested`";

    String description = "Adds `@Nested` to inner classes that contain JUnit 5 tests and removes `static` from them. " +
            "Before Java 16 an inner class may not declare static members other than constant variables, so a static " +
            "nested class that declares any other static member is marked as needing manual migration instead; " +
            "sources without a known Java version are assumed to support static members.";

    Duration estimatedEffortPerOccurrence = Duration.ofMinutes(1);

    Set<String> tags = singleton("RSPEC-S5790");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.and(PRECONDITION, Preconditions.not(new KotlinFileChecker<>())), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                cd = cd.withBody((J.Block) new AddNestedAnnotationVisitor().visitNonNull(cd.getBody(), ctx, updateCursor(cd)));
                maybeAddImport(NESTED);
                return cd;
            }
        });
    }

    public static class AddNestedAnnotationVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final int STATIC_MEMBERS_IN_INNER_CLASSES = 16;

        private static final String REQUIRES_MANUAL_MIGRATION = "Not converted to `@Nested`: this class declares " +
                "static members that may not be legal in an inner class before Java 16; tests in this class may not " +
                "run and require manual migration";

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
            if (cd.hasModifier(J.Modifier.Type.Abstract) ||
                    cd.getKind() == J.ClassDeclaration.Kind.Type.Annotation ||
                    classDecl.getLeadingAnnotations().stream()
                            .anyMatch(a -> TypeUtils.isOfClassType(a.getType(), NESTED)) ||
                    !hasTestMethods(cd)) {
                return cd;
            }
            if (cd.hasModifier(J.Modifier.Type.Static) && !canBeInnerClass(cd)) {
                // Skipping silently can end test discovery unnoticed, as `EnclosedToNested` already removed the runner
                return alreadyMarked(cd) ? cd : SearchResult.found(cd, REQUIRES_MANUAL_MIGRATION);
            }
            cd = JavaTemplate.builder("@Nested")
                    .javaParser(JavaParser.fromJavaVersion()
                            .classpathFromResources(ctx, "junit-jupiter-api-5"))
                    .imports(NESTED)
                    .build()
                    .apply(getCursor(), cd.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
            cd.getModifiers().removeIf(modifier -> modifier.getType() == J.Modifier.Type.Static);
            return maybeAutoFormat(classDecl, cd, ctx);
        }

        private static boolean hasTestMethods(final J.ClassDeclaration cd) {
            return TEST_ANNOTATIONS.stream().anyMatch(ann -> !FindAnnotations.find(cd, "@" + ann).isEmpty());
        }

        private static boolean alreadyMarked(J.ClassDeclaration cd) {
            return cd.getPrefix().getComments().stream()
                    .anyMatch(comment -> comment instanceof TextComment &&
                            ((TextComment) comment).getText().contains(REQUIRES_MANUAL_MIGRATION));
        }

        private boolean canBeInnerClass(J.ClassDeclaration cd) {
            return !declaresStaticMember(cd) || supportsStaticMembersInInnerClasses();
        }

        /**
         * @return whether static members other than constant variables are allowed in inner classes. An unknown
         * version is assumed to support them, as guessing wrong there fails at compile time, whereas withholding
         * the conversion can silently end test discovery.
         */
        private boolean supportsStaticMembersInInnerClasses() {
            JavaSourceFile sourceFile = getCursor().firstEnclosing(JavaSourceFile.class);
            if (sourceFile == null) {
                return true;
            }
            return sourceFile.getMarkers()
                    .findFirst(JavaVersion.class)
                    .map(javaVersion -> javaVersion.getMajorVersion() < 0 ||
                            STATIC_MEMBERS_IN_INNER_CLASSES <= javaVersion.getMajorVersion())
                    .orElse(true);
        }

        /**
         * @return whether the class declares a member that is not legal in an inner class before Java 16; member
         * interfaces, enums, annotation types and records are implicitly static.
         */
        private static boolean declaresStaticMember(J.ClassDeclaration cd) {
            for (Statement statement : cd.getBody().getStatements()) {
                if (statement instanceof J.Block) {
                    if (((J.Block) statement).isStatic()) {
                        return true;
                    }
                } else if (statement instanceof J.MethodDeclaration) {
                    if (((J.MethodDeclaration) statement).hasModifier(J.Modifier.Type.Static)) {
                        return true;
                    }
                } else if (statement instanceof J.VariableDeclarations) {
                    J.VariableDeclarations variables = (J.VariableDeclarations) statement;
                    if (variables.hasModifier(J.Modifier.Type.Static) && !isConstantVariable(variables)) {
                        return true;
                    }
                } else if (statement instanceof J.ClassDeclaration) {
                    J.ClassDeclaration member = (J.ClassDeclaration) statement;
                    if (member.hasModifier(J.Modifier.Type.Static) ||
                            member.getKind() != J.ClassDeclaration.Kind.Type.Class) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * @return whether these are constant variables per JLS 4.12.4. Deliberately conservative: only
         * literal-based initializers are recognised, so anything else counts as nonconstant.
         */
        private static boolean isConstantVariable(J.VariableDeclarations variables) {
            if (!variables.hasModifier(J.Modifier.Type.Final)) {
                return false;
            }
            for (J.VariableDeclarations.NamedVariable variable : variables.getVariables()) {
                if (!isPrimitiveOrString(variable.getType()) || !isConstantExpression(variable.getInitializer())) {
                    return false;
                }
            }
            return true;
        }

        private static boolean isConstantExpression(@Nullable Expression expression) {
            if (expression instanceof J.Literal) {
                return isPrimitiveOrString(((J.Literal) expression).getType());
            }
            if (expression instanceof J.Parentheses) {
                J tree = ((J.Parentheses<?>) expression).getTree();
                return tree instanceof Expression && isConstantExpression((Expression) tree);
            }
            if (expression instanceof J.Unary) {
                return isConstantExpression(((J.Unary) expression).getExpression());
            }
            if (expression instanceof J.Binary) {
                return isConstantExpression(((J.Binary) expression).getLeft()) &&
                        isConstantExpression(((J.Binary) expression).getRight());
            }
            return false;
        }

        private static boolean isPrimitiveOrString(@Nullable JavaType type) {
            return TypeUtils.isString(type) ||
                    type instanceof JavaType.Primitive &&
                            type != JavaType.Primitive.None &&
                            type != JavaType.Primitive.Null &&
                            type != JavaType.Primitive.Void;
        }
    }
}
