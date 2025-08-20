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
import org.openrewrite.java.AddOrUpdateAnnotationAttribute;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveAnnotation;
import org.openrewrite.java.trait.Annotated;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;

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

    private static final String ENABLED_ON_JRE = "org.junit.jupiter.api.condition.EnabledOnJre";
    private static final String DISABLED_ON_JRE = "org.junit.jupiter.api.condition.DisabledOnJre";
    private static final String ENABLED_FOR_JRE_RANGE = "org.junit.jupiter.api.condition.EnabledForJreRange";
    private static final String DISABLED_FOR_JRE_RANGE = "org.junit.jupiter.api.condition.DisabledForJreRange";
    private static final Annotated.Matcher TEST_ANNOTATION_MATCHER = new Annotated.Matcher("@org.junit.jupiter.api.Test");
    private static final Annotated.Matcher ENABLED_JRE_MATCHER = new Annotated.Matcher("@" + ENABLED_ON_JRE);
    private static final Annotated.Matcher DISABLED_JRE_MATCHER = new Annotated.Matcher("@" + DISABLED_ON_JRE);
    private static final Annotated.Matcher ENABLED_JRE_RANGE_MATCHER = new Annotated.Matcher("@" + ENABLED_FOR_JRE_RANGE);
    private static final Annotated.Matcher DISABLED_JRE_RANGE_MATCHER = new Annotated.Matcher("@" + DISABLED_FOR_JRE_RANGE);

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
            public  J.@Nullable MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                boolean isUnitTest = false;
                Optional<List<String>> enabledOnJre = Optional.empty();
                Optional<List<String>> disabledOnJre = Optional.empty();
                Optional<Range> enabledOnJreRange = Optional.empty();
                Optional<Range> disabledOnJreRange = Optional.empty();
                Space prefix = Space.EMPTY;
                for (J.Annotation ann : method.getLeadingAnnotations()) {
                    Cursor annotationCursor = new Cursor(getCursor(), ann);
                    if (TEST_ANNOTATION_MATCHER.get(annotationCursor).isPresent()) {
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
                        enabledOnJreRange = ENABLED_JRE_RANGE_MATCHER.get(annotationCursor).map(a -> {
                            String min = getAttribute(a, "min").map(Objects::toString).orElse(getAttribute(a, "minVersion").map(Objects::toString).orElse(null));
                            String max = getAttribute(a, "max").map(Objects::toString).orElse(getAttribute(a, "maxVersion").map(Objects::toString).orElse(null));
                            return new Range(min, max);
                        });
                        prefix = ann.getPrefix();
                    }
                    annotated = DISABLED_JRE_RANGE_MATCHER.get(annotationCursor);
                    if (annotated.isPresent()) {
                        disabledOnJreRange = DISABLED_JRE_RANGE_MATCHER.get(annotationCursor).map(a -> {
                            String min = getAttribute(a, "min").map(Objects::toString).orElse(getAttribute(a, "minVersion").map(Objects::toString).orElse(null));
                            String max = getAttribute(a, "max").map(Objects::toString).orElse(getAttribute(a, "maxVersion").map(Objects::toString).orElse(null));
                            return new Range(min, max);
                        });
                        prefix = ann.getPrefix();
                    }
                }
                if (!isUnitTest || !(enabledOnJre.isPresent() || disabledOnJre.isPresent() || enabledOnJreRange.isPresent() || disabledOnJreRange.isPresent())) {
                    return m;
                }

                if (enabledOnJre.isPresent()) {
                    if (enabledOnJre.get().stream().allMatch(v -> compareVersions(v, javaVersion) < 0)) {
                        // Remove the test method if it is enabled on a JRE version lower than the specified version
                        return null;
                    }
                    if (enabledOnJre.get().stream().filter(v -> compareVersions(v, javaVersion) >= 0).allMatch(v -> compareVersions(v, javaVersion) == 0)) {
                        // Remove the annotation if it is enabled on the same JRE version
                        RemoveAnnotation removeAnnotation = new RemoveAnnotation("@" + ENABLED_ON_JRE);
                        m = removeAnnotation.getVisitor().visitMethodDeclaration(m, ctx);
                    } else if (enabledOnJre.get().stream().anyMatch(v -> compareVersions(v, javaVersion) < 0)) {
                        AddOrUpdateAnnotationAttribute updatedVersions = new AddOrUpdateAnnotationAttribute(ENABLED_ON_JRE, "versions", enabledOnJre.get().stream().filter(v -> compareVersions(v, javaVersion) >= 0).collect(joining(", ")), null, false, false);
                        m = (J.MethodDeclaration) updatedVersions.getVisitor().visit(m, ctx, getCursor().getParent());
                    }
                }
                if (disabledOnJre.isPresent()) {
                    if (disabledOnJre.get().stream().allMatch(v -> compareVersions(v, javaVersion) < 0)) {
                        // Remove the annotation if it is disabled on a lower JRE version
                        RemoveAnnotation removeAnnotation = new RemoveAnnotation("@" + DISABLED_ON_JRE);
                        m = removeAnnotation.getVisitor().visitMethodDeclaration(m, ctx);
                    } else if (disabledOnJre.get().stream().anyMatch(v -> compareVersions(v, javaVersion) < 0)) {
                        // Update the annotation to only include JRE versions that are greater than or equal to the specified version
                        AddOrUpdateAnnotationAttribute updatedVersions = new AddOrUpdateAnnotationAttribute(DISABLED_ON_JRE, "versions", disabledOnJre.get().stream().filter(v -> compareVersions(v, javaVersion) >= 0).collect(joining(", ")), null, false, false);
                        m = (J.MethodDeclaration) updatedVersions.getVisitor().visit(m, ctx, getCursor().getParent());
                    }
                }
                if (enabledOnJreRange.isPresent()) {
                    if (compareVersions(enabledOnJreRange.map(Range::getMax).get(), javaVersion) < 0) {
                        // Remove the test method if it is enabled on a JRE range that ends before the specified version
                        return null;
                    }
                    if (compareVersions(enabledOnJreRange.map(Range::getMin).get(), javaVersion) < 0) {
                        //TODO update the range to start at the specified version and convert to a @EnabledOnJre annotation if the range is now a single version
                    }
                }
                if (disabledOnJreRange.isPresent()) {
                    if (compareVersions(disabledOnJreRange.map(Range::getMax).get(), javaVersion) < 0) {
                        // Remove the annotation if it is disabled on a JRE range that ends before the specified version
                        RemoveAnnotation removeAnnotation = new RemoveAnnotation("@" + DISABLED_FOR_JRE_RANGE);
                        m = removeAnnotation.getVisitor().visitMethodDeclaration(m, ctx);
                    }
                    if (compareVersions(disabledOnJreRange.map(Range::getMin).get(), javaVersion) < 0) {
                        //TODO update the range to start at the specified version and convert to a @DisabledOnJre annotation if the range is now a single version
                    }
                }

                Space finalPrefix = prefix;
                return m.withLeadingAnnotations(ListUtils.map(m.getLeadingAnnotations(), ann -> {
                    if (ENABLED_JRE_MATCHER.get(ann, getCursor().getParent()).isPresent() || DISABLED_JRE_MATCHER.get(ann, getCursor().getParent()).isPresent() || ENABLED_JRE_RANGE_MATCHER.get(ann, getCursor().getParent()).isPresent() || DISABLED_JRE_RANGE_MATCHER.get(ann, getCursor().getParent()).isPresent()) {
                        ann = ann.withPrefix(finalPrefix);
                    }
                    return ann;
                }));
            }
        };
    }

    public Optional<Expression> getDefaultAttribute(Annotated annotated) {
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

    public Optional<Expression> getAttribute(Annotated annotated, String attribute) {
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

    @Getter
    @ToString
    private static final class Range {
        @Nullable
        private final String min;

        @Nullable
        private final String max;

        public Range(@Nullable String min, @Nullable String max) {
            this.min = min == null ? String.valueOf(Integer.MIN_VALUE) : min;
            this.max = max == null ? String.valueOf(Integer.MAX_VALUE) : max;
        }
    }
}
