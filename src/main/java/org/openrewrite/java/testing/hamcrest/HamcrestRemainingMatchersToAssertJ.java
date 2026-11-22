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
package org.openrewrite.java.testing.hamcrest;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.ShortenFullyQualifiedTypeReferences;
import org.openrewrite.java.UseStaticImport;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.marker.JavaVersion;
import org.openrewrite.java.trait.Scope;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Marker;
import org.openrewrite.marker.Markers;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static java.util.Collections.*;
import static org.openrewrite.java.testing.hamcrest.HamcrestAdapterSource.*;

/**
 * Migrates the Hamcrest matchers that the other recipes of {@code MigrateHamcrestToAssertJ} leave behind, so that no
 * Hamcrest is left once the migration has run.
 * <p>
 * Each step rewrites the outermost matcher of an assertion or assumption. A matcher nested in it becomes a Hamcrest
 * assertion of its own inside a lambda, which the migration rewrites in a later cycle, so that the most specific
 * AssertJ assertion is chosen at every level. Where AssertJ has no counterpart, the assertion calls an adapter in a
 * {@code HamcrestAdapters} class that this recipe generates once per source set, holding only the adapters that the
 * source set needs.
 */
public class HamcrestRemainingMatchersToAssertJ extends ScanningRecipe<HamcrestRemainingMatchersToAssertJ.Accumulator> {

    private static final MethodMatcher ASSERT_THAT = new MethodMatcher("org.hamcrest.MatcherAssert assertThat(..)");
    private static final MethodMatcher MATCHER_ASSUME_THAT = new MethodMatcher("org.hamcrest.junit.MatcherAssume assumeThat(..)");
    private static final MethodMatcher JUNIT_ASSUME_THAT = new MethodMatcher("org.junit.Assume assumeThat(..)");

    /**
     * The matchers inside {@code not(..)} that {@code HamcrestNotMatcherToAssertJ} turns into a negated assertion,
     * provided they take no matcher of their own.
     */
    private static final Set<String> NEGATABLE = new HashSet<>(Arrays.asList(
            "anEmptyMap", "blankString", "containsString", "containsStringIgnoringCase", "empty", "emptyOrNullString",
            "emptyString", "endsWith", "equalTo", "equalToIgnoringCase", "equalToIgnoringWhiteSpace", "hasEntry",
            "hasItem", "hasItems", "hasKey", "hasToString", "hasValue", "in", "instanceOf", "isA",
            "isEmptyOrNullString", "isIn", "matchesPattern", "matchesRegex", "nullValue", "sameInstance", "startsWith"));

    /**
     * The source files that imported Hamcrest in this run, which the migration statically imports AssertJ's
     * {@code assertThat} in.
     */
    private static final String MIGRATED_FILES = HamcrestRemainingMatchersToAssertJ.class.getName() + ".migratedFiles";

    private static final String HAMCREST_ASSERT_THAT = "org.hamcrest.MatcherAssert.assertThat(";

    private static final Set<String> SIZE_MATCHERS = new HashSet<>(Arrays.asList(
            "hasSize", "iterableWithSize", "arrayWithSize", "aMapWithSize", "hasLength"));

    @Getter
    final String displayName = "Migrate the remaining Hamcrest matchers to AssertJ";

    @Getter
    final String description = "Migrate the Hamcrest matchers and assumptions that have no one-to-one AssertJ assertion, " +
            "such as matchers that take other matchers, `both(..).and(..)`, `samePropertyValuesAs(..)` and " +
            "`hasXPath(..)`, so that no Hamcrest is left behind. Where AssertJ has no counterpart, the assertion calls " +
            "an adapter in a generated `HamcrestAdapters` class that keeps the matcher's behavior.";

    /**
     * The matchers nested in a rewritten one become Hamcrest assertions of their own, which the recipes ahead of this
     * one in the migration rewrite in the next cycle.
     */
    @Override
    public boolean causesAnotherCycle() {
        return true;
    }

    public static class Accumulator {
        final Map<String, SourceSet> sourceSets = new LinkedHashMap<>();
    }

    static class SourceSet {
        @Nullable
        Path root;

        final List<Marker> markers = new ArrayList<>();
        final SortedSet<String> packages = new TreeSet<>();
        final SortedSet<String> adapters = new TreeSet<>();

        /**
         * The fully qualified name of a {@code HamcrestAdapters} class this migration generated earlier, if any.
         */
        @Nullable
        String existing;

        boolean generated;

        @Nullable
        UUID sourceSetId;

        @Nullable
        String sourceSetName;

        String packageName() {
            if (existing != null) {
                int dot = existing.lastIndexOf('.');
                return dot < 0 ? "" : existing.substring(0, dot);
            }
            String common = null;
            for (String p : packages) {
                common = common == null ? p : commonPackage(common, p);
            }
            return StringUtils.isNullOrEmpty(common) ? (packages.isEmpty() ? "" : packages.first()) : common;
        }

        String fullyQualifiedName() {
            String p = packageName();
            return p.isEmpty() ? CLASS_NAME : p + "." + CLASS_NAME;
        }

        private static String commonPackage(String a, String b) {
            String[] as = a.split("\\.");
            String[] bs = b.split("\\.");
            StringBuilder common = new StringBuilder();
            for (int i = 0; i < Math.min(as.length, bs.length) && as[i].equals(bs[i]); i++) {
                if (common.length() > 0) {
                    common.append('.');
                }
                common.append(as[i]);
            }
            return common.toString();
        }
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                String key = sourceSetKey(cu);
                if (key == null) {
                    return cu;
                }
                if (isGeneratedAdapters(cu)) {
                    SourceSet sourceSet = acc.sourceSets.computeIfAbsent(key, k -> new SourceSet());
                    sourceSet.existing = cu.getClasses().get(0).getType() == null ? null :
                            cu.getClasses().get(0).getType().getFullyQualifiedName();
                    return cu;
                }
                Set<String> adapters = new JavaIsoVisitor<Set<String>>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Set<String> found) {
                        String adapter = adapterFor(method);
                        if (adapter != null) {
                            found.add(adapter);
                        }
                        return super.visitMethodInvocation(method, found);
                    }
                }.reduce(cu, new TreeSet<>());
                if (!adapters.isEmpty()) {
                    SourceSet sourceSet = acc.sourceSets.computeIfAbsent(key, k -> new SourceSet());
                    sourceSet.adapters.addAll(adapters);
                    String packageName = cu.getPackageDeclaration() == null ? "" :
                            cu.getPackageDeclaration().getExpression().printTrimmed(getCursor());
                    sourceSet.packages.add(packageName);
                    if (sourceSet.root == null) {
                        Path path = cu.getSourcePath();
                        Path root = path.getParent() == null ? Paths.get("") : path.getParent();
                        for (int i = 0; !packageName.isEmpty() && i < packageName.split("\\.").length && root != null; i++) {
                            root = root.getParent();
                        }
                        sourceSet.root = root == null ? Paths.get("") : root;
                        for (Marker marker : cu.getMarkers().getMarkers()) {
                            if (marker instanceof JavaSourceSet) {
                                // the name and id, not the classpath the marker holds
                                sourceSet.sourceSetId = marker.getId();
                                sourceSet.sourceSetName = ((JavaSourceSet) marker).getName();
                            } else if (marker instanceof JavaProject || marker instanceof JavaVersion) {
                                sourceSet.markers.add(marker);
                            }
                        }
                    }
                }
                return cu;
            }
        };
    }

    @Override
    public Collection<? extends SourceFile> generate(Accumulator acc, ExecutionContext ctx) {
        List<SourceFile> generated = new ArrayList<>();
        for (SourceSet sourceSet : acc.sourceSets.values()) {
            if (sourceSet.existing != null || sourceSet.generated || sourceSet.adapters.isEmpty() || sourceSet.root == null) {
                continue;
            }
            sourceSet.generated = true;
            String packageName = sourceSet.packageName();
            Path path = sourceSet.root;
            if (!packageName.isEmpty()) {
                path = path.resolve(packageName.replace('.', '/'));
            }
            Path sourcePath = path.resolve(CLASS_NAME + ".java");
            JavaParser.fromJavaVersion()
                    .classpathFromResources(ctx, "assertj-core-3")
                    .build()
                    .parse(ctx, HamcrestAdapterSource.source(packageName, sourceSet.adapters))
                    .findFirst()
                    .ifPresent(source -> {
                        Markers markers = source.getMarkers();
                        for (Marker marker : sourceSet.markers) {
                            markers = markers.setByType(marker);
                        }
                        if (sourceSet.sourceSetName != null) {
                            markers = markers.setByType(new JavaSourceSet(sourceSet.sourceSetId, sourceSet.sourceSetName,
                                    emptyList(), emptyMap()));
                        }
                        generated.add(source.withSourcePath(sourcePath).withMarkers(markers));
                    });
        }
        return generated;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                if (isGeneratedAdapters(cu)) {
                    return withAdaptersNeeded(cu, acc.sourceSets.get(sourceSetKey(cu)), ctx);
                }
                // the recipes that migrate a matcher called as `Matchers.equalTo(..)` leave the import of `Matchers`
                Set<Path> migrated = ctx.computeMessageIfAbsent(MIGRATED_FILES, k -> new HashSet<>());
                for (J.Import anImport : cu.getImports()) {
                    String name = anImport.getTypeName();
                    if (name.startsWith("org.hamcrest.")) {
                        migrated.add(cu.getSourcePath());
                        String member = anImport.getQualid().getSimpleName();
                        maybeRemoveImport(anImport.isStatic() && !"*".equals(member) ? name + "." + member : name);
                    }
                }
                J.CompilationUnit c = super.visitCompilationUnit(cu, ctx);
                if (migrated.contains(cu.getSourcePath())) {
                    // AssertJ's `assertThat` stays qualified while Hamcrest's is imported
                    doAfterVisit(new UseStaticImport("org.assertj.core.api.Assertions assertThat(..)").getVisitor());
                }
                return c;
            }

            /**
             * Adds the adapters that this run calls to a {@code HamcrestAdapters} class an earlier run generated,
             * regenerating it with the adapters it already had as well.
             */
            private J.CompilationUnit withAdaptersNeeded(J.CompilationUnit cu, @Nullable SourceSet sourceSet, ExecutionContext ctx) {
                if (sourceSet == null) {
                    return cu;
                }
                Set<String> adapters = new TreeSet<>();
                for (Statement statement : cu.getClasses().get(0).getBody().getStatements()) {
                    if (statement instanceof J.MethodDeclaration &&
                        HamcrestAdapterSource.isAdapter(((J.MethodDeclaration) statement).getSimpleName())) {
                        adapters.add(((J.MethodDeclaration) statement).getSimpleName());
                    }
                }
                if (adapters.containsAll(sourceSet.adapters)) {
                    return cu;
                }
                adapters.addAll(sourceSet.adapters);
                String packageName = cu.getPackageDeclaration() == null ? "" :
                        cu.getPackageDeclaration().getExpression().printTrimmed(getCursor());
                return JavaParser.fromJavaVersion()
                        .classpathFromResources(ctx, "assertj-core-3")
                        .build()
                        .parse(ctx, HamcrestAdapterSource.source(packageName, adapters))
                        .findFirst()
                        .map(source -> ((J.CompilationUnit) source).withId(cu.getId())
                                .withSourcePath(cu.getSourcePath())
                                .withMarkers(cu.getMarkers())
                                .withEof(cu.getEof()))
                        .orElse(cu);
            }

            @Override
            public J.@Nullable MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (MATCHER_ASSUME_THAT.matches(mi) || JUNIT_ASSUME_THAT.matches(mi)) {
                    return assumption(mi, ctx);
                }
                if (!ASSERT_THAT.matches(mi)) {
                    return mi;
                }
                List<Expression> args = mi.getArguments();
                if (args.size() < 2 || !(args.get(args.size() - 1) instanceof J.MethodInvocation) ||
                    !isHamcrestMatcher(args.get(args.size() - 1))) {
                    return mi;
                }
                Expression reason = args.size() == 3 ? args.get(0) : null;
                Expression actual = args.get(args.size() - 2);
                J.MethodInvocation matcher = (J.MethodInvocation) args.get(args.size() - 1);
                Rewrite rewrite = rewrite(actual, reason, matcher);
                if (rewrite == null) {
                    return mi;
                }
                if (rewrite.remove) {
                    //noinspection DataFlowIssue
                    return null;
                }
                String adapters = null;
                if (!rewrite.adapters.isEmpty()) {
                    SourceSet sourceSet = acc.sourceSets.get(sourceSetKey(getCursor().firstEnclosingOrThrow(J.CompilationUnit.class)));
                    if (sourceSet == null) {
                        return mi;
                    }
                    adapters = sourceSet.fullyQualifiedName();
                }
                return apply(mi, "assertThat", "org.assertj.core.api.Assertions", actual, reason, rewrite, adapters, matcher, ctx);
            }

            private J.MethodInvocation assumption(J.MethodInvocation mi, ExecutionContext ctx) {
                List<Expression> args = mi.getArguments();
                if (args.size() < 2) {
                    return mi;
                }
                Expression reason = args.size() == 3 ? args.get(0) : null;
                Expression actual = args.get(args.size() - 2);
                Expression matcher = args.get(args.size() - 1);
                String arg = Scope.at(getCursor()).freeName("arg");
                Rewrite rewrite = new Rewrite(".satisfies(" + arg + " -> " + HAMCREST_ASSERT_THAT + arg + ", #{any()}))", singletonList(matcher));
                maybeRemoveImport("org.hamcrest.junit.MatcherAssume");
                maybeRemoveImport("org.hamcrest.junit.MatcherAssume.assumeThat");
                maybeRemoveImport("org.junit.Assume.assumeThat");
                maybeRemoveImport("org.junit.Assume");
                return apply(mi, "assumeThat", "org.assertj.core.api.Assumptions", actual, reason, rewrite, null, null, ctx);
            }

            private J.MethodInvocation apply(J.MethodInvocation mi, String root, String rootType, Expression actual,
                                             @Nullable Expression reason, Rewrite rewrite, @Nullable String adapters,
                                             J.@Nullable MethodInvocation matcher, ExecutionContext ctx) {
                JavaParser.Builder<?, ?> parser = JavaParser.fromJavaVersion()
                        .classpathFromResources(ctx, "assertj-core-3", "hamcrest-3");
                if (adapters != null) {
                    int dot = adapters.lastIndexOf('.');
                    parser = parser.dependsOn(HamcrestAdapterSource.source(dot < 0 ? "" : adapters.substring(0, dot), emptyList()));
                }
                String code = root + "(#{any()})" + (reason == null ? "" : ".as(#{any(java.lang.String)})") +
                              rewrite.suffix.replace("HamcrestAdapters.", adapters == null ? "HamcrestAdapters." : CLASS_NAME + ".");
                JavaTemplate.Builder template = JavaTemplate.builder(code)
                        .contextSensitive()
                        .javaParser(parser)
                        .staticImports(rootType + "." + root);
                if (adapters != null && adapters.contains(".")) {
                    template = template.imports(adapters);
                    maybeAddImport(adapters);
                }
                List<Object> templateArgs = new ArrayList<>();
                templateArgs.add(actual);
                if (reason != null) {
                    templateArgs.add(reason);
                }
                templateArgs.addAll(rewrite.args);

                maybeAddImport(rootType, root);
                if (matcher != null && matcher.getMethodType() != null) {
                    String owner = matcher.getMethodType().getDeclaringType().getFullyQualifiedName();
                    maybeRemoveImport(owner + "." + matcher.getSimpleName());
                    maybeRemoveImport(owner);
                    maybeRemoveImport("org.hamcrest.Matchers." + matcher.getSimpleName());
                    maybeRemoveImport("org.hamcrest.CoreMatchers." + matcher.getSimpleName());
                }
                maybeRemoveImport("org.hamcrest.MatcherAssert.assertThat");
                maybeRemoveImport("org.hamcrest.MatcherAssert");
                J.MethodInvocation replaced = template.build().apply(getCursor(), mi.getCoordinates().replace(), templateArgs.toArray());
                doAfterVisit(ShortenFullyQualifiedTypeReferences.modifyOnly(replaced));
                return replaced;
            }

            private @Nullable Rewrite rewrite(Expression actual, @Nullable Expression reason, J.MethodInvocation matcher) {
                String name = matcher.getSimpleName();
                List<Expression> args = arguments(matcher);
                boolean takesMatcher = HamcrestMatcherToAssertJ.takesMatcher(matcher);
                Scope scope = Scope.at(getCursor());
                String arg = scope.freeName("arg");
                switch (name) {
                    case "emptyIterable":
                    case "emptyIterableOf":
                    case "emptyCollectionOf":
                    case "emptyOptional":
                        return new Rewrite(".isEmpty()", emptyList());
                    case "notANumber":
                        return new Rewrite(".isNaN()", emptyList());
                    case "anExistingFile":
                        return new Rewrite(".isFile()", emptyList());
                    case "anExistingDirectory":
                        return new Rewrite(".isDirectory()", emptyList());
                    case "anExistingFileOrDirectory":
                        return new Rewrite(".exists()", emptyList());
                    case "aReadableFile":
                        return new Rewrite(".canRead()", emptyList());
                    case "aWritableFile":
                        return new Rewrite(".canWrite()", emptyList());
                    case "blankOrNullString":
                        return new Rewrite(".satisfiesAnyOf(" + arg + " -> assertThat(" + arg + ").isNull(), " +
                                           arg + " -> assertThat(" + arg + ").isBlank())", emptyList());
                    case "oneOf":
                    case "isOneOf":
                        return values(".isIn", args);
                    case "theInstance":
                        return values(".isSameAs", args);
                    case "equalToObject":
                        return values(".isEqualTo", args);
                    case "equalToCompressingWhiteSpace":
                        return values(".isEqualToNormalizingWhitespace", args);
                    case "typeCompatibleWith":
                        return values(".isAssignableTo", args);
                    case "any":
                        return values(".isInstanceOf", args);
                    case "stringContainsInOrder":
                        return values(".containsSubsequence", args);
                    case "anything":
                        return getCursor().getParentTreeCursor().getValue() instanceof J.Block ? Rewrite.REMOVE :
                                new Rewrite(".satisfies(" + arg + " -> {\n})", emptyList());
                    case "optionalWithValue":
                        if (args.isEmpty()) {
                            return new Rewrite(".isPresent()", emptyList());
                        }
                        return takesMatcher ? nested(".hasValueSatisfying", arg, args) : values(".contains", args);
                    case "aFileWithSize":
                        return takesMatcher ? feature(arg, arg + ".length()", args.get(0)) : values(".hasSize", args);
                    case "aFileNamed":
                        return feature(arg, arg + ".getName()", args.get(0));
                    case "aFileWithCanonicalPath":
                        return feature(arg, arg + ".getCanonicalPath()", args.get(0));
                    case "aFileWithAbsolutePath":
                        return feature(arg, arg + ".getAbsolutePath()", args.get(0));
                    case "containsInRelativeOrder":
                        if (!takesMatcher) {
                            return values(".containsSubsequence", args);
                        }
                        String element = elementType(actual.getType());
                        return new Rewrite(".is(HamcrestAdapters." + (element == null ? "" : "<" + element + ">") +
                                           IN_RELATIVE_ORDER + "(" + lambdas(arg, args.size()) + "))", args,
                                singleton(IN_RELATIVE_ORDER));
                    case "contains":
                        if (noMatchers(args)) {
                            return elementValues(".containsExactly", actual, args);
                        }
                        return takesMatcher && allMatchers(args) ? nested(".satisfiesExactly", arg, args) : null;
                    case "array":
                    case "arrayContaining":
                        return takesMatcher && allMatchers(args) ? nested(".satisfiesExactly", arg, args) : null;
                    case "containsInAnyOrder":
                        if (noMatchers(args)) {
                            return elementValues(".containsExactlyInAnyOrder", actual, args);
                        }
                        return takesMatcher && allMatchers(args) ? nested(".satisfiesExactlyInAnyOrder", arg, args) : null;
                    case "arrayContainingInAnyOrder":
                        return takesMatcher && allMatchers(args) ? nested(".satisfiesExactlyInAnyOrder", arg, args) : null;
                    case "hasItemInArray":
                        return takesMatcher ? nested(".anySatisfy", arg, args) : null;
                    case "hasItems":
                        if (!takesMatcher || !allMatchers(args)) {
                            return null;
                        }
                        StringBuilder chain = new StringBuilder();
                        for (int i = 0; i < args.size(); i++) {
                            chain.append(".anySatisfy(").append(arg).append(" -> ").append(HAMCREST_ASSERT_THAT).append(arg).append(", #{any()}))");
                        }
                        return new Rewrite(chain.toString(), args);
                    case "hasToString":
                        return takesMatcher ? feature(arg, arg + ".toString()", args.get(0)) : null;
                    case "hasEntry": {
                        if (!takesMatcher || args.size() != 2) {
                            return null;
                        }
                        String key = scope.freeName("key");
                        String value = scope.freeName("value", n -> n.equals(key));
                        return new Rewrite(".anySatisfy((" + key + ", " + value + ") -> {\n" +
                                           HAMCREST_ASSERT_THAT + key + ", #{any()});\n" +
                                           HAMCREST_ASSERT_THAT + value + ", #{any()});\n" +
                                           "})", args);
                    }
                    case "hasKey":
                    case "hasValue": {
                        if (!takesMatcher) {
                            return null;
                        }
                        String key = scope.freeName("key");
                        String value = scope.freeName("value", n -> n.equals(key));
                        return new Rewrite(".anySatisfy((" + key + ", " + value + ") -> " + HAMCREST_ASSERT_THAT +
                                           ("hasKey".equals(name) ? key : value) + ", #{any()}))", args);
                    }
                    case "describedAs": {
                        if (args.size() < 2) {
                            return null;
                        }
                        String satisfies = ".satisfies(" + arg + " -> " + HAMCREST_ASSERT_THAT + arg + ", #{any()}))";
                        if (reason != null) {
                            return new Rewrite(satisfies, singletonList(args.get(1)));
                        }
                        if (args.size() == 2) {
                            return new Rewrite(".as(#{any(java.lang.String)})" + satisfies, Arrays.asList(args.get(0), args.get(1)));
                        }
                        List<Expression> descriptionArgs = new ArrayList<>();
                        descriptionArgs.add(args.get(0));
                        descriptionArgs.addAll(args.subList(2, args.size()));
                        descriptionArgs.add(args.get(1));
                        return new Rewrite(".as(HamcrestAdapters." + DESCRIPTION + "(#{any(java.lang.String)}" +
                                           repeat(", #{any()}", args.size() - 2) + "))" + satisfies,
                                descriptionArgs, singleton(DESCRIPTION));
                    }
                    case "samePropertyValuesAs":
                        return new Rewrite(".is(HamcrestAdapters.<Object>" + SAME_PROPERTY_VALUES_AS + "(" +
                                           placeholders(args.size()) + "))", args, singleton(SAME_PROPERTY_VALUES_AS));
                    case "hasXPath": {
                        Expression valueMatcher = isMatcher(args.get(args.size() - 1)) ? args.get(args.size() - 1) : null;
                        List<Expression> xpathArgs = new ArrayList<>(args.subList(0, valueMatcher == null ? args.size() : args.size() - 1));
                        String namespaces = xpathArgs.size() == 2 ? ", #{any()}" : ", null";
                        if (valueMatcher == null) {
                            return new Rewrite(".is(HamcrestAdapters." + HAS_XPATH + "(#{any(java.lang.String)}" + namespaces + "))",
                                    xpathArgs, singleton(HAS_XPATH));
                        }
                        List<Expression> all = new ArrayList<>(xpathArgs);
                        all.add(valueMatcher);
                        return new Rewrite(".satisfies(" + arg + " -> " + HAMCREST_ASSERT_THAT + "HamcrestAdapters." + EVALUATE_XPATH + "(" + arg +
                                           ", #{any(java.lang.String)}" + namespaces + "), #{any()}))", all, singleton(EVALUATE_XPATH));
                    }
                    case "eventFrom":
                        return new Rewrite(".is(HamcrestAdapters." + EVENT_FROM + "(" +
                                           (args.size() == 1 ? "java.util.EventObject.class, " : "#{any()}, ") + "#{any()}))",
                                args, singleton(EVENT_FROM));
                    case "hasProperty": {
                        if (args.size() != 2 || isValueMatcher(args.get(1))) {
                            return null;
                        }
                        String valueType = matchedType(args.get(1));
                        return new Rewrite(".satisfies(" + arg + " -> " + HAMCREST_ASSERT_THAT + "HamcrestAdapters." +
                                           (valueType == null ? "" : "<" + valueType + ">") + PROPERTY + "(" + arg +
                                           ", #{any(java.lang.String)}), #{any()}))", args, singleton(PROPERTY));
                    }
                    case "not": {
                        if (args.size() != 1) {
                            return null;
                        }
                        Expression negated = args.get(0);
                        if (!isMatcher(negated)) {
                            return values(".isNotEqualTo", args);
                        }
                        if (!needsSatisfying(negated)) {
                            return null;
                        }
                        // the witness keeps the lambda's parameter the actual's type, not a capture of it that the
                        // negated matcher may not accept
                        String actualType = typeName(actual.getType());
                        return new Rewrite(".isNot(HamcrestAdapters." + (actualType == null ? "" : "<" + actualType + ">") +
                                           SATISFYING + "(" + arg + " -> " + HAMCREST_ASSERT_THAT + arg +
                                           ", #{any()})))", args, singleton(SATISFYING));
                    }
                    case "and":
                    case "or": {
                        List<Expression> operands = combined(matcher, name);
                        if (operands == null) {
                            return null;
                        }
                        return nested("and".equals(name) ? ".satisfies" : ".satisfiesAnyOf", arg, operands);
                    }
                    default:
                        if (SIZE_MATCHERS.contains(name) && takesMatcher && args.size() == 1) {
                            return size(actual, arg, args.get(0));
                        }
                        if ("iterableWithSize".equals(name)) {
                            return values(".hasSize", args);
                        }
                        return null;
                }
            }

            private @Nullable Rewrite size(Expression actual, String arg, Expression sizeMatcher) {
                JavaType type = actual.getType();
                if (type instanceof JavaType.Array) {
                    return feature(arg, arg + ".length", sizeMatcher);
                }
                if (TypeUtils.isAssignableTo("java.lang.CharSequence", type)) {
                    return feature(arg, arg + ".length()", sizeMatcher);
                }
                if (TypeUtils.isAssignableTo("java.util.Collection", type) || TypeUtils.isAssignableTo("java.util.Map", type)) {
                    return feature(arg, arg + ".size()", sizeMatcher);
                }
                if (TypeUtils.isAssignableTo("java.lang.Iterable", type)) {
                    return new Rewrite(".size().satisfies(" + arg + " -> " + HAMCREST_ASSERT_THAT + arg + ", #{any()}))", singletonList(sizeMatcher));
                }
                return null;
            }
        };
    }

    private static Rewrite values(String assertion, List<Expression> args) {
        return new Rewrite(assertion + "(" + placeholders(args.size()) + ")", args);
    }

    /**
     * The elements an iterable is expected to hold, which Hamcrest also took as one array of a wider type than the
     * iterable's elements, such as {@code contains(names.toArray())}, which AssertJ's assertion of the iterable does
     * not take until it asserts on an iterable of objects.
     */
    private static Rewrite elementValues(String assertion, Expression actual, List<Expression> args) {
        if (args.size() == 1 && args.get(0).getType() instanceof JavaType.Array) {
            JavaType component = ((JavaType.Array) args.get(0).getType()).getElemType();
            JavaType.Parameterized iterable = TypeUtils.asParameterized(actual.getType());
            JavaType element = iterable != null && iterable.getTypeParameters().size() == 1 ?
                    iterable.getTypeParameters().get(0) : null;
            if (element == null || !TypeUtils.isAssignableTo(element, component)) {
                return new Rewrite(".asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.ITERABLE)" + assertion +
                                   "(#{anyArray(java.lang.Object)})", args);
            }
        }
        return values(assertion, args);
    }

    private static boolean noMatchers(List<Expression> args) {
        for (Expression arg : args) {
            if (isMatcher(arg) || arg.getType() instanceof JavaType.Array &&
                                  TypeUtils.isAssignableTo("org.hamcrest.Matcher", ((JavaType.Array) arg.getType()).getElemType())) {
                return false;
            }
        }
        return !args.isEmpty();
    }

    private static Rewrite nested(String assertion, String arg, List<Expression> matchers) {
        return new Rewrite(assertion + "(" + lambdas(arg, matchers.size()) + ")", matchers);
    }

    private static Rewrite feature(String arg, String feature, Expression featureMatcher) {
        return new Rewrite(".satisfies(" + arg + " -> " + HAMCREST_ASSERT_THAT + feature + ", #{any()}))", singletonList(featureMatcher));
    }

    private static String lambdas(String arg, int count) {
        StringJoiner joiner = new StringJoiner(",\n");
        for (int i = 0; i < count; i++) {
            joiner.add(arg + " -> " + HAMCREST_ASSERT_THAT + arg + ", #{any()})");
        }
        return joiner.toString();
    }

    private static String placeholders(int count) {
        StringJoiner joiner = new StringJoiner(", ");
        for (int i = 0; i < count; i++) {
            joiner.add("#{any()}");
        }
        return joiner.toString();
    }

    private static String repeat(String s, int count) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < count; i++) {
            b.append(s);
        }
        return b.toString();
    }

    private static Set<String> singleton(String adapter) {
        return Collections.singleton(adapter);
    }

    private static List<Expression> arguments(J.MethodInvocation method) {
        List<Expression> args = new ArrayList<>();
        for (Expression arg : method.getArguments()) {
            if (!(arg instanceof J.Empty)) {
                args.add(arg);
            }
        }
        return args;
    }

    /**
     * The operands of {@code both(a).and(b).and(c)} or {@code either(a).or(b)}, or null for a chain that mixes the two.
     */
    private static @Nullable List<Expression> combined(J.MethodInvocation matcher, String operator) {
        LinkedList<Expression> operands = new LinkedList<>();
        Expression current = matcher;
        while (current instanceof J.MethodInvocation) {
            J.MethodInvocation call = (J.MethodInvocation) current;
            if (call.getArguments().size() != 1 || !isHamcrestMatcher(call)) {
                return null;
            }
            String name = call.getSimpleName();
            if (name.equals(operator)) {
                operands.addFirst(call.getArguments().get(0));
                current = call.getSelect();
            } else if (("and".equals(operator) && "both".equals(name)) || ("or".equals(operator) && "either".equals(name))) {
                operands.addFirst(call.getArguments().get(0));
                return operands;
            } else {
                return null;
            }
        }
        return null;
    }

    /**
     * The adapter a matcher in the original source will need, which the scanner collects to generate only those.
     */
    static @Nullable String adapterFor(J.MethodInvocation method) {
        if (!isHamcrestMatcher(method)) {
            return null;
        }
        List<Expression> args = arguments(method);
        switch (method.getSimpleName()) {
            case "samePropertyValuesAs":
                return SAME_PROPERTY_VALUES_AS;
            case "hasXPath":
                return !args.isEmpty() && isMatcher(args.get(args.size() - 1)) ? EVALUATE_XPATH : HAS_XPATH;
            case "eventFrom":
                return EVENT_FROM;
            case "hasProperty":
                return args.size() == 2 && !isValueMatcher(args.get(1)) ? PROPERTY : null;
            case "describedAs":
                return args.size() > 2 ? DESCRIPTION : null;
            case "containsInRelativeOrder":
                return HamcrestMatcherToAssertJ.takesMatcher(method) ? IN_RELATIVE_ORDER : null;
            case "not":
                return args.size() == 1 && isMatcher(args.get(0)) && needsSatisfying(args.get(0)) ? SATISFYING : null;
            default:
                return null;
        }
    }

    /**
     * Whether negating the matcher needs the {@code satisfying} adapter, as no recipe negates its assertion.
     */
    private static boolean needsSatisfying(Expression negated) {
        if (!(negated instanceof J.MethodInvocation) || !isHamcrestMatcher(negated)) {
            // a matcher of the project's own, which a later migration of custom matchers takes on
            return false;
        }
        J.MethodInvocation inner = (J.MethodInvocation) negated;
        return !NEGATABLE.contains(inner.getSimpleName()) || HamcrestMatcherToAssertJ.takesMatcher(inner);
    }

    private static boolean isValueMatcher(Expression matcher) {
        if (!(matcher instanceof J.MethodInvocation) || !isHamcrestMatcher(matcher)) {
            return false;
        }
        String name = ((J.MethodInvocation) matcher).getSimpleName();
        return ("equalTo".equals(name) || "is".equals(name)) && !HamcrestMatcherToAssertJ.takesMatcher((J.MethodInvocation) matcher);
    }

    static boolean isHamcrestMatcher(Expression expression) {
        if (!(expression instanceof J.MethodInvocation)) {
            return false;
        }
        JavaType.Method type = ((J.MethodInvocation) expression).getMethodType();
        if (type == null) {
            return false;
        }
        String owner = type.getDeclaringType().getFullyQualifiedName();
        return owner.startsWith("org.hamcrest.") && !owner.startsWith("org.hamcrest.MatcherAssert") &&
               !owner.startsWith("org.hamcrest.junit.");
    }

    private static boolean isMatcher(Expression expression) {
        return TypeUtils.isAssignableTo("org.hamcrest.Matcher", expression.getType());
    }

    private static boolean allMatchers(List<Expression> args) {
        for (Expression arg : args) {
            if (!isMatcher(arg)) {
                return false;
            }
        }
        return !args.isEmpty();
    }

    /**
     * The type a matcher matches, written as source, when it is a type that can be named.
     */
    private static @Nullable String matchedType(Expression matcher) {
        JavaType.Parameterized matcherType = TypeUtils.asParameterized(matcher.getType());
        if (matcherType == null || matcherType.getTypeParameters().size() != 1) {
            return null;
        }
        return nameable(matcherType.getTypeParameters().get(0));
    }

    private static @Nullable String elementType(@Nullable JavaType type) {
        JavaType.Parameterized parameterized = TypeUtils.asParameterized(type);
        if (parameterized == null || parameterized.getTypeParameters().size() != 1 ||
            !TypeUtils.isAssignableTo("java.util.Collection", parameterized)) {
            return null;
        }
        return nameable(parameterized.getTypeParameters().get(0));
    }

    private static @Nullable String nameable(@Nullable JavaType type) {
        if (type instanceof JavaType.Class && !(type instanceof JavaType.Parameterized)) {
            String name = ((JavaType.Class) type).getFullyQualifiedName();
            // `java.lang` needs no import, so the name stays as short as the source would write it
            return name.startsWith("java.lang.") && name.indexOf('.', "java.lang.".length()) < 0 ?
                    name.substring("java.lang.".length()) : name.replace('$', '.');
        }
        return null;
    }

    /**
     * The type as source code can name it, boxing a primitive, or null when it has no such name, such as a capture
     * or a type variable.
     */
    private static @Nullable String typeName(@Nullable JavaType type) {
        if (type instanceof JavaType.Primitive) {
            JavaType.Primitive primitive = (JavaType.Primitive) type;
            return primitive == JavaType.Primitive.String ? "String" :
                    primitive.isNumeric() || primitive == JavaType.Primitive.Boolean || primitive == JavaType.Primitive.Char ?
                            boxed(primitive) : null;
        }
        if (type instanceof JavaType.Array) {
            String element = typeName(((JavaType.Array) type).getElemType());
            return element == null ? null : element + "[]";
        }
        if (type instanceof JavaType.Parameterized) {
            JavaType.Parameterized parameterized = (JavaType.Parameterized) type;
            StringJoiner parameters = new StringJoiner(", ", "<", ">");
            for (JavaType parameter : parameterized.getTypeParameters()) {
                String name = typeName(parameter);
                if (name == null) {
                    return null;
                }
                parameters.add(name);
            }
            return parameterized.getFullyQualifiedName().replace('$', '.') + parameters;
        }
        return nameable(type);
    }

    private static String boxed(JavaType.Primitive primitive) {
        switch (primitive) {
            case Boolean:
                return "Boolean";
            case Byte:
                return "Byte";
            case Char:
                return "Character";
            case Double:
                return "Double";
            case Float:
                return "Float";
            case Int:
                return "Integer";
            case Long:
                return "Long";
            default:
                return "Short";
        }
    }

    static @Nullable String sourceSetKey(J.CompilationUnit cu) {
        JavaSourceSet sourceSet = cu.getMarkers().findFirst(JavaSourceSet.class).orElse(null);
        JavaProject project = cu.getMarkers().findFirst(JavaProject.class).orElse(null);
        if (sourceSet == null) {
            return "";
        }
        return (project == null ? "" : project.getProjectName()) + ":" + sourceSet.getName();
    }

    static boolean isGeneratedAdapters(J.CompilationUnit cu) {
        return cu.getClasses().size() == 1 && CLASS_NAME.equals(cu.getClasses().get(0).getSimpleName()) &&
               cu.printAll().contains(MARKER);
    }

    static class Rewrite {
        static final Rewrite REMOVE = new Rewrite("", emptyList(), emptySet(), true);

        final String suffix;
        final List<? extends Expression> args;
        final Set<String> adapters;
        final boolean remove;

        Rewrite(String suffix, List<? extends Expression> args) {
            this(suffix, args, emptySet(), false);
        }

        Rewrite(String suffix, List<? extends Expression> args, Set<String> adapters) {
            this(suffix, args, adapters, false);
        }

        private Rewrite(String suffix, List<? extends Expression> args, Set<String> adapters, boolean remove) {
            this.suffix = suffix;
            this.args = args;
            this.adapters = adapters;
            this.remove = remove;
        }
    }
}
