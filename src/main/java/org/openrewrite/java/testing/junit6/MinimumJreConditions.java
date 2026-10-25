/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.java.testing.junit6;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.trait.Annotated;
import org.openrewrite.java.trait.Literal;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

@Value
@EqualsAndHashCode(callSuper = false)
public class MinimumJreConditions extends Recipe {

    private static final String JRE_IMPORT = "org.junit.jupiter.api.condition.JRE";
    private static final String ENABLED_ON_JRE = "org.junit.jupiter.api.condition.EnabledOnJre";
    private static final String DISABLED_ON_JRE = "org.junit.jupiter.api.condition.DisabledOnJre";
    private static final String ENABLED_FOR_JRE_RANGE = "org.junit.jupiter.api.condition.EnabledForJreRange";
    private static final String DISABLED_FOR_JRE_RANGE = "org.junit.jupiter.api.condition.DisabledForJreRange";
    private static final Annotated.Matcher ENABLED_JRE_MATCHER = new Annotated.Matcher("@" + ENABLED_ON_JRE);
    private static final Annotated.Matcher DISABLED_JRE_MATCHER = new Annotated.Matcher("@" + DISABLED_ON_JRE);
    private static final Annotated.Matcher ENABLED_JRE_RANGE_MATCHER = new Annotated.Matcher("@" + ENABLED_FOR_JRE_RANGE);
    private static final Annotated.Matcher DISABLED_JRE_RANGE_MATCHER = new Annotated.Matcher("@" + DISABLED_FOR_JRE_RANGE);
    private static final List<Annotated.Matcher> TEST_ANNOTATION_MATCHERS = Arrays.asList(
            new Annotated.Matcher("@org.junit.jupiter.api.Test"),
            new Annotated.Matcher("@org.junit.jupiter.api.TestFactory"),
            new Annotated.Matcher("@org.junit.jupiter.api.TestTemplate"),
            new Annotated.Matcher("@org.junit.jupiter.api.RepeatedTest"),
            new Annotated.Matcher("@org.junit.jupiter.params.ParameterizedTest")
    );

    @Option(displayName = "JRE version", description = "The minimum JRE version to use for test conditions.", example = "17")
    String javaVersion;

    String displayName = "Migrate JUnit JRE conditions";

    String description = "Remove tests that can not run on the specified minimum JRE version or later, and drop JRE versions below that minimum from the `@EnabledOnJre`, `@DisabledOnJre`, `@EnabledForJreRange` and `@DisabledForJreRange` conditions of the remaining tests.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(new UsesType<>(ENABLED_ON_JRE, false), new UsesType<>(DISABLED_ON_JRE, false), new UsesType<>(ENABLED_FOR_JRE_RANGE, false), new UsesType<>(DISABLED_FOR_JRE_RANGE, false)), new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                J.CompilationUnit c = super.visitCompilationUnit(cu, ctx);
                if (cu != c) {
                    maybeRemoveImport("org.junit.jupiter.api.Test");
                    maybeRemoveImport("org.junit.jupiter.api.TestFactory");
                    maybeRemoveImport("org.junit.jupiter.api.TestTemplate");
                    maybeRemoveImport("org.junit.jupiter.api.RepeatedTest");
                    maybeRemoveImport("org.junit.jupiter.params.ParameterizedTest");
                    maybeRemoveImport(ENABLED_ON_JRE);
                    maybeRemoveImport(DISABLED_ON_JRE);
                    maybeRemoveImport(ENABLED_FOR_JRE_RANGE);
                    maybeRemoveImport(DISABLED_FOR_JRE_RANGE);
                }
                return c;
            }

            @Override
            public J.@Nullable MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                boolean isUnitTest = false;
                List<String> enabledOnJre = null;
                List<String> disabledOnJre = null;
                Range enabledOnJreRange = null;
                Range disabledOnJreRange = null;
                Space prefix = Space.EMPTY;

                // First assemble all annotations to see if this is a unit test impacted with JRE conditional annotations
                for (J.Annotation ann : method.getLeadingAnnotations()) {
                    Cursor annotationCursor = new Cursor(getCursor(), ann);
                    if (TEST_ANNOTATION_MATCHERS.stream().anyMatch(matcher -> matcher.get(annotationCursor).isPresent())) {
                        isUnitTest = true;
                    }
                    Optional<Annotated> annotated = ENABLED_JRE_MATCHER.get(annotationCursor);
                    if (annotated.isPresent()) {
                        enabledOnJre = annotated.map(a -> getDefaultAttribute(a).map(this::extractVersionsFromAnnotationArgument)
                                        .orElseGet(() -> a.getAttribute("versions").map(Literal::getStrings).orElse(null)))
                                .orElse(null);
                        prefix = ann.getPrefix();
                    }
                    annotated = DISABLED_JRE_MATCHER.get(annotationCursor);
                    if (annotated.isPresent()) {
                        disabledOnJre = annotated.map(a -> getDefaultAttribute(a).map(this::extractVersionsFromAnnotationArgument)
                                        .orElseGet(() -> a.getAttribute("versions").map(Literal::getStrings).orElse(null)))
                                .orElse(null);
                        prefix = ann.getPrefix();
                    }
                    annotated = ENABLED_JRE_RANGE_MATCHER.get(annotationCursor);
                    if (annotated.isPresent()) {
                        enabledOnJreRange = annotated.map(Range::new).orElse(null);
                        prefix = ann.getPrefix();
                    }
                    annotated = DISABLED_JRE_RANGE_MATCHER.get(annotationCursor);
                    if (annotated.isPresent()) {
                        disabledOnJreRange = annotated.map(Range::new).orElse(null);
                        prefix = ann.getPrefix();
                    }
                }

                // Only act upon unit tests that have JRE conditions
                if (!isUnitTest || (enabledOnJre == null && disabledOnJre == null && enabledOnJreRange == null && disabledOnJreRange == null)) {
                    return m;
                }
                if (!isComparable(enabledOnJre) || !isComparable(disabledOnJre) || !isComparable(enabledOnJreRange) || !isComparable(disabledOnJreRange)) {
                    return m;
                }

                // Now transform the annotations to the correct results
                if (enabledOnJre != null) {
                    if (enabledOnJre.stream().allMatch(v -> compareVersions(v, javaVersion) < 0)) {
                        // Remove the test method if it is enabled on a JRE version lower than the specified version
                        maybeRemoveImport(JRE_IMPORT);
                        return null;
                    }
                    m = updateAnnotationVersions(m, ENABLED_ON_JRE);
                }
                if (disabledOnJre != null) {
                    if (disabledOnJre.stream().allMatch(v -> compareVersions(v, javaVersion) < 0)) {
                        m = removeAnnotation(m, DISABLED_ON_JRE, ctx);
                    } else {
                        m = updateAnnotationVersions(m, DISABLED_ON_JRE);
                    }
                }
                if (enabledOnJreRange != null) {
                    if (compareVersions(enabledOnJreRange.getMax(), javaVersion) < 0) {
                        // Remove the test method if it is enabled on a JRE range that ends before the specified version
                        maybeRemoveImport(JRE_IMPORT);
                        return null;
                    }
                    if (compareVersions(enabledOnJreRange.getMin(), javaVersion) <= 0 && enabledOnJreRange.getMaxNotation() == null) {
                        // The test is enabled for all versions from `javaVersion till end -> remove annotation
                        m = removeAnnotation(m, ENABLED_FOR_JRE_RANGE, ctx);
                    } else {
                        m = updateRangeStart(m, ENABLED_FOR_JRE_RANGE, enabledOnJreRange, ctx);
                    }
                }
                if (disabledOnJreRange != null) {
                    if (compareVersions(disabledOnJreRange.getMax(), javaVersion) < 0) {
                        m = removeAnnotation(m, DISABLED_FOR_JRE_RANGE, ctx);
                    } else if (compareVersions(disabledOnJreRange.getMin(), javaVersion) <= 0 && disabledOnJreRange.getMaxNotation() == null) {
                        // The test is disabled for all versions from `javaVersion` till end -> remove test
                        maybeRemoveImport(JRE_IMPORT);
                        return null;
                    } else {
                        m = updateRangeStart(m, DISABLED_FOR_JRE_RANGE, disabledOnJreRange, ctx);
                    }
                }

                m = replaceSingleVersionRangeWithEquivalentAnnotation(m, enabledOnJreRange, ENABLED_FOR_JRE_RANGE, ENABLED_ON_JRE, ctx);
                m = replaceSingleVersionRangeWithEquivalentAnnotation(m, disabledOnJreRange, DISABLED_FOR_JRE_RANGE, DISABLED_ON_JRE, ctx);

                if (m != method) {
                    return simplifySingleValueAnnotationAttributeArrays(m, prefix);
                }
                return method;
            }

            private J.MethodDeclaration removeAnnotation(J.MethodDeclaration m, String annotationType, ExecutionContext ctx) {
                RemoveAnnotation removeAnnotation = new RemoveAnnotation("@" + annotationType);
                maybeRemoveImport(JRE_IMPORT);
                return (J.MethodDeclaration) removeAnnotation.getVisitor().visit(m, ctx, getCursor().getParentTreeCursor());
            }

            private J.MethodDeclaration updateAnnotationVersions(J.MethodDeclaration m, String annotationType) {
                return m.withLeadingAnnotations(ListUtils.map(m.getLeadingAnnotations(), ann -> {
                    if (!TypeUtils.isOfClassType(ann.getType(), annotationType)) {
                        return ann;
                    }
                    return ann.withArguments(ListUtils.map(ann.getArguments(), arg -> {
                        if (!(arg instanceof J.Assignment)) {
                            return keepVersionsFromMinimum(arg);
                        }
                        J.Assignment assignment = (J.Assignment) arg;
                        String attribute = assignment.getVariable().printTrimmed(getCursor());
                        if ("value".equals(attribute) || "versions".equals(attribute)) {
                            return assignment.withAssignment(keepVersionsFromMinimum(assignment.getAssignment()));
                        }
                        return assignment;
                    }));
                }));
            }

            private Expression keepVersionsFromMinimum(Expression versions) {
                if (!(versions instanceof J.NewArray) || ((J.NewArray) versions).getInitializer() == null) {
                    return versions;
                }
                J.NewArray array = (J.NewArray) versions;
                JContainer<Expression> initializer = array.getPadding().getInitializer();
                List<JRightPadded<Expression>> elements = initializer.getPadding().getElements();
                List<JRightPadded<Expression>> kept = ListUtils.map(elements, e -> compareVersions(e.getElement().toString(), javaVersion) < 0 ? null : e);
                if (kept == elements || kept.isEmpty()) {
                    return array;
                }
                kept = ListUtils.mapFirst(kept, e -> e.withElement(e.getElement().withPrefix(elements.get(0).getElement().getPrefix())));
                kept = ListUtils.mapLast(kept, e -> e.withAfter(elements.get(elements.size() - 1).getAfter()));
                return array.getPadding().withInitializer(initializer.getPadding().withElements(kept));
            }

            // Return a method declaration with a new range only if the start is below requested java version.
            // If the start version would be equal to the end version, then no updated method is returned as this is handled in a later phase
            private J.MethodDeclaration updateRangeStart(J.MethodDeclaration m, String annotationtype, Range range, ExecutionContext ctx) {
                if (compareVersions(range.getMin(), javaVersion) <= 0 && compareVersions(range.getMax(), javaVersion) != 0) {
                    // Update the range to start at the specified version
                    String attributeName = range.getMinNotation() == RangeNotation.VERSION ? "minVersion" : "min";
                    RemoveAnnotationAttribute updatedRange = new RemoveAnnotationAttribute(annotationtype, attributeName);
                    m = (J.MethodDeclaration) updatedRange.getVisitor().visitNonNull(m, ctx, getCursor().getParent());
                }
                return m;
            }

            // If the range is resulting in a single version after updating (current max is the new min), then we can replace the range annotation with a single version annotation.
            private J.MethodDeclaration replaceSingleVersionRangeWithEquivalentAnnotation(J.MethodDeclaration m, @Nullable Range range, String rangeAnnotationType, String singleVersionAnnotationType, ExecutionContext ctx) {
                if (range == null || compareVersions(range.getMax(), javaVersion) != 0) {
                    return m;
                }
                Cursor methodCursor = new Cursor(getCursor().getParent(), m);
                return m.withLeadingAnnotations(ListUtils.map(m.getLeadingAnnotations(), ann -> {
                    if (!TypeUtils.isOfClassType(ann.getType(), rangeAnnotationType)) {
                        return ann;
                    }
                    maybeAddImport(singleVersionAnnotationType);
                    String singleVersion;
                    switch (range.getNotation()) {
                        case JRE:
                            maybeAddImport(JRE_IMPORT);
                            singleVersion = "JRE.JAVA_" + javaVersion;
                            break;
                        case STATIC_JRE:
                            maybeAddImport(JRE_IMPORT, "JAVA_" + javaVersion);
                            singleVersion = "JAVA_" + javaVersion;
                            break;
                        case VERSION:
                        default:
                            singleVersion = "versions = " + javaVersion;
                    }
                    return JavaTemplate.builder("@" + singleVersionAnnotationType.substring(singleVersionAnnotationType.lastIndexOf('.') + 1) + "(" + singleVersion + ")")
                            .imports(singleVersionAnnotationType, JRE_IMPORT)
                            .staticImports(JRE_IMPORT + ".JAVA_" + javaVersion)
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5"))
                            .build()
                            .apply(new Cursor(methodCursor, ann), ann.getCoordinates().replace());
                }));
            }

            private J.MethodDeclaration simplifySingleValueAnnotationAttributeArrays(J.MethodDeclaration m, Space prefix) {
                return m.withLeadingAnnotations(ListUtils.map(m.getLeadingAnnotations(), ann -> {
                    Cursor parentCursor = getCursor().getParent();
                    if (ENABLED_JRE_MATCHER.get(ann, parentCursor).isPresent() ||
                            DISABLED_JRE_MATCHER.get(ann, parentCursor).isPresent() ||
                            ENABLED_JRE_RANGE_MATCHER.get(ann, parentCursor).isPresent() ||
                            DISABLED_JRE_RANGE_MATCHER.get(ann, parentCursor).isPresent()) {
                        ann = ann
                                .withArguments(ListUtils.map(ann.getArguments(), arg -> {
                                    if (arg instanceof J.NewArray) {
                                        List<Expression> initializer = ((J.NewArray) arg).getInitializer();
                                        if (initializer != null && initializer.size() == 1 && !(initializer.get(0) instanceof J.Empty)) {
                                            return initializer.get(0).withPrefix(arg.getPrefix());
                                        }
                                    } else if (arg instanceof J.Assignment) {
                                        J.Assignment ass = (J.Assignment) arg;
                                        if (ass.getAssignment() instanceof J.NewArray) {
                                            List<Expression> initializer = ((J.NewArray) ass.getAssignment()).getInitializer();
                                            if (initializer != null && initializer.size() == 1 && !(initializer.get(0) instanceof J.Empty)) {
                                                return ass.withAssignment(initializer.get(0).withPrefix(ass.getAssignment().getPrefix()));
                                            }
                                        }
                                    }
                                    return arg;
                                }))
                                .withPrefix(prefix);
                    }
                    return ann;
                }));
            }

            private List<String> extractVersionsFromAnnotationArgument(Expression expression) {
                if (expression instanceof J.NewArray) {
                    List<Expression> initializer = ((J.NewArray) expression).getInitializer();
                    if (initializer == null) {
                        return emptyList();
                    }
                    return initializer.stream().map(Objects::toString).collect(toList());
                }
                return singletonList(expression.toString());
            }
        });
    }

    private static Optional<Expression> getDefaultAttribute(Annotated annotated) {
        if (annotated.getTree().getArguments() == null) {
            return Optional.empty();
        }
        for (Expression argument : annotated.getTree().getArguments()) {
            if (!(argument instanceof J.Assignment)) {
                return Optional.of(argument);
            }
        }
        return getAttribute(annotated, "value");
    }

    private static Optional<Expression> getAttribute(Annotated annotated, String attribute) {
        if (annotated.getTree().getArguments() == null) {
            return Optional.empty();
        }
        for (Expression argument : annotated.getTree().getArguments()) {
            if (argument instanceof J.Assignment) {
                J.Assignment assignment = (J.Assignment) argument;
                if (assignment.getVariable() instanceof J.Identifier &&
                        ((J.Identifier) assignment.getVariable()).getSimpleName().equals(attribute)) {
                    return Optional.of(assignment.getAssignment());
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isComparable(@Nullable List<String> versions) {
        return versions == null || versions.stream().allMatch(v -> fromJREVersion(v).matches("\\d+"));
    }

    private static boolean isComparable(@Nullable Range range) {
        return range == null || isComparable(Arrays.asList(range.getMin(), range.getMax()));
    }

    private static int compareVersions(String version1, String version2) {
        String numericVersion1 = fromJREVersion(version1);
        String numericVersion2 = fromJREVersion(version2);
        int v1 = Integer.parseInt(numericVersion1.replaceAll("\\D", ""));
        int v2 = Integer.parseInt(numericVersion2.replaceAll("\\D", ""));

        int comparison = Integer.compare(v1, v2);
        if (comparison == 0) {
            return comparison;
        }
        return comparison / Math.abs(comparison);
    }

    private static String fromJREVersion(String version) {
        String constant = version.substring(version.lastIndexOf('.') + 1);
        if (constant.startsWith("JAVA_")) {
            return constant.substring(5);
        }
        if ("OTHER".equals(constant)) {
            return String.valueOf(Integer.MAX_VALUE);
        }
        return constant;
    }

    @Getter
    @ToString
    private static final class Range {
        private final String min;

        @Nullable
        private final RangeNotation minNotation;

        private final String max;

        @Nullable
        private final RangeNotation maxNotation;

        public Range(Annotated annotated) {
            Optional<String> minAttribute = getAttribute(annotated, "min").map(Objects::toString);
            Optional<String> minVersionAttribute = getAttribute(annotated, "minVersion").map(Objects::toString);
            Optional<String> maxAttribute = getAttribute(annotated, "max").map(Objects::toString);
            Optional<String> maxVersionAttribute = getAttribute(annotated, "maxVersion").map(Objects::toString);
            String min = minAttribute.orElse(minVersionAttribute.orElse(null));
            String max = maxAttribute.orElse(maxVersionAttribute.orElse(null));
            this.min = min == null ? "0" : min;
            this.max = max == null ? String.valueOf(Integer.MAX_VALUE) : max;
            this.minNotation = minAttribute
                    .map(it -> it.startsWith("JRE.") ? RangeNotation.JRE : RangeNotation.STATIC_JRE)
                    .orElse(minVersionAttribute.map(__ -> RangeNotation.VERSION).orElse(null));
            this.maxNotation = maxAttribute
                    .map(it -> it.startsWith("JRE.") ? RangeNotation.JRE : RangeNotation.STATIC_JRE)
                    .orElse(maxVersionAttribute.map(__ -> RangeNotation.VERSION).orElse(null));
        }

        private RangeNotation getNotation() {
            if (minNotation == null && maxNotation == null) {
                return RangeNotation.VERSION;
            }
            if (maxNotation == null) {
                return minNotation;
            }
            return maxNotation;
        }
    }

    enum RangeNotation {
        JRE, STATIC_JRE, VERSION
    }
}
