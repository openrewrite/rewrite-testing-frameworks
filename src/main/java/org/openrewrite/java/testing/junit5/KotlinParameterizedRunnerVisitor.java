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
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.tree.*;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.kotlin.KotlinTemplate;
import org.openrewrite.kotlin.KotlinVisitor;
import org.openrewrite.kotlin.marker.PrimaryConstructor;
import org.openrewrite.kotlin.marker.By;
import org.openrewrite.kotlin.marker.SingleExpressionBlock;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.marker.Markers;

import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.joining;
import static org.openrewrite.Tree.randomId;

class KotlinParameterizedRunnerVisitor extends KotlinIsoVisitor<ExecutionContext> {
    private KotlinParser snippetParser;
    private final Map<String, TypeTree> typePrototypes = new HashMap<>();
    private static final AnnotationMatcher RUN_WITH = new AnnotationMatcher("@org.junit.runner.RunWith");
    private static final AnnotationMatcher PARAMETERS = new AnnotationMatcher("@org.junit.runners.Parameterized$Parameters");
    private static final AnnotationMatcher PARAMETER = new AnnotationMatcher("@org.junit.runners.Parameterized$Parameter");
    private static final AnnotationMatcher TEST = new AnnotationMatcher("@org.junit.Test");
    private static final AnnotationMatcher JUPITER_TEST = new AnnotationMatcher("@org.junit.jupiter.api.Test");
    private static final AnnotationMatcher BEFORE = new AnnotationMatcher("@org.junit.Before");
    private static final AnnotationMatcher BEFORE_EACH = new AnnotationMatcher("@org.junit.jupiter.api.BeforeEach");

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
        if (cd.getType() == null) {
            return cd;
        }
        if (cd.getLeadingAnnotations().stream().noneMatch(this::isParameterizedRunner)) {
            return cd;
        }
        Provider provider = findProvider(cd);
        if (provider == null) {
            return cd;
        }

        J.MethodDeclaration constructor = null;
        J.MethodDeclaration primaryConstructor = null;
        K.Constructor secondaryConstructor = null;
        SortedMap<Integer, J.VariableDeclarations> fields = new TreeMap<>();
        for (Statement statement : cd.getBody().getStatements()) {
            if (statement instanceof J.MethodDeclaration) {
                J.MethodDeclaration method = (J.MethodDeclaration) statement;
                if (method.getMarkers().findFirst(PrimaryConstructor.class).isPresent() || method.isConstructor()) {
                    if (method.getParameters().stream().anyMatch(J.VariableDeclarations.class::isInstance)) {
                        constructor = method;
                        if (method.getMarkers().findFirst(PrimaryConstructor.class).isPresent()) {
                            primaryConstructor = method;
                        }
                    }
                }
            } else if (statement instanceof K.Constructor &&
                    !((K.Constructor) statement).getMethodDeclaration().hasModifier(J.Modifier.Type.Private)) {
                secondaryConstructor = (K.Constructor) statement;
            } else if (statement instanceof J.VariableDeclarations) {
                J.VariableDeclarations field = (J.VariableDeclarations) statement;
                for (J.Annotation annotation : field.getLeadingAnnotations()) {
                    if (PARAMETER.matches(annotation)) {
                        fields.put(parameterIndex(annotation), field);
                    }
                }
            }
        }
        if (secondaryConstructor != null && (primaryConstructor == null ||
                primaryConstructor.hasModifier(J.Modifier.Type.Private))) {
            constructor = secondaryConstructor.getMethodDeclaration();
        } else {
            secondaryConstructor = null;
        }
        List<J.VariableDeclarations> parameters = new ArrayList<>();
        if (constructor != null) {
            for (Statement parameter : constructor.getParameters()) {
                if (parameter instanceof J.VariableDeclarations) {
                    parameters.add((J.VariableDeclarations) parameter);
                }
            }
        } else {
            parameters.addAll(fields.values());
        }
        if (parameters.isEmpty()) {
            return cd;
        }

        String initName = "init" + cd.getSimpleName();
        List<Statement> methodParameters = parameters.stream().map(parameter -> parameter
                .withLeadingAnnotations(emptyList())
                .withModifiers(ListUtils.filter(parameter.getModifiers(), modifier -> "vararg".equals(modifier.getKeyword())))
                .withPrefix(Space.EMPTY)
                .withVariables(ListUtils.map(parameter.getVariables(), variable -> variable.withInitializer(null)
                        .withPrefix(Space.EMPTY).withName(variable.getName().withPrefix(Space.EMPTY)))))
                .collect(toList());

        boolean primary = primaryConstructor != null;
        List<J.VariableDeclarations> propertyParameters = primary ? primaryConstructor.getParameters().stream()
                .filter(J.VariableDeclarations.class::isInstance).map(J.VariableDeclarations.class::cast).collect(toList()) :
                parameters;
        Set<String> fieldNames = new HashSet<>();
        for (Statement member : cd.getBody().getStatements()) {
            if (member instanceof J.VariableDeclarations) {
                ((J.VariableDeclarations) member).getVariables().forEach(variable -> fieldNames.add(variable.getSimpleName()));
            }
        }
        for (J.VariableDeclarations parameter : propertyParameters) {
            if (isPropertyParameter(parameter)) {
                fieldNames.add(parameter.getVariables().get(0).getSimpleName());
            }
        }
        List<J.VariableDeclarations> backingParameters = new ArrayList<>();
        for (J.VariableDeclarations parameter : propertyParameters) {
            J.VariableDeclarations.NamedVariable variable = parameter.getVariables().get(0);
            if (primary && !isPropertyParameter(parameter)) {
                String name = variable.getSimpleName();
                for (int suffix = 0; fieldNames.contains(name); suffix++) {
                    name = variable.getSimpleName() + "Argument" + (suffix == 0 ? "" : suffix);
                }
                fieldNames.add(name);
                JavaType.Variable type = variable.getVariableType().withName(name).withOwner(cd.getType());
                parameter = parameter.withVariables(singletonList(variable.withVariableType(type)
                        .withName(variable.getName().withSimpleName(name).withFieldType(type))));
            }
            backingParameters.add(parameter);
        }
        Map<UUID, DelegatedProperty> delegates = new HashMap<>();
        if (primary) {
            Set<String> names = new HashSet<>();
            for (Statement member : cd.getBody().getStatements()) {
                if (member instanceof J.VariableDeclarations) {
                    ((J.VariableDeclarations) member).getVariables().forEach(variable -> names.add(variable.getSimpleName()));
                }
            }
            for (Statement member : cd.getBody().getStatements()) {
                if (member instanceof J.VariableDeclarations && member.getMarkers().findFirst(By.class).isPresent()) {
                    J.VariableDeclarations field = (J.VariableDeclarations) member;
                    delegates.put(field.getId(), delegatedProperty(field, names, cd, ctx));
                }
            }
        }
        List<Statement> initStatements = new ArrayList<>();
        if (constructor == null || primary) {
            for (int index = 0; index < propertyParameters.size(); index++) {
                J.VariableDeclarations parameter = backingParameters.get(index);
                initStatements.add(secondaryConstructor == null ?
                        assignment(parameter, propertyParameters.get(index).getVariables().get(0).getName()) :
                        assignment(parameter, secondaryConstructor.getInvocation().getArguments().get(index)));
            }
        } else if (constructor.getBody() != null) {
            initStatements.addAll(constructor.getBody().getStatements());
        }

        if (primary) {
            for (Statement statement : cd.getBody().getStatements()) {
                if (statement instanceof J.VariableDeclarations) {
                    J.VariableDeclarations field = (J.VariableDeclarations) statement;
                    for (J.VariableDeclarations.NamedVariable variable : field.getVariables()) {
                        if (variable.getInitializer() != null) {
                            DelegatedProperty delegate = delegates.get(field.getId());
                            initStatements.add(assignment(delegate == null ? field : delegate.backing, variable.getInitializer()));
                        }
                    }
                } else if (statement instanceof J.Block) {
                    initStatements.addAll(((J.Block) statement).getStatements());
                }
            }
            if (secondaryConstructor != null && constructor.getBody() != null) {
                initStatements.addAll(constructor.getBody().getStatements());
            }
        }
        J.MethodDeclaration init = initMethod(cd, constructor, initName, methodParameters, initStatements);
        List<J.MethodDeclaration> setupMethods = new ArrayList<>();
        for (Statement member : cd.getBody().getStatements()) {
            if (member instanceof J.MethodDeclaration && ((J.MethodDeclaration) member).getLeadingAnnotations().stream()
                    .anyMatch(annotation -> BEFORE.matches(annotation) || BEFORE_EACH.matches(annotation))) {
                setupMethods.add((J.MethodDeclaration) member);
            }
        }
        List<Statement> members = new ArrayList<>();
        if (primary) {
            for (int index = 0; index < backingParameters.size(); index++) {
                J.VariableDeclarations parameter = backingParameters.get(index);
                J.VariableDeclarations field = backingField(parameter, cd, ctx);
                members.add(isPropertyParameter(propertyParameters.get(index)) ? field :
                        field.withModifiers(ListUtils.concatAll(singletonList(modifier("private")), field.getModifiers())));
            }
        }
        for (Statement statement : cd.getBody().getStatements()) {
            if (statement == primaryConstructor || statement == constructor ||
                    statement == secondaryConstructor && primaryConstructor == null) {
                members.add(init);
            } else if (statement == secondaryConstructor) {
                // Its delegation and body have been folded into the initialization method.
            } else if (statement instanceof J.MethodDeclaration) {
                J.MethodDeclaration method = (J.MethodDeclaration) statement;
                if (method.getLeadingAnnotations().stream().anyMatch(annotation -> TEST.matches(annotation) || JUPITER_TEST.matches(annotation))) {
                    method = parameterize(method, provider, methodParameters, init, setupMethods, ctx);
                }
                method = method.withLeadingAnnotations(ListUtils.map(method.getLeadingAnnotations(),
                        annotation -> BEFORE.matches(annotation) || BEFORE_EACH.matches(annotation) ? null : annotation));
                members.add(removeParametersAnnotation(method));
            } else if (statement instanceof J.ClassDeclaration && isCompanion((J.ClassDeclaration) statement)) {
                J.ClassDeclaration nested = (J.ClassDeclaration) statement;
                members.add(nested.withBody(nested.getBody().withStatements(ListUtils.mapFirst(ListUtils.map(
                        nested.getBody().getStatements(), member -> member instanceof J.MethodDeclaration ?
                                removeParametersAnnotation((J.MethodDeclaration) member) : member),
                        member -> member.withPrefix(member.getPrefix().withWhitespace("\n"))))));
            } else if (statement instanceof J.VariableDeclarations) {
                J.VariableDeclarations field = (J.VariableDeclarations) statement;
                DelegatedProperty delegate = delegates.get(field.getId());
                if (delegate != null) {
                    members.add(delegate.backing);
                    members.add(delegate.property);
                    continue;
                }
                field = field.withLeadingAnnotations(ListUtils.map(field.getLeadingAnnotations(),
                        annotation -> PARAMETER.matches(annotation) ? null : annotation));
                if (primary || constructor != null && field.getVariables().stream().anyMatch(variable -> variable.getInitializer() == null)) {
                    field = backingField(field, cd, ctx);
                }
                members.add(field);
            } else if (primary && statement instanceof J.Block) {
                // Initializers must run after the arguments have been assigned.
            } else {
                members.add(statement);
            }
        }
        if (constructor == null) {
            members.add(init);
        }
        cd = cd.withLeadingAnnotations(ListUtils.map(cd.getLeadingAnnotations(),
                        annotation -> isParameterizedRunner(annotation) ? null : annotation))
                .withMarkers(cd.getMarkers().removeByType(PrimaryConstructor.class))
                .withBody(cd.getBody().withStatements(members));
        maybeRemoveImport("org.junit.runner.RunWith");
        maybeRemoveImport("org.junit.runners.Parameterized");
        maybeRemoveImport("org.junit.runners.Parameterized.Parameters");
        maybeRemoveImport("org.junit.runners.Parameterized.Parameter");
        maybeRemoveImport("org.junit.Test");
        maybeRemoveImport("org.junit.jupiter.api.Test");
        maybeRemoveImport("org.junit.Before");
        maybeRemoveImport("org.junit.jupiter.api.BeforeEach");
        maybeAddImport("org.junit.jupiter.params.ParameterizedTest");
        maybeAddImport("org.junit.jupiter.params.provider.MethodSource");
        cd = maybeAutoFormat(classDecl, cd, ctx, getCursor().getParentTreeCursor());
        return KotlinJUnit5Support.normalizeCompanions(cd);
    }

    private boolean isParameterizedRunner(J.Annotation annotation) {
        if (!RUN_WITH.matches(annotation) || annotation.getArguments() == null) {
            return false;
        }
        for (Expression argument : annotation.getArguments()) {
            Expression value = argument instanceof J.Assignment ? ((J.Assignment) argument).getAssignment() : argument;
            if (value instanceof J.MemberReference && TypeUtils.isOfClassType(
                    ((J.MemberReference) value).getContaining().getType(), "org.junit.runners.Parameterized")) {
                return true;
            }
        }
        return false;
    }

    private @Nullable Provider findProvider(J.ClassDeclaration cd) {
        for (Statement statement : cd.getBody().getStatements()) {
            if (statement instanceof J.MethodDeclaration) {
                J.MethodDeclaration method = (J.MethodDeclaration) statement;
                for (J.Annotation annotation : method.getLeadingAnnotations()) {
                    if (PARAMETERS.matches(annotation)) {
                        return new Provider(method.getSimpleName(), annotation.getArguments());
                    }
                }
            } else if (statement instanceof J.ClassDeclaration && isCompanion((J.ClassDeclaration) statement)) {
                Provider provider = findProvider((J.ClassDeclaration) statement);
                if (provider != null) {
                    return provider;
                }
            }
        }
        return null;
    }

    private boolean isCompanion(J.ClassDeclaration cd) {
        return cd.getModifiers().stream().anyMatch(modifier -> "companion".equals(modifier.getKeyword()));
    }

    private boolean isPropertyParameter(J.VariableDeclarations parameter) {
        return parameter.getModifiers().stream().anyMatch(modifier ->
                modifier.getType() == J.Modifier.Type.Final || "var".equals(modifier.getKeyword()));
    }

    private int parameterIndex(J.Annotation annotation) {
        if (annotation.getArguments() == null || annotation.getArguments().isEmpty()) {
            return 0;
        }
        Expression argument = annotation.getArguments().get(0);
        if (argument instanceof J.Assignment) {
            argument = ((J.Assignment) argument).getAssignment();
        }
        return argument instanceof J.Literal ? ((Number) ((J.Literal) argument).getValue()).intValue() : 0;
    }

    private J.VariableDeclarations backingField(J.VariableDeclarations parameter, J.ClassDeclaration cd, ExecutionContext ctx) {
        List<J.Modifier> modifiers = ListUtils.map(parameter.getModifiers(), modifier ->
                modifier.getType() == J.Modifier.Type.Final || "val".equals(modifier.getKeyword()) ||
                        "var".equals(modifier.getKeyword()) || "lateinit".equals(modifier.getKeyword()) ? null : modifier);
        JavaType type = parameter.getVariables().get(0).getType();
        boolean nullable = parameter.getTypeExpression() != null ?
                parameter.getTypeExpression().printTrimmed(getCursor()).endsWith("?") :
                nullableInitializer(parameter.getVariables().get(0).getInitializer(), cd);
        if (parameter.getTypeExpression() == null) {
            parameter = parameter.withTypeExpression(typeExpression(kotlinType(type) + (nullable ? "?" : ""), ctx)
                    .withType(type).withPrefix(Space.SINGLE_SPACE));
        }
        Expression initializer = defaultValue(type, nullable);
        if (initializer == null) {
            modifiers = ListUtils.concat(modifiers, modifier("lateinit"));
            parameter = parameter.withLeadingAnnotations(ListUtils.map(parameter.getLeadingAnnotations(),
                    annotation -> "JvmField".equals(annotation.getSimpleName()) ? null : annotation));
        }
        modifiers = ListUtils.concat(modifiers, modifier("var"));
        Expression fieldInitializer = initializer;
        return parameter.withModifiers(modifiers)
                .withVariables(ListUtils.map(parameter.getVariables(), variable -> variable.withInitializer(fieldInitializer)))
                .withPrefix(Space.format("\n"));
    }

    private boolean nullableInitializer(@Nullable Expression initializer, J.ClassDeclaration cd) {
        if (initializer == null) {
            return false;
        }
        boolean[] nullable = {false};
        new KotlinIsoVisitor<boolean[]>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, boolean[] value) {
                value[0] |= literal.getValue() == null;
                return literal;
            }

            @Override
            public J.Lambda visitLambda(J.Lambda lambda, boolean[] value) {
                return lambda;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, boolean[] value) {
                for (Statement member : cd.getBody().getStatements()) {
                    if (member instanceof J.MethodDeclaration &&
                            ((J.MethodDeclaration) member).getSimpleName().equals(invocation.getSimpleName())) {
                        TypeTree returnType = ((J.MethodDeclaration) member).getReturnTypeExpression();
                        value[0] |= returnType != null && returnType.printTrimmed(getCursor()).endsWith("?");
                    }
                }
                return super.visitMethodInvocation(invocation, value);
            }
        }.visit(initializer, nullable);
        return nullable[0];
    }

    private static String kotlinType(JavaType type) {
        if (type instanceof JavaType.Parameterized) {
            JavaType.Parameterized parameterized = (JavaType.Parameterized) type;
            return parameterized.getFullyQualifiedName().replace('$', '.') + "<" +
                    parameterized.getTypeParameters().stream().map(KotlinParameterizedRunnerVisitor::kotlinType)
                            .collect(joining(", ")) + ">";
        }
        if (type instanceof JavaType.GenericTypeVariable) {
            JavaType.GenericTypeVariable generic = (JavaType.GenericTypeVariable) type;
            return "?".equals(generic.getName()) ? "*" : generic.getName();
        }
        if (type instanceof JavaType.Array) {
            return "kotlin.Array<" + kotlinType(((JavaType.Array) type).getElemType()) + ">";
        }
        if (type instanceof JavaType.Primitive) {
            switch ((JavaType.Primitive) type) {
                case Boolean: return "kotlin.Boolean";
                case Byte: return "kotlin.Byte";
                case Char: return "kotlin.Char";
                case Double: return "kotlin.Double";
                case Float: return "kotlin.Float";
                case Int: return "kotlin.Int";
                case Long: return "kotlin.Long";
                case Short: return "kotlin.Short";
                case String: return "kotlin.String";
                case Void: return "kotlin.Unit";
                default: return "kotlin.Any";
            }
        }
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
        return fq == null ? "kotlin.Any" : fq.getFullyQualifiedName().replace('$', '.');
    }

    private TypeTree typeExpression(String name, ExecutionContext ctx) {
        TypeTree prototype = typePrototypes.get(name);
        if (prototype == null) {
            if (snippetParser == null) {
                snippetParser = KotlinParser.builder().build();
            }
            K.CompilationUnit parsed = (K.CompilationUnit) snippetParser.reset()
                    .parse(ctx, "val value: " + name + " = TODO()").findFirst().orElseThrow(IllegalStateException::new);
            prototype = ((J.VariableDeclarations) parsed.getStatements().get(0)).getTypeExpression();
            typePrototypes.put(name, prototype);
        }
        return (TypeTree) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J preVisit(J tree, ExecutionContext ctx) {
                return tree.withId(randomId());
            }
        }.visitNonNull((J) prototype, ctx);
    }

    private DelegatedProperty delegatedProperty(J.VariableDeclarations field, Set<String> names,
                                                J.ClassDeclaration cd, ExecutionContext ctx) {
        J.VariableDeclarations.NamedVariable variable = field.getVariables().get(0);
        String name = variable.getSimpleName() + "Delegate";
        for (int suffix = 1; !names.add(name); suffix++) {
            name = variable.getSimpleName() + "Delegate" + suffix;
        }
        String delegateName = name;
        Expression initializer = variable.getInitializer();
        JavaType delegateType = initializer.getType();
        JavaType.Variable backingType = variable.getVariableType().withName(name).withType(delegateType);
        J.VariableDeclarations backing = field.withId(randomId()).withMarkers(field.getMarkers().removeByType(By.class))
                .withLeadingAnnotations(emptyList()).withTypeExpression(null)
                .withVariables(singletonList(variable.withId(randomId()).withVariableType(backingType)
                        .withName(variable.getName().withId(randomId()).withSimpleName(name).withType(delegateType)
                                .withFieldType(backingType))));
        backing = backingField(backing, cd, ctx);
        boolean mutable = !field.hasModifier(J.Modifier.Type.Final);
        if (snippetParser == null) {
            snippetParser = KotlinParser.builder().build();
        }
        String propertyType = kotlinType(variable.getType());
        String backingTypeName = kotlinType(delegateType);
        if (backingTypeName.startsWith("kotlin.properties.ReadWriteProperty<kotlin.Any,")) {
            backingTypeName = backingTypeName.replace("ReadWriteProperty<kotlin.Any,", "ReadWriteProperty<kotlin.Any?,");
        }
        backing = backing.withTypeExpression(typeExpression(backingTypeName, ctx).withType(delegateType).withPrefix(Space.SINGLE_SPACE));
        K.CompilationUnit parsed = (K.CompilationUnit) snippetParser.reset().parse(ctx,
                "class Placeholder {\n" +
                        "    private lateinit var " + delegateName + ": " + backingTypeName + "\n" +
                        "    private " + (mutable ? "var " : "val ") + variable.getSimpleName() + ": " + propertyType + "\n" +
                        "        get() = " + delegateName + ".getValue(this, ::" + variable.getSimpleName() + ")\n" +
                        (mutable ? "        set(value) { " + delegateName + ".setValue(this, ::" +
                                variable.getSimpleName() + ", value) }\n" : "") + "}")
                .findFirst().orElseThrow(IllegalStateException::new);
        parsed = (K.CompilationUnit) new ChangeType("Placeholder", cd.getType().getFullyQualifiedName(), true)
                .getVisitor().visitNonNull(parsed, ctx);
        J.ClassDeclaration prototype = (J.ClassDeclaration) parsed.getStatements().get(0);
        K.Property property = (K.Property) prototype.getBody().getStatements().get(1);
        J.VariableDeclarations declaration = property.getVariableDeclarations()
                .withLeadingAnnotations(field.getLeadingAnnotations()).withModifiers(field.getModifiers());
        property = property.withPaddedVariableDeclarations(property.getPaddedVariableDeclarations().withElement(declaration))
                .withPrefix(field.getPrefix());
        return new DelegatedProperty(backing, property);
    }

    private @Nullable Expression defaultValue(@Nullable JavaType type, boolean nullable) {
        if (nullable || type == JavaType.Primitive.Null) {
            return new J.Literal(randomId(), Space.format(" "), Markers.EMPTY, null, "null", null, JavaType.Primitive.Null);
        }
        if (type == JavaType.Primitive.Boolean) {
            return new J.Literal(randomId(), Space.format(" "), Markers.EMPTY, false, "false", null, JavaType.Primitive.Boolean);
        }
        if (type == JavaType.Primitive.Char) {
            return new J.Literal(randomId(), Space.format(" "), Markers.EMPTY, '\0', "'\\u0000'", null, JavaType.Primitive.Char);
        }
        if (type == JavaType.Primitive.Void) {
            return (Expression) TypeTree.build("kotlin.Unit").withType(JavaType.ShallowClass.build("kotlin.Unit"));
        }
        if (type instanceof JavaType.Primitive && type != JavaType.Primitive.String) {
            String value = type == JavaType.Primitive.Long ? "0L" : type == JavaType.Primitive.Float ? "0.0f" :
                    type == JavaType.Primitive.Double ? "0.0" : "0";
            return new J.Literal(randomId(), Space.format(" "), Markers.EMPTY, 0, value, null, (JavaType.Primitive) type);
        }
        return null;
    }

    private J.Modifier modifier(String keyword) {
        return new J.Modifier(randomId(), Space.format(" "), Markers.EMPTY, keyword, J.Modifier.Type.LanguageExtension, emptyList());
    }

    private Statement assignment(J.VariableDeclarations parameter) {
        return assignment(parameter, parameter.getVariables().get(0).getName().withId(randomId()).withPrefix(Space.format(" ")));
    }

    private Statement assignment(J.VariableDeclarations parameter, Expression value) {
        J.VariableDeclarations.NamedVariable variable = parameter.getVariables().get(0);
        J.Identifier name = variable.getName().withId(randomId()).withPrefix(Space.EMPTY);
        J.FieldAccess field = new J.FieldAccess(randomId(), Space.EMPTY, Markers.EMPTY,
                new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), "this", null, null),
                new JLeftPadded<>(Space.EMPTY, name, Markers.EMPTY), variable.getType());
        return new J.Assignment(randomId(), Space.format("\n"), Markers.EMPTY, field,
                new JLeftPadded<>(Space.format(" "), value.withPrefix(Space.format(" ")), Markers.EMPTY),
                variable.getType());
    }

    private J.MethodDeclaration initMethod(J.ClassDeclaration cd, J.@Nullable MethodDeclaration constructor, String name,
                                          List<Statement> parameters, List<Statement> statements) {
        J.Block body = new J.Block(randomId(), Space.format(" "), Markers.EMPTY,
                new JRightPadded<>(false, Space.EMPTY, Markers.EMPTY),
                JRightPadded.withElements(emptyList(), statements), Space.format("\n"));
        if (constructor != null) {
            JavaType.Method type = constructor.getMethodType();
            if (type != null) {
                type = type.withName(name).withReturnType(JavaType.Primitive.Void);
            }
            return constructor.withMarkers(constructor.getMarkers().removeByType(PrimaryConstructor.class)
                            .removeByType(org.openrewrite.kotlin.marker.Implicit.class))
                    .withLeadingAnnotations(emptyList()).withModifiers(singletonList(modifier("fun")))
                    .withName(constructor.getName().withSimpleName(name).withPrefix(Space.format(" "))
                            .withMarkers(Markers.EMPTY).withType(type))
                    .withReturnTypeExpression(null).withParameters(parameters).withBody(body).withMethodType(type)
                    .withPrefix(Space.format("\n\n"));
        }
        JavaType.Method type = new JavaType.Method(null, 0, cd.getType(), name, JavaType.Primitive.Void,
                parameters.stream().map(parameter -> ((J.VariableDeclarations) parameter).getVariables().get(0).getSimpleName()).collect(toList()),
                parameters.stream().map(parameter -> ((J.VariableDeclarations) parameter).getVariables().get(0).getType()).collect(toList()),
                emptyList(), emptyList(), emptyList(), emptyList());
        return new J.MethodDeclaration(randomId(), Space.format("\n\n"), Markers.EMPTY, emptyList(),
                singletonList(modifier("fun")), null, null,
                new J.MethodDeclaration.IdentifierWithAnnotations(new J.Identifier(randomId(), Space.format(" "),
                        Markers.EMPTY, emptyList(), name, type, null), emptyList()),
                JContainer.build(JRightPadded.withElements(emptyList(), parameters)), null, body, null, type);
    }

    private J.MethodDeclaration parameterize(J.MethodDeclaration method, Provider provider, List<Statement> parameters,
                                            J.MethodDeclaration init, List<J.MethodDeclaration> setupMethods, ExecutionContext ctx) {
        JavaType.FullyQualified annotationType = JavaType.ShallowClass.build("org.junit.jupiter.params.ParameterizedTest");
        J.MethodDeclaration m = method.withLeadingAnnotations(ListUtils.map(method.getLeadingAnnotations(), annotation ->
                TEST.matches(annotation) || JUPITER_TEST.matches(annotation) ?
                        annotation.withAnnotationType(new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(),
                                        "ParameterizedTest", annotationType, null)).withArguments(provider.arguments) : annotation));
        m = KotlinTemplate.builder("@MethodSource(\"" + provider.name + "\")")
                .parser(KotlinParser.builder().classpathFromResources(ctx, "junit-jupiter-api-5", "junit-jupiter-params-5"))
                .imports("org.junit.jupiter.params.provider.MethodSource")
                .build().apply(new Cursor(getCursor().getParentTreeCursor(), m), m.getCoordinates()
                        .addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
        List<Statement> testParameters = new ArrayList<>(parameters);
        Set<String> parameterNames = new HashSet<>();
        for (Statement parameter : testParameters) {
            parameterNames.add(((J.VariableDeclarations) parameter).getVariables().get(0).getSimpleName());
        }
        Map<UUID, List<Expression>> setupArguments = new HashMap<>();
        for (J.MethodDeclaration setupMethod : setupMethods) {
            List<Expression> arguments = new ArrayList<>();
            for (Statement parameter : setupMethod.getParameters()) {
                if (!(parameter instanceof J.VariableDeclarations)) {
                    continue;
                }
                J.VariableDeclarations declaration = (J.VariableDeclarations) parameter;
                String name = declaration.getVariables().get(0).getSimpleName();
                String uniqueName = name;
                for (int suffix = 1; !parameterNames.add(uniqueName); suffix++) {
                    uniqueName = name + suffix;
                }
                String argumentName = uniqueName;
                declaration = declaration.withId(randomId()).withLeadingAnnotations(emptyList())
                        .withVariables(ListUtils.map(declaration.getVariables(), variable ->
                                variable.withId(randomId()).withName(variable.getName().withId(randomId()).withSimpleName(argumentName))));
                testParameters.add(declaration);
                arguments.add(declaration.getVariables().get(0).getName().withId(randomId()).withPrefix(Space.EMPTY));
            }
            setupArguments.put(setupMethod.getId(), arguments);
        }
        m = m.withParameters(testParameters);
        if (m.getMethodType() != null) {
            m = m.withMethodType(m.getMethodType()
                    .withParameterNames(testParameters.stream().map(parameter ->
                            ((J.VariableDeclarations) parameter).getVariables().get(0).getSimpleName()).collect(toList()))
                    .withParameterTypes(testParameters.stream().map(parameter ->
                            ((J.VariableDeclarations) parameter).getVariables().get(0).getType()).collect(toList())));
            m = m.withName(m.getName().withType(m.getMethodType()));
        }
        if (m.getBody() != null) {
            J.Block body = m.getBody();
            if (body.getMarkers().findFirst(SingleExpressionBlock.class).isPresent()) {
                m = m.withBody(body.withMarkers(body.getMarkers().removeByType(SingleExpressionBlock.class)
                                .removeByType(org.openrewrite.kotlin.marker.OmitBraces.class)
                                .removeByType(org.openrewrite.java.marker.OmitBraces.class))
                        .withStatements(ListUtils.mapFirst(body.getStatements(), statement ->
                                statement.withPrefix(Space.format("\n")))).withEnd(Space.format("\n")));
            }
            Set<String> names = new HashSet<>();
            for (Statement parameter : parameters) {
                names.add(((J.VariableDeclarations) parameter).getVariables().get(0).getSimpleName());
            }
            J.ClassDeclaration enclosingClass = getCursor().getValue();
            if (enclosingClass.getType() != null) {
                m = m.withBody((J.Block) new KotlinVisitor<ExecutionContext>() {
                    @Override
                    public J visitStringTemplateExpression(K.StringTemplate.Expression expression, ExecutionContext ctx) {
                        K.StringTemplate.Expression e = (K.StringTemplate.Expression) super.visitStringTemplateExpression(expression, ctx);
                        return e.getTree() instanceof J.FieldAccess ? e.withEnclosedInBraces(true) : e;
                    }

                    @Override
                    public J visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                        Object parent = getCursor().getParentTreeCursor().getValue();
                        if (names.contains(identifier.getSimpleName()) && identifier.getFieldType() != null &&
                                TypeUtils.isOfType(identifier.getFieldType().getOwner(), enclosingClass.getType()) &&
                                !(parent instanceof J.FieldAccess && ((J.FieldAccess) parent).getName() == identifier)) {
                            return new J.FieldAccess(randomId(), identifier.getPrefix(), Markers.EMPTY,
                                    new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(),
                                            "this", enclosingClass.getType(), null),
                                    new JLeftPadded<>(Space.EMPTY, identifier.withPrefix(Space.EMPTY), Markers.EMPTY),
                                    identifier.getType());
                        }
                        return identifier;
                    }
                }.visitNonNull(m.getBody(), ctx, new Cursor(getCursor(), m)));
            }
            List<Expression> arguments = parameters.stream().map(parameter -> {
                J.VariableDeclarations declaration = (J.VariableDeclarations) parameter;
                Expression argument = declaration.getVariables().get(0).getName().withId(randomId()).withPrefix(Space.EMPTY);
                return declaration.getModifiers().stream().anyMatch(modifier -> "vararg".equals(modifier.getKeyword())) ?
                        new org.openrewrite.kotlin.tree.K.SpreadArgument(randomId(), Space.EMPTY, Markers.EMPTY, argument) : argument;
            })
                    .collect(toList());
            J.MethodInvocation call = new J.MethodInvocation(randomId(), Space.format("\n"), Markers.EMPTY, null, null,
                    init.getName().withId(randomId()).withPrefix(Space.EMPTY),
                    JContainer.build(JRightPadded.withElements(emptyList(), arguments)), init.getMethodType());
            List<Statement> setup = new ArrayList<>();
            setup.add(call);
            for (J.MethodDeclaration setupMethod : setupMethods) {
                List<Expression> setupParameters = setupArguments.get(setupMethod.getId());
                setup.add(new J.MethodInvocation(randomId(), Space.format("\n"), Markers.EMPTY, null, null,
                        setupMethod.getName().withId(randomId()).withPrefix(Space.EMPTY),
                        setupParameters.isEmpty() ?
                                JContainer.build(singletonList(JRightPadded.build(new J.Empty(randomId(), Space.EMPTY, Markers.EMPTY)))) :
                                JContainer.build(JRightPadded.withElements(emptyList(), setupParameters)),
                        setupMethod.getMethodType()));
            }
            m = m.withBody(m.getBody().withStatements(ListUtils.concatAll(setup, m.getBody().getStatements())));
        }
        return m;
    }

    private J.MethodDeclaration removeParametersAnnotation(J.MethodDeclaration method) {
        return method.withLeadingAnnotations(ListUtils.map(method.getLeadingAnnotations(),
                annotation -> PARAMETERS.matches(annotation) ? null : annotation));
    }

    private static class Provider {
        final String name;
        final @Nullable List<Expression> arguments;

        Provider(String name, @Nullable List<Expression> arguments) {
            this.name = name;
            this.arguments = arguments;
        }
    }

    private static class DelegatedProperty {
        final J.VariableDeclarations backing;
        final Statement property;

        DelegatedProperty(J.VariableDeclarations backing, Statement property) {
            this.backing = backing;
            this.property = property;
        }
    }
}
