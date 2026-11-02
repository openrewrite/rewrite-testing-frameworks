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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.testing.mockito.table.PowerMockTestsDisabled;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.openrewrite.Tree.randomId;

public class DisableUnsupportedPowerMockTests extends Recipe {

    private static final String JUNIT4_TEST = "org.junit.Test";
    private static final String JUNIT4_IGNORE = "org.junit.Ignore";
    private static final String JUNIT5_TEST = "org.junit.jupiter.api.Test";
    private static final String JUNIT5_DISABLED = "org.junit.jupiter.api.Disabled";
    private static final String TESTNG_TEST = "org.testng.annotations.Test";
    private static final String TESTNG_IGNORE = "org.testng.annotations.Ignore";

    private static final List<String> JUNIT4_TESTS = Arrays.asList(JUNIT4_TEST, "org.junit.experimental.theories.Theory");
    private static final List<String> JUNIT5_TESTS = Arrays.asList(JUNIT5_TEST,
            "org.junit.jupiter.params.ParameterizedTest",
            "org.junit.jupiter.api.RepeatedTest",
            "org.junit.jupiter.api.TestFactory",
            "org.junit.jupiter.api.TestTemplate");

    private transient final PowerMockTestsDisabled disabledTests = new PowerMockTestsDisabled(this);

    @Getter
    final String displayName = "Disable tests using PowerMock features with no Mockito equivalent";

    @Getter
    final String description = "Disables tests that reach into private members through PowerMock, which Mockito " +
            "deliberately does not support, so that the rest of the repository can migrate. The test is annotated " +
            "`@Disabled`, `@Ignore` or TestNG's `@Ignore`, or `@Test(enabled = false)` on TestNG before 6.13, which " +
            "has no `@Ignore`, and recorded in a data table as an action item: rework the " +
            "test not to depend on private members, then re-enable it. A usage outside a test method, such as in a " +
            "setup method or a class-level annotation, disables the whole class, and an initializer holding it is " +
            "commented out and an annotation holding it removed, so that the class compiles without PowerMock.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.powermock..*", false), new JavaIsoVisitor<ExecutionContext>() {
            private Map<UUID, String> unsupported = emptyMap();
            private String sourcePath = "";
            private boolean testNgIgnoreAvailable = true;

            @Override
            public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof JavaSourceFile) {
                    unsupported = UnsupportedPowerMockUsage.find((JavaSourceFile) tree, ctx);
                    if (unsupported.isEmpty()) {
                        return (J) tree;
                    }
                    sourcePath = ((JavaSourceFile) tree).getSourcePath().toString();
                    testNgIgnoreAvailable = isTestNgIgnoreAvailable((JavaSourceFile) tree);
                }
                return super.visit(tree, ctx);
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                // Usages left over after the methods have been visited sit outside any test method -- in a setup
                // method, a field, or a class-level annotation -- so the class as a whole cannot run.
                Map<UUID, String> remaining = reasonsIn(cd);
                if (remaining.isEmpty()) {
                    return cd;
                }
                if (isDisabled(cd.getLeadingAnnotations())) {
                    // Already disabled, so nothing to annotate or record, but the PowerMock dependency is removed
                    return commentOutBodiesHoldingUsage(cd);
                }
                String reason = String.join("; ", new LinkedHashMap<>(remaining).values());
                disabledTests.insertRow(ctx, new PowerMockTestsDisabled.Row(
                        sourcePath, cd.getSimpleName(), cd.getSimpleName(), "CLASS", reason));
                Framework framework = frameworkOf(cd);
                // A class that declares no test, such as an abstract base class with a setup method, has no
                // framework to take a disabling annotation from. Its usage is still commented out, as the
                // PowerMock dependency is removed from the repository regardless.
                return commentOutBodiesHoldingUsage(framework == null ? cd : disable(cd, framework, ctx));
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
                    // Already disabled, so nothing to annotate or record, but the PowerMock dependency is removed
                    return commentOutBody(method, getCursor().getParentOrThrow());
                }
                String reason = String.join("; ", new LinkedHashMap<>(reasons).values());
                J.ClassDeclaration enclosing = getCursor().firstEnclosing(J.ClassDeclaration.class);
                disabledTests.insertRow(ctx, new PowerMockTestsDisabled.Row(sourcePath,
                        enclosing == null ? "" : enclosing.getSimpleName(), method.getSimpleName(), "METHOD", reason));
                return commentOutBody(disable(method, framework, ctx), getCursor().getParentOrThrow());
            }

            private <T extends J> T disable(T target, Framework framework, ExecutionContext ctx) {
                if (framework == Framework.TESTNG && !testNgIgnoreAvailable) {
                    return disableWithoutTestNgIgnore(target);
                }
                maybeAddImport(framework.disabledAnnotation);
                return JavaTemplate.builder("@" + simpleName(framework.disabledAnnotation) +
                                "(\"PowerMock test disabled by migration: rework it not to rely on private members\")")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, framework.classpathResource))
                        .imports(framework.disabledAnnotation)
                        .build()
                        .apply(updateCursor(target), annotationCoordinates(target));
            }

            /**
             * Keeps the body as line comments rather than deleting it: the test cannot run, but whoever reworks
             * it still needs to see what it did, and with no PowerMock left in code the dependency can go.
             */
            private J.MethodDeclaration commentOutBody(J.MethodDeclaration method, Cursor parent) {
                J.Block body = method.getBody();
                if (body == null || body.getStatements().isEmpty()) {
                    return method;
                }
                String closingIndent = lastLineOf(body.getEnd().getWhitespace());
                String indent = closingIndent + "    ";
                List<Comment> comments = commentedOutStatements(body, indent);
                TextComment last = (TextComment) comments.get(comments.size() - 1);
                J.MethodDeclaration emptied = method.withBody(body.withStatements(emptyList()));
                TypeTree returnType = method.getReturnTypeExpression();
                if (returnType == null || returnType.getType() == JavaType.Primitive.Void) {
                    return emptied.withBody(commentOut(body));
                }
                // A method that returns a value no longer compiles without a body, so it throws instead
                comments.set(comments.size() - 1, last.withSuffix("\n" + indent));
                J.MethodDeclaration throwing = JavaTemplate.apply(
                        "throw new UnsupportedOperationException(\"Disabled by the PowerMock migration\");",
                        new Cursor(parent, emptied), body.getCoordinates().lastStatement());
                J.Block throwingBody = throwing.getBody();
                return throwing.withBody(throwingBody
                        .withStatements(ListUtils.mapFirst(throwingBody.getStatements(),
                                statement -> statement.withPrefix(Space.build("\n" + indent, comments))))
                        .withEnd(Space.format("\n" + closingIndent)));
            }

            /** Empties the block, keeping what it did as line comments in its place. */
            private J.Block commentOut(J.Block block) {
                String closingIndent = lastLineOf(block.getEnd().getWhitespace());
                String indent = closingIndent + "    ";
                List<Comment> comments = commentedOutStatements(block, indent);
                // A comment's suffix precedes whatever follows it, so the last one carries the closing brace
                // back out to the block's own indentation.
                TextComment last = (TextComment) comments.get(comments.size() - 1);
                comments.set(comments.size() - 1, last.withSuffix("\n" + closingIndent));
                return block.withStatements(emptyList())
                        .withEnd(block.getEnd().withWhitespace("\n" + indent).withComments(comments));
            }

            private List<Comment> commentedOutStatements(J.Block block, String indent) {
                List<Comment> comments = new ArrayList<>(block.getEnd().getComments());
                comments.add(new TextComment(false, " The body of this test is kept for reference while it is migrated by hand:",
                        "\n" + indent, Markers.EMPTY));
                // Printing the block rather than its statements keeps the original punctuation and layout.
                for (String line : dedent(innerSourceOf(block))) {
                    comments.add(new TextComment(false, " " + line, "\n" + indent, Markers.EMPTY));
                }
                return comments;
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
                    if (!StringUtils.isBlank(line)) {
                        common = Math.min(common, line.length() - line.replaceAll("^\\s+", "").length());
                    }
                }
                for (String line : source.split("\n")) {
                    if (!StringUtils.isBlank(line)) {
                        lines.add(line.substring(Math.min(common, line.length())));
                    }
                }
                return lines;
            }

            private String lastLineOf(String whitespace) {
                int lineStart = whitespace.lastIndexOf('\n');
                return lineStart < 0 ? "" : whitespace.substring(lineStart + 1);
            }

            /**
             * Also comments out an initializer holding usage, and removes an annotation of the class holding usage,
             * such as a PowerMock runner that is left in place: the class does not run anyway, and either would keep
             * it from compiling once the PowerMock dependency is removed.
             */
            private J.ClassDeclaration commentOutBodiesHoldingUsage(J.ClassDeclaration classDecl) {
                Cursor bodyCursor = new Cursor(getCursor(), classDecl.getBody());
                J.ClassDeclaration cd = classDecl.withBody(classDecl.getBody().withStatements(
                        ListUtils.map(classDecl.getBody().getStatements(), statement -> {
                            if (reasonsIn(statement).isEmpty()) {
                                return statement;
                            }
                            if (statement instanceof J.MethodDeclaration) {
                                return commentOutBody((J.MethodDeclaration) statement, bodyCursor);
                            }
                            if (statement instanceof J.Block && !((J.Block) statement).getStatements().isEmpty()) {
                                return commentOut((J.Block) statement);
                            }
                            return statement;
                        })));
                List<J.Annotation> annotations = cd.getLeadingAnnotations();
                List<J.Annotation> kept = ListUtils.map(annotations, annotation -> reasonsIn(annotation).isEmpty() ? annotation : null);
                if (kept.size() == annotations.size()) {
                    return cd;
                }
                Space prefix = annotations.get(0).getPrefix();
                if (!kept.isEmpty()) {
                    return cd.withLeadingAnnotations(ListUtils.mapFirst(kept, first -> first.withPrefix(prefix)));
                }
                // A class without a test framework gets no disabling annotation, so it may be left with none at all
                cd = cd.withLeadingAnnotations(emptyList());
                if (!cd.getModifiers().isEmpty()) {
                    return cd.withModifiers(ListUtils.mapFirst(cd.getModifiers(), first -> first.withPrefix(prefix)));
                }
                return cd.getPadding().withKind(cd.getPadding().getKind().withPrefix(prefix));
            }

            /**
             * TestNG only has `@Ignore` since 6.13, so before that a test is disabled with `@Test(enabled = false)`.
             * TestNG does not apply a class-level `enabled = false` to methods with a `@Test` of their own, so for a
             * class every such method is disabled.
             */
            @SuppressWarnings("unchecked")
            private <T extends J> T disableWithoutTestNgIgnore(T target) {
                if (target instanceof J.MethodDeclaration) {
                    J.MethodDeclaration method = (J.MethodDeclaration) target;
                    return (T) method.withLeadingAnnotations(ListUtils.map(method.getLeadingAnnotations(), this::withEnabledFalse));
                }
                J.ClassDeclaration cd = (J.ClassDeclaration) target;
                cd = cd.withLeadingAnnotations(ListUtils.map(cd.getLeadingAnnotations(), this::withEnabledFalse));
                return (T) cd.withBody(cd.getBody().withStatements(ListUtils.map(cd.getBody().getStatements(), statement ->
                        statement instanceof J.MethodDeclaration ? disableWithoutTestNgIgnore(statement) : statement)));
            }

            private J.Annotation withEnabledFalse(J.Annotation annotation) {
                if (!TypeUtils.isOfClassType(annotation.getType(), TESTNG_TEST)) {
                    return annotation;
                }
                List<Expression> arguments = ListUtils.filter(annotation.getArguments() == null ? emptyList() : annotation.getArguments(),
                        argument -> !(argument instanceof J.Empty) && !isEnabledAttribute(argument));
                J.Assignment enabledFalse = new J.Assignment(randomId(), arguments.isEmpty() ? Space.EMPTY : Space.SINGLE_SPACE,
                        Markers.EMPTY,
                        new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), "enabled", JavaType.Primitive.Boolean, null),
                        JLeftPadded.<Expression>build(new J.Literal(randomId(), Space.SINGLE_SPACE, Markers.EMPTY, false, "false",
                                null, JavaType.Primitive.Boolean)).withBefore(Space.SINGLE_SPACE),
                        JavaType.Primitive.Boolean);
                return annotation.withArguments(ListUtils.concat(arguments, enabledFalse));
            }

            private boolean isEnabledAttribute(Expression argument) {
                return argument instanceof J.Assignment && ((J.Assignment) argument).getVariable() instanceof J.Identifier &&
                       "enabled".equals(((J.Identifier) ((J.Assignment) argument).getVariable()).getSimpleName());
            }

            private boolean isTestNgIgnoreAvailable(JavaSourceFile sourceFile) {
                JavaSourceSet sourceSet = sourceFile.getMarkers().findFirst(JavaSourceSet.class).orElse(null);
                if (sourceSet == null) {
                    return true;
                }
                boolean testNgOnClasspath = false;
                for (JavaType.FullyQualified type : sourceSet.getClasspath()) {
                    if (TESTNG_IGNORE.equals(type.getFullyQualifiedName())) {
                        return true;
                    }
                    testNgOnClasspath |= type.getFullyQualifiedName().startsWith("org.testng.");
                }
                // Nothing is known of a classpath that does not list TestNG
                return !testNgOnClasspath;
            }

            private org.openrewrite.java.tree.@Nullable JavaCoordinates annotationCoordinates(J target) {
                if (target instanceof J.MethodDeclaration) {
                    return ((J.MethodDeclaration) target).getCoordinates().addAnnotation((a, b) -> 0);
                }
                return ((J.ClassDeclaration) target).getCoordinates().addAnnotation((a, b) -> 0);
            }

            /** The unsupported usages inside this element, keyed by node so they can be consumed once. */
            private Map<UUID, String> reasonsIn(J element) {
                return new JavaIsoVisitor<Map<UUID, String>>() {
                    @Override
                    public @Nullable J preVisit(J tree, Map<UUID, String> p) {
                        String reason = unsupported.get(tree.getId());
                        if (reason != null) {
                            p.put(tree.getId(), reason);
                        }
                        return tree;
                    }
                }.reduce(element, new LinkedHashMap<>());
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
                return testFrameworkOf(method.getLeadingAnnotations());
            }

            private @Nullable Framework testFrameworkOf(List<J.Annotation> annotations) {
                for (J.Annotation annotation : annotations) {
                    for (String type : JUNIT5_TESTS) {
                        if (TypeUtils.isOfClassType(annotation.getType(), type)) {
                            return Framework.JUNIT5;
                        }
                    }
                    for (String type : JUNIT4_TESTS) {
                        if (TypeUtils.isOfClassType(annotation.getType(), type)) {
                            return Framework.JUNIT4;
                        }
                    }
                    if (TypeUtils.isOfClassType(annotation.getType(), TESTNG_TEST)) {
                        return Framework.TESTNG;
                    }
                }
                return null;
            }

            /** The framework the class tests with, taken from whichever test method it declares. */
            private @Nullable Framework frameworkOf(J.ClassDeclaration classDecl) {
                // TestNG allows `@Test` on the class, which makes every public method a test
                Framework classLevel = testFrameworkOf(classDecl.getLeadingAnnotations());
                if (classLevel != null) {
                    return classLevel;
                }
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
