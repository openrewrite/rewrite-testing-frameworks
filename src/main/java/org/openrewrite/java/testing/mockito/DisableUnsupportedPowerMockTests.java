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

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.marker.Markers;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.java.testing.mockito.table.PowerMockTestsDisabled;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

public class DisableUnsupportedPowerMockTests extends Recipe {

    private static final String JUNIT4_TEST = "org.junit.Test";
    private static final String JUNIT4_IGNORE = "org.junit.Ignore";
    private static final String JUNIT5_TEST = "org.junit.jupiter.api.Test";
    private static final String JUNIT5_DISABLED = "org.junit.jupiter.api.Disabled";
    private static final String TESTNG_TEST = "org.testng.annotations.Test";
    private static final String TESTNG_IGNORE = "org.testng.annotations.Ignore";

    private transient final PowerMockTestsDisabled disabledTests = new PowerMockTestsDisabled(this);

    @Getter
    final String displayName = "Disable tests using PowerMock features with no Mockito equivalent";

    @Getter
    final String description = "Disables tests that reach into private members through PowerMock, which Mockito " +
            "deliberately does not support, so that the rest of the repository can migrate. The test is annotated " +
            "`@Disabled`, `@Ignore` or TestNG's `@Ignore` and recorded in a data table as an action item: rework the " +
            "test not to depend on private members, then re-enable it. A usage outside a test method, such as in a " +
            "setup method or a class-level annotation, disables the whole class.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.powermock..*", false), new JavaIsoVisitor<ExecutionContext>() {
            private Map<UUID, String> unsupported = emptyMap();
            private String sourcePath = "";

            @Override
            public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof JavaSourceFile) {
                    unsupported = UnsupportedPowerMockUsage.find((JavaSourceFile) tree, ctx);
                    if (unsupported.isEmpty()) {
                        return (J) tree;
                    }
                    sourcePath = ((JavaSourceFile) tree).getSourcePath().toString();
                }
                return super.visit(tree, ctx);
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                // Usages left over after the methods have been visited sit outside any test method -- in a setup
                // method, a field, or a class-level annotation -- so the class as a whole cannot run.
                Map<UUID, String> remaining = reasonsIn(cd);
                if (remaining.isEmpty() || isDisabled(cd.getLeadingAnnotations())) {
                    return cd;
                }
                String reason = String.join("; ", new LinkedHashMap<>(remaining).values());
                Framework framework = frameworkOf(cd);
                if (framework == null) {
                    return cd;
                }
                disabledTests.insertRow(ctx, new PowerMockTestsDisabled.Row(
                        sourcePath, cd.getSimpleName(), cd.getSimpleName(), "CLASS", reason));
                return commentOutBodiesHoldingUsage(disable(cd, framework, ctx));
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                Framework framework = testFrameworkOf(method);
                Map<UUID, String> reasons = reasonsIn(method);
                if (framework == null || reasons.isEmpty()) {
                    return method;
                }
                // Consumed either way, so that a usage inside a test method never disables the whole class.
                for (UUID id : reasons.keySet()) {
                    unsupported.remove(id);
                }
                if (isDisabled(method.getLeadingAnnotations())) {
                    return method;
                }
                String reason = String.join("; ", new LinkedHashMap<>(reasons).values());
                J.ClassDeclaration enclosing = getCursor().firstEnclosing(J.ClassDeclaration.class);
                disabledTests.insertRow(ctx, new PowerMockTestsDisabled.Row(sourcePath,
                        enclosing == null ? "" : enclosing.getSimpleName(), method.getSimpleName(), "METHOD", reason));
                return commentOutBody(disable(method, framework, ctx));
            }

            private <T extends J> T disable(T target, Framework framework, ExecutionContext ctx) {
                maybeAddImport(framework.disabledAnnotation);
                return JavaTemplate.builder("@" + simpleName(framework.disabledAnnotation) +
                                "(\"PowerMock test disabled by migration: rework it not to rely on private members\")")
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, framework.classpathResource))
                        .imports(framework.disabledAnnotation)
                        .build()
                        .apply(updateCursor(target), annotationCoordinates(target));
            }

            /**
             * Keeps the body as line comments rather than deleting it: the test cannot run, but whoever reworks
             * it still needs to see what it did, and with no PowerMock left in code the dependency can go.
             */
            private J.MethodDeclaration commentOutBody(J.MethodDeclaration method) {
                J.Block body = method.getBody();
                if (body == null || body.getStatements().isEmpty()) {
                    return method;
                }
                String closingIndent = lastLineOf(body.getEnd().getWhitespace());
                String indent = closingIndent + "    ";
                List<Comment> comments = new ArrayList<>(body.getEnd().getComments());
                comments.add(new TextComment(false, " The body of this test is kept for reference while it is migrated by hand:",
                        "\n" + indent, Markers.EMPTY));
                // Printing the block rather than its statements keeps the original punctuation and layout.
                for (String line : dedent(innerSourceOf(body))) {
                    comments.add(new TextComment(false, " " + line, "\n" + indent, Markers.EMPTY));
                }
                // A comment's suffix precedes whatever follows it, so the last one carries the closing brace
                // back out to the method's own indentation.
                comments.set(comments.size() - 1,
                        ((TextComment) comments.get(comments.size() - 1)).withSuffix("\n" + closingIndent));
                return method.withBody(body
                        .withStatements(emptyList())
                        .withEnd(body.getEnd().withWhitespace("\n" + indent).withComments(comments)));
            }

            private String innerSourceOf(J.Block body) {
                String printed = body.printTrimmed(getCursor());
                int open = printed.indexOf('{');
                int close = printed.lastIndexOf('}');
                return open < 0 || close <= open ? printed : printed.substring(open + 1, close);
            }

            /** Drops the common leading whitespace so the comment does not carry the original nesting twice. */
            private List<String> dedent(String source) {
                List<String> lines = new ArrayList<>();
                int common = Integer.MAX_VALUE;
                for (String line : source.split("\n")) {
                    if (!line.trim().isEmpty()) {
                        common = Math.min(common, line.length() - line.replaceAll("^\\s+", "").length());
                    }
                }
                for (String line : source.split("\n")) {
                    if (!line.trim().isEmpty()) {
                        lines.add(line.substring(Math.min(common, line.length())));
                    }
                }
                return lines;
            }

            private String lastLineOf(String whitespace) {
                int lineStart = whitespace.lastIndexOf('\n');
                return lineStart < 0 ? "" : whitespace.substring(lineStart + 1);
            }

            private J.ClassDeclaration commentOutBodiesHoldingUsage(J.ClassDeclaration classDecl) {
                return classDecl.withBody(classDecl.getBody().withStatements(
                        ListUtils.map(classDecl.getBody().getStatements(), statement ->
                                statement instanceof J.MethodDeclaration && !reasonsIn(statement).isEmpty() ?
                                        commentOutBody((J.MethodDeclaration) statement) : statement)));
            }

            private org.openrewrite.java.tree.@Nullable JavaCoordinates annotationCoordinates(J target) {
                if (target instanceof J.MethodDeclaration) {
                    return ((J.MethodDeclaration) target).getCoordinates().addAnnotation((a, b) -> 0);
                }
                return ((J.ClassDeclaration) target).getCoordinates().addAnnotation((a, b) -> 0);
            }

            /** The unsupported usages inside this element, keyed by node so they can be consumed once. */
            private Map<UUID, String> reasonsIn(J element) {
                Map<UUID, String> found = new LinkedHashMap<>();
                new JavaIsoVisitor<Map<UUID, String>>() {
                    @Override
                    public @Nullable J preVisit(J tree, Map<UUID, String> p) {
                        String reason = unsupported.get(tree.getId());
                        if (reason != null) {
                            p.put(tree.getId(), reason);
                        }
                        return tree;
                    }
                }.visit(element, found);
                return found;
            }

            private boolean isDisabled(java.util.List<J.Annotation> annotations) {
                for (J.Annotation annotation : annotations) {
                    if (TypeUtils.isOfClassType(annotation.getType(), JUNIT4_IGNORE) ||
                            TypeUtils.isOfClassType(annotation.getType(), JUNIT5_DISABLED) ||
                            TypeUtils.isOfClassType(annotation.getType(), TESTNG_IGNORE)) {
                        return true;
                    }
                    // An annotation this recipe has just added is not attributed yet, so recognise it by name
                    // as well; otherwise a second cycle would add it again.
                    String name = annotation.getSimpleName();
                    if ("Ignore".equals(name) || "Disabled".equals(name)) {
                        return true;
                    }
                }
                return false;
            }

            private @Nullable Framework testFrameworkOf(J.MethodDeclaration method) {
                for (J.Annotation annotation : method.getLeadingAnnotations()) {
                    if (TypeUtils.isOfClassType(annotation.getType(), JUNIT5_TEST)) {
                        return Framework.JUNIT5;
                    }
                    if (TypeUtils.isOfClassType(annotation.getType(), JUNIT4_TEST)) {
                        return Framework.JUNIT4;
                    }
                    if (TypeUtils.isOfClassType(annotation.getType(), TESTNG_TEST)) {
                        return Framework.TESTNG;
                    }
                }
                return null;
            }

            /** The framework the class tests with, taken from whichever test method it declares. */
            private @Nullable Framework frameworkOf(J.ClassDeclaration classDecl) {
                for (org.openrewrite.java.tree.Statement statement : classDecl.getBody().getStatements()) {
                    if (statement instanceof J.MethodDeclaration) {
                        Framework framework = testFrameworkOf((J.MethodDeclaration) statement);
                        if (framework != null) {
                            return framework;
                        }
                    }
                }
                return null;
            }
        });
    }

    private static String simpleName(String fullyQualified) {
        return fullyQualified.substring(fullyQualified.lastIndexOf('.') + 1);
    }

    private enum Framework {
        JUNIT4(JUNIT4_IGNORE, "junit-4"),
        JUNIT5(JUNIT5_DISABLED, "junit-jupiter-api-5"),
        TESTNG(TESTNG_IGNORE, "testng-7");

        final String disabledAnnotation;
        final String classpathResource;

        Framework(String disabledAnnotation, String classpathResource) {
            this.disabledAnnotation = disabledAnnotation;
            this.classpathResource = classpathResource;
        }
    }
}
