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
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.trait.Annotated;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static java.lang.String.format;
import static java.util.Collections.emptySet;
import static java.util.Comparator.comparing;

public class JUnitSoftAssertionsToSoftAssertionsExtension extends Recipe {

    private static final String JUNIT_SOFT_ASSERTIONS = "org.assertj.core.api.JUnitSoftAssertions";
    private static final String JUNIT_BDD_SOFT_ASSERTIONS = "org.assertj.core.api.JUnitBDDSoftAssertions";

    private static final Map<String, String> RULE_TO_PROVIDER = new LinkedHashMap<String, String>() {{
        put(JUNIT_SOFT_ASSERTIONS, "org.assertj.core.api.SoftAssertions");
        put(JUNIT_BDD_SOFT_ASSERTIONS, "org.assertj.core.api.BDDSoftAssertions");
    }};

    private static final String RULE = "org.junit.Rule";
    private static final String EXTEND_WITH = "org.junit.jupiter.api.extension.ExtendWith";
    private static final String INJECT_SOFT_ASSERTIONS = "org.assertj.core.api.junit.jupiter.InjectSoftAssertions";
    private static final String SOFT_ASSERTIONS_EXTENSION = "org.assertj.core.api.junit.jupiter.SoftAssertionsExtension";

    private static final AnnotationMatcher RULE_MATCHER = new AnnotationMatcher('@' + RULE);
    private static final AnnotationMatcher EXTEND_WITH_MATCHER =
            new AnnotationMatcher(format("@%s(%s.class)", EXTEND_WITH, SOFT_ASSERTIONS_EXTENSION), true);

    private static final String CONVERTED_TYPES = "convertedSoftAssertionsRuleTypes";
    private static final String UNCONVERTIBLE_TYPES = "unconvertibleSoftAssertionsRuleTypes";

    @Getter
    final String displayName = "AssertJ `@Rule` soft assertions to `SoftAssertionsExtension`";

    @Getter
    final String description = "Replaces `@Rule` fields of type `JUnitSoftAssertions` or `JUnitBDDSoftAssertions` with " +
            "`@InjectSoftAssertions` fields, and registers `@ExtendWith(SoftAssertionsExtension.class)` on the test class. " +
            "JUnit Jupiter does not run JUnit 4 rules, so soft assertions collected through such a rule would otherwise " +
            "never be reported, silently passing tests that ought to fail.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(
                        new UsesType<>(RULE, false),
                        Preconditions.or(
                                new UsesType<>(JUNIT_SOFT_ASSERTIONS, false),
                                new UsesType<>(JUNIT_BDD_SOFT_ASSERTIONS, false))),
                new JavaIsoVisitor<ExecutionContext>() {

                    // Kotlin properties would need `lateinit var` rather than a dropped `final` and initializer, and
                    // the `ChangeType` below is driven from a `J.CompilationUnit` that a Kotlin source does not have.
                    @Override
                    public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                        return sourceFile instanceof J.CompilationUnit;
                    }

                    @Override
                    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                        getCursor().putMessage(UNCONVERTIBLE_TYPES, unconvertibleRuleTypes(cu));
                        J.CompilationUnit c = super.visitCompilationUnit(cu, ctx);
                        Set<String> convertedTypes = getCursor().pollMessage(CONVERTED_TYPES);
                        if (convertedTypes == null) {
                            return c;
                        }
                        for (String convertedType : convertedTypes) {
                            c = (J.CompilationUnit) new ChangeType(convertedType, RULE_TO_PROVIDER.get(convertedType), true)
                                    .getVisitor().visitNonNull(c, ctx);
                        }
                        maybeRemoveImport(RULE);
                        return c;
                    }

                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                        if (getCursor().pollMessage(CONVERTED_TYPES) == null ||
                                service(AnnotationService.class).matches(updateCursor(cd), EXTEND_WITH_MATCHER)) {
                            return cd;
                        }
                        maybeAddImport(EXTEND_WITH);
                        maybeAddImport(SOFT_ASSERTIONS_EXTENSION);
                        return JavaTemplate.builder("@ExtendWith(SoftAssertionsExtension.class)")
                                .imports(EXTEND_WITH, SOFT_ASSERTIONS_EXTENSION)
                                .javaParser(JavaParser.fromJavaVersion()
                                        .classpathFromResources(ctx, "junit-jupiter-api-5", "assertj-core-3"))
                                .build()
                                .apply(updateCursor(cd), cd.getCoordinates().addAnnotation(comparing(J.Annotation::getSimpleName)));
                    }

                    @Override
                    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                        J.VariableDeclarations mv = super.visitVariableDeclarations(multiVariable, ctx);
                        String ruleType = softAssertionsType(mv);
                        if (ruleType == null ||
                                J.Modifier.hasModifier(mv.getModifiers(), J.Modifier.Type.Static) ||
                                getCursor().<Set<String>>getNearestMessage(UNCONVERTIBLE_TYPES, emptySet()).contains(ruleType) ||
                                !service(AnnotationService.class).matches(getCursor(), RULE_MATCHER)) {
                            return mv;
                        }

                        mv = maybeAutoFormat(mv, mv.withModifiers(ListUtils.map(mv.getModifiers(),
                                m -> m.getType() == J.Modifier.Type.Final ? null : m)), ctx, getCursor().getParentOrThrow());
                        mv = mv.withVariables(ListUtils.map(mv.getVariables(), v -> v.withInitializer(null)));
                        mv = (J.VariableDeclarations) new Annotated.Matcher('@' + RULE)
                                .asVisitor(a -> JavaTemplate.builder("@InjectSoftAssertions")
                                        .imports(INJECT_SOFT_ASSERTIONS)
                                        .javaParser(JavaParser.fromJavaVersion()
                                                .classpathFromResources(ctx, "junit-jupiter-api-5", "assertj-core-3"))
                                        .build()
                                        .apply(a.getCursor(), a.getTree().getCoordinates().replace()))
                                .visitNonNull(mv, ctx, getCursor().getParentOrThrow());
                        maybeAddImport(INJECT_SOFT_ASSERTIONS);

                        getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, CONVERTED_TYPES, ruleType);
                        getCursor().dropParentUntil(J.CompilationUnit.class::isInstance)
                                .<Set<String>>computeMessageIfAbsent(CONVERTED_TYPES, k -> new LinkedHashSet<>())
                                .add(ruleType);
                        return mv;
                    }
                });
    }

    private static @Nullable String softAssertionsType(J.VariableDeclarations mv) {
        for (String ruleType : RULE_TO_PROVIDER.keySet()) {
            if (TypeUtils.isOfClassType(mv.getType(), ruleType)) {
                return ruleType;
            }
        }
        return null;
    }

    // Types also declared as a field this recipe leaves alone, such as a `@ClassRule` or a field only referenced from a
    // `RuleChain`; those fields still have to implement `TestRule`, so the compilation unit wide `ChangeType` that
    // follows the conversion must not run for their type.
    private static Set<String> unconvertibleRuleTypes(J.CompilationUnit cu) {
        return new JavaIsoVisitor<Set<String>>() {
            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations mv, Set<String> types) {
                String ruleType = softAssertionsType(mv);
                if (ruleType != null && isField(getCursor()) &&
                        (J.Modifier.hasModifier(mv.getModifiers(), J.Modifier.Type.Static) ||
                                mv.getLeadingAnnotations().stream().noneMatch(RULE_MATCHER::matches))) {
                    types.add(ruleType);
                }
                return super.visitVariableDeclarations(mv, types);
            }

            private boolean isField(Cursor cursor) {
                return cursor.getParentTreeCursor().getParentTreeCursor().getValue() instanceof J.ClassDeclaration;
            }
        }.reduce(cu, new LinkedHashSet<>());
    }
}
