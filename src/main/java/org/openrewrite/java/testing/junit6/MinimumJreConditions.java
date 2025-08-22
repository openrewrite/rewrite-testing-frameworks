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
package org.openrewrite.java.testing.junit6;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.trait.Annotated;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
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

    @Override
    public String getDisplayName() {
        return "Migrate JUnit JRE conditions";
    }

    @Override
    public String getDescription() {
        return "This recipe will:\n" + " - Remove tests that are only active on JREs that are below the specified version.\n" + " - Adjust ranges to use minimum the specified version.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

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
                Optional<List<String>> enabledOnJre = Optional.empty();
                Optional<List<String>> disabledOnJre = Optional.empty();
                Optional<Range> enabledOnJreRange = Optional.empty();
                Optional<Range> disabledOnJreRange = Optional.empty();
                Space prefix = Space.EMPTY;

                // First assemble all annotations to see if this is a unit test impacted with JRE conditional annotations
                for (J.Annotation ann : method.getLeadingAnnotations()) {
                    Cursor annotationCursor = new Cursor(getCursor(), ann);
                    if (TEST_ANNOTATION_MATCHERS.stream().anyMatch(matcher -> matcher.get(annotationCursor).isPresent())) {
                        isUnitTest = true;
                    }
                    Optional<Annotated> annotated = ENABLED_JRE_MATCHER.get(annotationCursor);
                    if (annotated.isPresent()) {
                        enabledOnJre = annotated.map(a -> getDefaultAttribute(a).orElse(getAttribute(a, "versions").orElse(null))).map(e -> {
                            if (e instanceof J.NewArray) {
                                List<Expression> initializer = ((J.NewArray) e).getInitializer();
                                if (initializer == null) {
                                    return emptyList();
                                }
                                return initializer.stream().map(Objects::toString).collect(toList());
                            }
                            return singletonList(e.toString());
                        });
                        prefix = ann.getPrefix();
                    }
                    annotated = DISABLED_JRE_MATCHER.get(annotationCursor);
                    if (annotated.isPresent()) {
                        disabledOnJre = DISABLED_JRE_MATCHER.get(annotationCursor).map(a -> getDefaultAttribute(a).orElse(getAttribute(a, "versions").orElse(null))).map(e -> {
                            if (e instanceof J.NewArray) {
                                List<Expression> initializer = ((J.NewArray) e).getInitializer();
                                if (initializer == null) {
                                    return emptyList();
                                }
                                return initializer.stream().map(Objects::toString).collect(toList());
                            }
                            return singletonList(e.toString());
                        });
                        prefix = ann.getPrefix();
                    }
                    annotated = ENABLED_JRE_RANGE_MATCHER.get(annotationCursor);
                    if (annotated.isPresent()) {
                        enabledOnJreRange = ENABLED_JRE_RANGE_MATCHER.get(annotationCursor).map(Range::new);
                        prefix = ann.getPrefix();
                    }
                    annotated = DISABLED_JRE_RANGE_MATCHER.get(annotationCursor);
                    if (annotated.isPresent()) {
                        disabledOnJreRange = DISABLED_JRE_RANGE_MATCHER.get(annotationCursor).map(Range::new);
                        prefix = ann.getPrefix();
                    }
                }

                // Only act upon unit tests that have JRE conditions
                if (!isUnitTest || !(enabledOnJre.isPresent() || disabledOnJre.isPresent() || enabledOnJreRange.isPresent() || disabledOnJreRange.isPresent())) {
                    return m;
                }

                // Now transform the annotations to the correct results
                if (enabledOnJre.isPresent()) {
                    if (enabledOnJre.get().stream().allMatch(v -> compareVersions(v, javaVersion) < 0)) {
                        // Remove the test method if it is enabled on a JRE version lower than the specified version
                        maybeRemoveImport(JRE_IMPORT);
                        return null;
                    }
                    m = updateAnnotationVersions(m, enabledOnJre, ENABLED_ON_JRE, ctx);
                }
                if (disabledOnJre.isPresent()) {
                    if (disabledOnJre.get().stream().allMatch(v -> compareVersions(v, javaVersion) < 0)) {
                        m = removeAnnotation(m, DISABLED_ON_JRE, ctx);
                    } else {
                        m = updateAnnotationVersions(m, disabledOnJre, DISABLED_ON_JRE, ctx);
                    }
                }
                if (enabledOnJreRange.isPresent()) {
                    Range range = enabledOnJreRange.get();
                    if (compareVersions(range.getMax(), javaVersion) < 0) {
                        // Remove the test method if it is enabled on a JRE range that ends before the specified version
                        maybeRemoveImport(JRE_IMPORT);
                        return null;
                    } else if (compareVersions(range.getMin(), javaVersion) <= 0 && range.getMaxNotation() == null) {
                        // The test is enabled for all versions from `javaVersion till end -> remove annotation
                        m = removeAnnotation(m, ENABLED_FOR_JRE_RANGE, ctx);
                    } else {
                        m = updateRangeStart(m, ENABLED_FOR_JRE_RANGE, range, ctx);
                    }
                }
                if (disabledOnJreRange.isPresent()) {
                    Range range = disabledOnJreRange.get();
                    if (compareVersions(range.getMax(), javaVersion) < 0) {
                        m = removeAnnotation(m, DISABLED_FOR_JRE_RANGE, ctx);
                    } else if (compareVersions(range.getMin(), javaVersion) <= 0 && range.getMaxNotation() == null) {
                        // The test is disabled for all versions from `javaVersion` till end -> remove test
                        maybeRemoveImport(JRE_IMPORT);
                        return null;
                    } else {
                        m = updateRangeStart(m, DISABLED_FOR_JRE_RANGE, range, ctx);
                    }
                }

                replaceSingleVersionRangeWithEquivalentAnnotation(enabledOnJreRange, ENABLED_FOR_JRE_RANGE, ENABLED_ON_JRE);
                replaceSingleVersionRangeWithEquivalentAnnotation(disabledOnJreRange, DISABLED_FOR_JRE_RANGE, DISABLED_ON_JRE);

                if (m != method) {
                    return simplifySingleValueAnnotationAttributeArrays(m, prefix);
                }
                return method;
            }

            private J.MethodDeclaration removeAnnotation(J.MethodDeclaration m, String annotationType, ExecutionContext ctx) {
                RemoveAnnotation removeAnnotation = new RemoveAnnotation("@" + annotationType);
                maybeRemoveImport(JRE_IMPORT);
                return removeAnnotation.getVisitor().visitMethodDeclaration(m, ctx);
            }

            private J.MethodDeclaration updateAnnotationVersions(J.MethodDeclaration m, Optional<List<String>> versions, String annotationType, ExecutionContext ctx) {
                if (versions.isPresent() && versions.get().stream().anyMatch(v -> compareVersions(v, javaVersion) < 0)) {
                    // Update the annotation to only include JRE versions that are greater than or equal to the specified version
                    AddOrUpdateAnnotationAttribute updatedVersions = new AddOrUpdateAnnotationAttribute(annotationType, "versions", versions.get().stream().filter(v -> compareVersions(v, javaVersion) >= 0).collect(joining(", ")), null, false, false);
                    m = (J.MethodDeclaration) updatedVersions.getVisitor().visit(m, ctx, getCursor().getParent());
                }
                return m;
            }

            // Return a method declaration with a new range only if the start is below requested java version.
            // If the start version would be equal to the end version, then no updated method is returned as this is handled in a later phase
            private J.MethodDeclaration updateRangeStart(J.MethodDeclaration m, String annotationtype, Range range, ExecutionContext ctx) {
                if (compareVersions(range.getMin(), javaVersion) <= 0) {
                    if (compareVersions(range.getMax(), javaVersion) != 0) {
                        // Update the range to start at the specified version
                        String attributeName = range.getMinNotation() == RangeNotation.VERSION ? "minVersion" : "min";
                        RemoveAnnotationAttribute updatedRange = new RemoveAnnotationAttribute(annotationtype, attributeName);
                        m = (J.MethodDeclaration) updatedRange.getVisitor().visit(m, ctx, getCursor().getParent());
                    }
                }
                return m;
            }

            // If the range is resulting in a single version after updating (current max is the new min), then we can replace the range annotation with a single version annotation.
            private void replaceSingleVersionRangeWithEquivalentAnnotation(Optional<Range> optionalRange, String rangeAnnotationType, String singleVersionAnnotationType) {
                if (optionalRange.isPresent()) {
                    Range range = optionalRange.get();
                    if (compareVersions(range.getMax(), javaVersion) == 0) {
                        doAfterVisit(new ChangeType(rangeAnnotationType, singleVersionAnnotationType, false).getVisitor());
                        doAfterVisit(new RemoveAnnotationAttribute(singleVersionAnnotationType, "min").getVisitor());
                        doAfterVisit(new RemoveAnnotationAttribute(singleVersionAnnotationType, "minVersion").getVisitor());
                        doAfterVisit(new RemoveAnnotationAttribute(singleVersionAnnotationType, "max").getVisitor());
                        doAfterVisit(new RemoveAnnotationAttribute(singleVersionAnnotationType, "maxVersion").getVisitor());
                        String attributeName = range.getNotation() == RangeNotation.VERSION ? "versions" : "value";
                        String newValue = formatJreValue(javaVersion, range.getNotation());
                        doAfterVisit(new AddOrUpdateAnnotationAttribute(singleVersionAnnotationType, attributeName, newValue, null, false, false).getVisitor());
                    }
                }
            }

            private J.MethodDeclaration simplifySingleValueAnnotationAttributeArrays(J.MethodDeclaration m, Space prefix) {
                return m.withLeadingAnnotations(ListUtils.map(m.getLeadingAnnotations(), ann -> {
                    if (ENABLED_JRE_MATCHER.get(ann, getCursor().getParent()).isPresent() || DISABLED_JRE_MATCHER.get(ann, getCursor().getParent()).isPresent() || ENABLED_JRE_RANGE_MATCHER.get(ann, getCursor().getParent()).isPresent() || DISABLED_JRE_RANGE_MATCHER.get(ann, getCursor().getParent()).isPresent()) {
                        ann = ann
                                .withArguments(ListUtils.map(ann.getArguments(), arg -> {
                                    if (arg instanceof J.Assignment) {
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
        };
    }

    private Optional<Expression> getDefaultAttribute(Annotated annotated) {
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
                if (assignment.getVariable() instanceof J.Identifier) {
                    J.Identifier identifier = (J.Identifier) assignment.getVariable();
                    if (identifier.getSimpleName().equals(attribute)) {
                        return Optional.of(assignment.getAssignment());
                    }
                }
            }
        }
        return Optional.empty();
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
        if (version.startsWith("JRE.")) {
            version = version.substring(4);
        }
        if (version.startsWith("JAVA_")) {
            return version.substring(5);
        }
        if ("OTHER".equals(version)) {
            return String.valueOf(Integer.MAX_VALUE);
        }
        return version;
    }

    private static String formatJreValue(String version, @Nullable RangeNotation notation) {
        if (notation == null) {
            return version;
        }
        switch (notation) {
            case VERSION:
                return version;
            case JRE:
                return "JRE.JAVA_" + version;
            case STATIC_JRE:
                return "JAVA_" + version;
            default:
                return version;
        }
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
            if (minAttribute.isPresent()) {
                if (minAttribute.get().startsWith("JRE.")) {
                    this.minNotation = RangeNotation.JRE;
                } else {
                    this.minNotation = RangeNotation.STATIC_JRE;
                }
            } else if (minVersionAttribute.isPresent()) {
                this.minNotation = RangeNotation.VERSION;
            } else {
                this.minNotation = null;
            }
            if (maxAttribute.isPresent()) {
                if (maxAttribute.get().startsWith("JRE.")) {
                    this.maxNotation = RangeNotation.JRE;
                } else {
                    this.maxNotation = RangeNotation.STATIC_JRE;
                }
            } else if (maxVersionAttribute.isPresent()) {
                this.maxNotation = RangeNotation.VERSION;
            } else {
                this.maxNotation = null;
            }
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
