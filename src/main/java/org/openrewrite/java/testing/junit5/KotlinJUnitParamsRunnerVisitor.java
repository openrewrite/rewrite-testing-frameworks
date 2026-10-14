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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.kotlin.KotlinTemplate;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.marker.Markers;

import java.util.*;

import static java.util.stream.Collectors.joining;
import static org.openrewrite.Tree.randomId;

class KotlinJUnitParamsRunnerVisitor extends KotlinIsoVisitor<ExecutionContext> {
    private static final String PARAMETERS = "junitparams.Parameters";
    private static final String NAMED_PARAMETERS = "junitparams.NamedParameters";
    private static final String CASE_NAME = "junitparams.naming.TestCaseName";
    private J.ClassDeclaration companionPrototype;

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration clazz, ExecutionContext ctx) {
        J.ClassDeclaration cd = super.visitClassDeclaration(clazz, ctx);
        if (cd.getType() == null) {
            return cd;
        }
        List<J.MethodDeclaration> methods = new ArrayList<>();
        collectMethods(cd, methods);
        Map<String, String> namedProviders = new HashMap<>();
        for (J.MethodDeclaration method : methods) {
            for (J.Annotation annotation : method.getLeadingAnnotations()) {
                if (TypeUtils.isOfClassType(annotation.getType(), NAMED_PARAMETERS)) {
                    String name = stringArgument(annotation, "value");
                    if (name != null) {
                        namedProviders.put(name, method.getSimpleName());
                    }
                }
            }
        }
        Set<String> providers = new LinkedHashSet<>();
        boolean[] migrated = {false};
        Cursor classCursor = updateCursor(cd);
        cd = cd.withBody(cd.getBody().withStatements(ListUtils.map(cd.getBody().getStatements(), statement -> {
            if (!(statement instanceof J.MethodDeclaration)) {
                return statement;
            }
            J.MethodDeclaration method = (J.MethodDeclaration) statement;
            J.Annotation parameters = method.getLeadingAnnotations().stream()
                    .filter(a -> TypeUtils.isOfClassType(a.getType(), PARAMETERS)).findFirst().orElse(null);
            if (parameters == null) {
                return method;
            }
            String provider = stringArgument(parameters, "method");
            String named = stringArgument(parameters, "named");
            if (named != null) {
                provider = namedProviders.get(named);
                if (provider == null) {
                    return unsupported(method);
                }
            }
            String source;
            if (provider != null) {
                List<String> names = Arrays.asList(provider.split("\\s*,\\s*"));
                providers.addAll(names);
                source = "@MethodSource(" + String.join(", ", ListUtils.map(names, name -> "\"" + name + "\"")) + ")";
                maybeAddImport("org.junit.jupiter.params.provider.MethodSource");
            } else if (parameters.getArguments() == null || parameters.getArguments().isEmpty() ||
                    parameters.getArguments().get(0) instanceof J.Empty) {
                String conventional = "parametersFor" + Character.toUpperCase(method.getSimpleName().charAt(0)) +
                        method.getSimpleName().substring(1);
                if (methods.stream().noneMatch(m -> conventional.equals(m.getSimpleName()))) {
                    return method;
                }
                providers.add(conventional);
                source = "@MethodSource(\"" + conventional + "\")";
                maybeAddImport("org.junit.jupiter.params.provider.MethodSource");
            } else if (isCsv(parameters) && method.getParameters().stream().noneMatch(parameter ->
                    parameter instanceof J.VariableDeclarations &&
                            ((J.VariableDeclarations) parameter).getLeadingAnnotations().stream().anyMatch(a ->
                                    TypeUtils.isOfClassType(a.getType(), "junitparams.converters.Param")))) {
                source = "@CsvSource(" + parameters.getArguments().stream()
                        .map(this::csvArgument).collect(joining(", ")) + ")";
                maybeAddImport("org.junit.jupiter.params.provider.CsvSource");
            } else {
                return unsupported(method);
            }
            migrated[0] = true;
            Cursor methodCursor = new Cursor(classCursor, method);
            String name = method.getLeadingAnnotations().stream().filter(a -> TypeUtils.isOfClassType(a.getType(), CASE_NAME))
                    .map(a -> stringArgument(a, "value")).filter(Objects::nonNull).findFirst().orElse(null);
            String test = name == null ? "@ParameterizedTest" :
                    "@ParameterizedTest(name = \"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\")";
            String parameterSource = source;
            method = method.withLeadingAnnotations(ListUtils.map(method.getLeadingAnnotations(), annotation -> {
                if (TypeUtils.isOfClassType(annotation.getType(), CASE_NAME)) {
                    return null;
                }
                String replacement = TypeUtils.isOfClassType(annotation.getType(), PARAMETERS) ? parameterSource :
                        TypeUtils.isOfClassType(annotation.getType(), "org.junit.Test") ||
                                TypeUtils.isOfClassType(annotation.getType(), "org.junit.jupiter.api.Test") ? test : null;
                return replacement == null ? annotation : KotlinTemplate.builder(replacement)
                        .imports("org.junit.jupiter.params.ParameterizedTest", "org.junit.jupiter.params.provider.MethodSource",
                                "org.junit.jupiter.params.provider.CsvSource")
                        .parser(KotlinParser.builder().classpathFromResources(ctx, "junit-jupiter-params-5", "junit-jupiter-api-5"))
                        .build().apply(new Cursor(methodCursor, annotation), annotation.getCoordinates().replace());
            }));
            return method;
        })));
        if (!migrated[0]) {
            return cd;
        }
        cd = cd.withLeadingAnnotations(ListUtils.map(cd.getLeadingAnnotations(), annotation -> {
            if (TypeUtils.isOfClassType(annotation.getType(), "org.junit.runner.RunWith") && annotation.getArguments() != null &&
                    annotation.getArguments().stream().anyMatch(argument -> argument instanceof J.MemberReference &&
                            TypeUtils.isOfClassType(((J.MemberReference) argument).getContaining().getType(), "junitparams.JUnitParamsRunner"))) {
                return null;
            }
            return annotation;
        }));
        addProviderDependencies(methods, providers, cd.getType());
        List<J.MethodDeclaration> moved = new ArrayList<>();
        cd = cd.withBody(cd.getBody().withStatements(ListUtils.map(cd.getBody().getStatements(), statement -> {
            if (statement instanceof J.MethodDeclaration && providers.contains(((J.MethodDeclaration) statement).getSimpleName())) {
                moved.add(staticProvider((J.MethodDeclaration) statement));
                return null;
            }
            if (statement instanceof J.ClassDeclaration && isCompanion((J.ClassDeclaration) statement)) {
                J.ClassDeclaration companion = (J.ClassDeclaration) statement;
                return companion.withBody(companion.getBody().withStatements(ListUtils.map(companion.getBody().getStatements(),
                        member -> member instanceof J.MethodDeclaration && providers.contains(((J.MethodDeclaration) member).getSimpleName()) ?
                                staticProvider((J.MethodDeclaration) member) : member)));
            }
            return statement;
        })));
        if (!moved.isEmpty()) {
            J.ClassDeclaration companion = cd.getBody().getStatements().stream()
                    .filter(s -> s instanceof J.ClassDeclaration && isCompanion((J.ClassDeclaration) s))
                    .map(J.ClassDeclaration.class::cast).findFirst().orElse(null);
            if (companion == null) {
                if (companionPrototype == null) {
                    K.CompilationUnit parsed = (K.CompilationUnit) KotlinParser.builder().build()
                            .parse(ctx, "class Placeholder { companion object {} }").findFirst().orElseThrow(IllegalStateException::new);
                    companionPrototype = (J.ClassDeclaration) ((J.ClassDeclaration) parsed.getStatements().get(0)).getBody().getStatements().get(0);
                }
                companion = (J.ClassDeclaration) new KotlinIsoVisitor<ExecutionContext>() {
                    @Override
                    public J preVisit(J tree, ExecutionContext ctx) {
                        return tree.withId(randomId());
                    }
                }.visitNonNull(companionPrototype, ctx);
                JavaType.FullyQualified companionType = JavaType.ShallowClass.build(cd.getType().getFullyQualifiedName() + "$Companion");
                companion = companion.withType(companionType).withName(companion.getName().withType(companionType))
                        .withPrefix(Space.format("\n\n    "));
                cd = cd.withBody(cd.getBody().withStatements(ListUtils.concat(cd.getBody().getStatements(), companion)));
            }
            J.ClassDeclaration target = companion;
            List<J.MethodDeclaration> movedMethods = ListUtils.map(moved, method -> {
                JavaType.Method type = method.getMethodType();
                if (type == null) {
                    return method;
                }
                type = type.withDeclaringType(target.getType());
                return method.withMethodType(type).withName(method.getName().withType(type));
            });
            cd = cd.withBody(cd.getBody().withStatements(ListUtils.map(cd.getBody().getStatements(), statement ->
                    statement == target ? target.withBody(target.getBody().withStatements(
                            ListUtils.concatAll(target.getBody().getStatements(), movedMethods))) : statement)));
        }
        maybeAddImport("org.junit.jupiter.params.ParameterizedTest");
        for (String type : Arrays.asList("org.junit.Test", "org.junit.jupiter.api.Test", "org.junit.runner.RunWith",
                "junitparams.JUnitParamsRunner", PARAMETERS, NAMED_PARAMETERS, CASE_NAME)) {
            maybeRemoveImport(type);
        }
        return KotlinJUnit5Support.normalizeCompanions(maybeAutoFormat(clazz, cd, ctx));
    }

    private static void collectMethods(J.ClassDeclaration cd, List<J.MethodDeclaration> methods) {
        for (Statement statement : cd.getBody().getStatements()) {
            if (statement instanceof J.MethodDeclaration) {
                methods.add((J.MethodDeclaration) statement);
            } else if (statement instanceof J.ClassDeclaration && isCompanion((J.ClassDeclaration) statement)) {
                collectMethods((J.ClassDeclaration) statement, methods);
            }
        }
    }

    private static boolean isCompanion(J.ClassDeclaration cd) {
        return cd.getModifiers().stream().anyMatch(modifier -> "companion".equals(modifier.getKeyword()));
    }

    private static @Nullable String stringArgument(J.Annotation annotation, String name) {
        if (annotation.getArguments() == null) {
            return null;
        }
        for (Expression argument : annotation.getArguments()) {
            Expression value = argument;
            if (argument instanceof J.Assignment) {
                if (!name.equals(((J.Assignment) argument).getVariable().toString())) {
                    continue;
                }
                value = ((J.Assignment) argument).getAssignment();
            } else if (!"value".equals(name)) {
                continue;
            }
            if (value instanceof J.Literal && ((J.Literal) value).getValue() instanceof String) {
                return (String) ((J.Literal) value).getValue();
            }
        }
        return null;
    }

    private static boolean isCsv(J.Annotation parameters) {
        return parameters.getArguments() != null && parameters.getArguments().stream().allMatch(argument -> {
            if (argument instanceof J.Assignment && !"value".equals(((J.Assignment) argument).getVariable().toString())) {
                return false;
            }
            Expression value = argument instanceof J.Assignment ? ((J.Assignment) argument).getAssignment() : argument;
            if (value instanceof K.ListLiteral) {
                return ((K.ListLiteral) value).getElements().stream().allMatch(element ->
                        element instanceof J.Literal && ((J.Literal) element).getValue() instanceof String);
            }
            if (value instanceof J.MethodInvocation && "arrayOf".equals(((J.MethodInvocation) value).getSimpleName())) {
                return ((J.MethodInvocation) value).getArguments().stream().allMatch(element ->
                        element instanceof J.Literal && ((J.Literal) element).getValue() instanceof String);
            }
            return value instanceof J.Literal && ((J.Literal) value).getValue() instanceof String;
        });
    }

    private String csvArgument(Expression argument) {
        if (argument instanceof J.Assignment) {
            J.Assignment assignment = (J.Assignment) argument;
            return assignment.getVariable().printTrimmed(getCursor()) + " = " + csvArgument(assignment.getAssignment());
        }
        if (argument instanceof J.MethodInvocation && "arrayOf".equals(((J.MethodInvocation) argument).getSimpleName())) {
            return "[" + ((J.MethodInvocation) argument).getArguments().stream()
                    .map(value -> value.printTrimmed(getCursor())).collect(joining(", ")) + "]";
        }
        return argument.printTrimmed(getCursor());
    }

    private static J.MethodDeclaration unsupported(J.MethodDeclaration method) {
        String comment = " JunitParamsRunnerToParameterized conversion not supported";
        return method.withLeadingAnnotations(ListUtils.map(method.getLeadingAnnotations(), annotation -> {
            if (TypeUtils.isOfClassType(annotation.getType(), PARAMETERS) &&
                    annotation.getComments().stream().noneMatch(existing ->
                            existing instanceof TextComment && comment.equals(((TextComment) existing).getText()))) {
                return annotation.withComments(ListUtils.concat(annotation.getComments(),
                        new TextComment(false, comment, "\n" + annotation.getPrefix().getIndent(), Markers.EMPTY)));
            }
            return annotation;
        }));
    }

    private static J.MethodDeclaration staticProvider(J.MethodDeclaration method) {
        J.MethodDeclaration m = method.withLeadingAnnotations(ListUtils.map(method.getLeadingAnnotations(),
                annotation -> TypeUtils.isOfClassType(annotation.getType(), NAMED_PARAMETERS) ? null : annotation));
        if (m.getLeadingAnnotations().stream().noneMatch(a -> TypeUtils.isOfClassType(a.getType(), "kotlin.jvm.JvmStatic"))) {
            m = m.withLeadingAnnotations(ListUtils.concat(new J.Annotation(randomId(), Space.EMPTY, Markers.EMPTY,
                    TypeTree.build("JvmStatic").withType(JavaType.ShallowClass.build("kotlin.jvm.JvmStatic")), null),
                    ListUtils.map(m.getLeadingAnnotations(), a -> a.withPrefix(Space.format("\n")))));
        }
        return m;
    }

    private static void addProviderDependencies(List<J.MethodDeclaration> methods, Set<String> providers,
                                                JavaType.@Nullable FullyQualified owner) {
        boolean changed;
        do {
            Set<String> found = new LinkedHashSet<>();
            for (J.MethodDeclaration method : methods) {
                if (providers.contains(method.getSimpleName())) {
                    new JavaIsoVisitor<Set<String>>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, Set<String> names) {
                            if (invocation.getMethodType() != null && TypeUtils.isOfType(owner, invocation.getMethodType().getDeclaringType())) {
                                names.add(invocation.getMethodType().getName());
                            }
                            return super.visitMethodInvocation(invocation, names);
                        }
                    }.visit(method, found);
                }
            }
            changed = providers.addAll(found);
        } while (changed);
    }
}
