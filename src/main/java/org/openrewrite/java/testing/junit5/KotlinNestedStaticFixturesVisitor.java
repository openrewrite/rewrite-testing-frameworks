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

import org.openrewrite.ExecutionContext;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.kotlin.KotlinVisitor;
import org.openrewrite.kotlin.marker.Extension;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.marker.Markers;

import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.openrewrite.Tree.randomId;

class KotlinNestedStaticFixturesVisitor extends KotlinIsoVisitor<ExecutionContext> {
    private final UUID enclosingClass;
    private final Map<String, Companion> companions = new LinkedHashMap<>();
    private KotlinParser snippetParser;
    private J.MethodInvocation extensionScope;

    private K.CompilationUnit parseSnippet(ExecutionContext ctx, String source) {
        if (snippetParser == null) {
            snippetParser = KotlinParser.builder().build();
        }
        return (K.CompilationUnit) snippetParser.reset().parse(ctx, source)
                .findFirst().orElseThrow(IllegalStateException::new);
    }

    KotlinNestedStaticFixturesVisitor(UUID enclosingClass) {
        this.enclosingClass = enclosingClass;
    }

    @Override
    public K.CompilationUnit visitCompilationUnit(K.CompilationUnit cu, ExecutionContext ctx) {
        K.CompilationUnit c = super.visitCompilationUnit(cu, ctx);
        if (companions.isEmpty()) {
            return c;
        }
        c = (K.CompilationUnit) new KotlinVisitor<ExecutionContext>() {
            @Override
            public J visitImport(J.Import anImport, ExecutionContext ctx) {
                return anImport;
            }

            @Override
            public J visitStringTemplateExpression(K.StringTemplate.Expression expression, ExecutionContext ctx) {
                K.StringTemplate.Expression e = (K.StringTemplate.Expression) super.visitStringTemplateExpression(expression, ctx);
                return e.getTree() instanceof J.FieldAccess ? e.withEnclosedInBraces(true) : e;
            }

            @Override
            public J visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier id = (J.Identifier) super.visitIdentifier(identifier, ctx);
                JavaType.Variable field = id.getFieldType();
                Companion companion = field == null ? null : companion(field.getOwner());
                Object parent = getCursor().getParentTreeCursor().getValue();
                if (companion == null || inside(companion) ||
                        parent instanceof J.VariableDeclarations.NamedVariable &&
                                ((J.VariableDeclarations.NamedVariable) parent).getName() == id ||
                        parent instanceof J.FieldAccess && ((J.FieldAccess) parent).getName() == id) {
                    return id;
                }
                return new J.FieldAccess(randomId(), id.getPrefix(), Markers.EMPTY, reference(companion),
                        JLeftPadded.build(id.withPrefix(Space.EMPTY)), id.getType());
            }

            @Override
            public J visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                Companion owner = companion(fieldAccess.getType());
                if (owner != null) {
                    return reference(owner).withPrefix(fieldAccess.getPrefix());
                }
                J.FieldAccess fa = (J.FieldAccess) super.visitFieldAccess(fieldAccess, ctx);
                Companion companion = fa.getName().getFieldType() == null ? null :
                        companion(fa.getName().getFieldType().getOwner());
                return companion == null || inside(companion) ? fa : fa.withTarget(reference(companion));
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                Companion companion = m.getMethodType() == null ? null : companion(m.getMethodType().getDeclaringType());
                if (companion == null || inside(companion)) {
                    return m;
                }
                if (companion.extensions.contains(m.getSimpleName())) {
                    if (extensionScope == null) {
                        K.CompilationUnit parsed = parseSnippet(ctx,
                                "class Placeholder { companion object { fun String.extension() = this } }\n" +
                                        "val result = with(Placeholder) { \"\".extension() }");
                        extensionScope = (J.MethodInvocation) ((J.VariableDeclarations) parsed.getStatements().get(1))
                                .getVariables().get(0).getInitializer();
                    }
                    J.MethodInvocation call = (J.MethodInvocation) new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J preVisit(J tree, ExecutionContext ctx) {
                            return tree.withId(randomId());
                        }
                    }.visitNonNull(extensionScope, ctx);
                    J.Lambda lambda = (J.Lambda) call.getArguments().get(1);
                    lambda = lambda.withBody(((J.Block) lambda.getBody()).withStatements(singletonList(
                            new K.ExpressionStatement(m.withPrefix(Space.SINGLE_SPACE)))));
                    return call.withPrefix(m.getPrefix()).withMethodType(call.getMethodType().withReturnType(m.getType()))
                            .withArguments(Arrays.asList(reference(companion), lambda));
                }
                return m.withSelect(reference(companion));
            }

            @Override
            public J visitMemberReference(J.MemberReference memberReference, ExecutionContext ctx) {
                Companion owner = companion(memberReference.getContaining().getType());
                J.MemberReference m = (J.MemberReference) super.visitMemberReference(memberReference, ctx);
                if (owner != null && "class".equals(m.getReference().getSimpleName())) {
                    return m.withContaining(companionReference(owner));
                }
                Companion companion = m.getMethodType() == null ? null : companion(m.getMethodType().getDeclaringType());
                return companion == null || inside(companion) ? m : m.withContaining(companionReference(companion));
            }

            private boolean inside(Companion companion) {
                return getCursor().getPathAsStream().anyMatch(value -> value instanceof J.ClassDeclaration &&
                        ((J.ClassDeclaration) value).getId().equals(companion.declaration.getId()));
            }

            private Companion companion(JavaType type) {
                JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
                return fq == null ? null : companions.get(fq.getFullyQualifiedName());
            }

            private Expression reference(Companion companion) {
                boolean enclosed = getCursor().getPathAsStream().anyMatch(value -> value instanceof J.ClassDeclaration &&
                        ((J.ClassDeclaration) value).getId().equals(enclosingClass));
                return enclosed ? KotlinNestedStaticFixturesVisitor.reference(companion) :
                        TypeTree.build(companion.type.getClassName()).withType(companion.declaration.getType());
            }

            private J.FieldAccess companionReference(Companion companion) {
                return new J.FieldAccess(randomId(), Space.EMPTY, Markers.EMPTY, reference(companion),
                        JLeftPadded.build(new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(),
                                companion.name, companion.declaration.getType(), null)), companion.declaration.getType());
            }
        }.visitNonNull(c, ctx, getCursor().getParentTreeCursor());
        for (Companion companion : companions.values()) {
            c = (K.CompilationUnit) new ChangeType(companion.originalType,
                    companion.type.getFullyQualifiedName() + "$" + companion.name, true).getVisitor()
                    .visitNonNull(c, ctx, getCursor().getParentTreeCursor());
        }
        return c;
    }

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
        if (!classDecl.getId().equals(enclosingClass) || classDecl.getType() == null) {
            return super.visitClassDeclaration(classDecl, ctx);
        }
        Set<String> names = new HashSet<>();
        new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, ExecutionContext ctx) {
                names.add(cd.getSimpleName());
                return super.visitClassDeclaration(cd, ctx);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                names.add(method.getSimpleName());
                return super.visitMethodDeclaration(method, ctx);
            }

            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, ExecutionContext ctx) {
                names.add(variable.getSimpleName());
                return super.visitVariable(variable, ctx);
            }
        }.visit(classDecl, ctx);
        List<Statement> lifted = new ArrayList<>();
        J.ClassDeclaration cd = (J.ClassDeclaration) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDeclaration, ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDeclaration, ctx);
                if (c.getType() == null || c.getLeadingAnnotations().stream().noneMatch(annotation ->
                        TypeUtils.isOfClassType(annotation.getType(), "org.junit.jupiter.api.Nested"))) {
                    return c;
                }
                for (Statement member : c.getBody().getStatements()) {
                    if (!(member instanceof J.ClassDeclaration) || ((J.ClassDeclaration) member).getModifiers().stream()
                            .noneMatch(modifier -> "companion".equals(modifier.getKeyword()))) {
                        continue;
                    }
                    J.ClassDeclaration declaration = (J.ClassDeclaration) member;
                    if (declaration.getType() == null) {
                        continue;
                    }
                    String name = c.getSimpleName() + "Fixtures";
                    for (int suffix = 1; !names.add(name); suffix++) {
                        name = c.getSimpleName() + "Fixtures" + suffix;
                    }
                    String fixtureType = classDecl.getType().getFullyQualifiedName() + "$" + name;
                    K.CompilationUnit parsed = parseSnippet(ctx, "abstract class " + name + " {}\nclass Usage : " + name + "()");
                    parsed = (K.CompilationUnit) new ChangeType(name, fixtureType, true).getVisitor().visitNonNull(parsed, ctx);
                    J.ClassDeclaration fixture = (J.ClassDeclaration) parsed.getStatements().get(0);
                    J.ClassDeclaration usage = (J.ClassDeclaration) parsed.getStatements().get(1);
                    K.ConstructorInvocation fixtureParent = (K.ConstructorInvocation) usage.getImplements().get(0);
                    Space parentPrefix = c.getImplements() == null || c.getImplements().isEmpty() ?
                            Space.EMPTY : Space.SINGLE_SPACE;
                    fixtureParent = fixtureParent.withPrefix(Space.EMPTY).withTypeTree(
                            new J.Identifier(randomId(), parentPrefix, Markers.EMPTY, emptyList(), name, fixture.getType(), null));
                    Companion companion = new Companion(declaration, fixture.getType());
                    companions.put(companion.originalType, companion);
                    declaration = declaration.withModifiers(accessible(declaration.getModifiers()))
                            .withBody(declaration.getBody().withStatements(ListUtils.map(
                            declaration.getBody().getStatements(), statement -> {
                                if (statement instanceof J.MethodDeclaration) {
                                    J.MethodDeclaration method = (J.MethodDeclaration) statement;
                                    return method.withModifiers(accessible(method.getModifiers()));
                                }
                                if (statement instanceof J.VariableDeclarations) {
                                    J.VariableDeclarations field = (J.VariableDeclarations) statement;
                                    return field.withModifiers(accessible(field.getModifiers()));
                                }
                                return statement;
                            })));
                    JavaType.FullyQualified supertype = c.getType().getSupertype();
                    List<TypeTree> parents = c.getImplements() == null ? emptyList() : c.getImplements();
                    List<TypeTree> superclass = ListUtils.filter(parents, parent -> TypeUtils.isOfType(parent.getType(), supertype));
                    fixture = fixture.withImplements(superclass.isEmpty() ? null : ListUtils.mapFirst(superclass,
                                    parent -> parent instanceof K.ConstructorInvocation ?
                                            ((K.ConstructorInvocation) parent).withPrefix(Space.EMPTY).withTypeTree(
                                                    ((K.ConstructorInvocation) parent).getTypeTree().withPrefix(Space.EMPTY)) :
                                            parent.withPrefix(parent.getPrefix().withWhitespace(""))))
                            .withBody(fixture.getBody().withStatements(singletonList(declaration.withPrefix(Space.format("\n")))));
                    if (fixture.getType() instanceof JavaType.Class) {
                        fixture = fixture.withType(((JavaType.Class) fixture.getType()).withSupertype(supertype));
                    }
                    lifted.add(fixture.withPrefix(Space.format("\n\n")));
                    UUID companionId = declaration.getId();
                    c = c.withBody(c.getBody().withStatements(ListUtils.filter(c.getBody().getStatements(),
                                    statement -> !statement.getId().equals(companionId))))
                            .withImplements(ListUtils.concatAll(singletonList(fixtureParent),
                                    ListUtils.filter(parents, parent -> !TypeUtils.isOfType(parent.getType(), supertype))));
                    c = c.getPadding().withImplements(c.getPadding().getImplements().withBefore(Space.SINGLE_SPACE));
                    if (c.getType() instanceof JavaType.Class) {
                        c = c.withType(((JavaType.Class) c.getType()).withSupertype(fixture.getType()));
                    }
                    Set<String> methods = new HashSet<>();
                    for (Statement statement : declaration.getBody().getStatements()) {
                        if (statement instanceof J.MethodDeclaration) {
                            methods.add(((J.MethodDeclaration) statement).getSimpleName());
                        }
                    }
                    c = (J.ClassDeclaration) new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                            J.Annotation a = super.visitAnnotation(annotation, ctx);
                            if (!TypeUtils.isOfClassType(a.getType(), "org.junit.jupiter.params.provider.MethodSource")) {
                                return a;
                            }
                            return a.withArguments(ListUtils.map(a.getArguments(), argument -> argument instanceof J.Literal &&
                                    methods.contains(((J.Literal) argument).getValue()) ?
                                    ((J.Literal) argument).withValue(fixtureType + "#" + ((J.Literal) argument).getValue())
                                            .withValueSource("\"" + fixtureType.replace("$", "\\$") +
                                                    "#" + ((J.Literal) argument).getValue() + "\"") : argument));
                        }
                    }.visitNonNull(c, ctx);
                }
                return c;
            }
        }.visitNonNull(classDecl, ctx, getCursor().getParentTreeCursor());
        cd = cd.withBody(cd.getBody().withStatements(ListUtils.concatAll(cd.getBody().getStatements(), lifted)));
        return maybeAutoFormat(classDecl, cd, ctx);
    }

    private static List<J.Modifier> accessible(List<J.Modifier> modifiers) {
        return ListUtils.filter(modifiers, modifier -> modifier.getType() != J.Modifier.Type.Private);
    }

    private static J.Identifier reference(Companion companion) {
        String name = companion.type.getClassName();
        return new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(),
                name.substring(name.lastIndexOf('.') + 1), companion.declaration.getType(), null);
    }

    private static class Companion {
        final J.ClassDeclaration declaration;
        final String originalType;
        final String name;
        final JavaType.FullyQualified type;
        final Set<String> extensions = new HashSet<>();

        Companion(J.ClassDeclaration declaration, JavaType.FullyQualified type) {
            this.declaration = declaration;
            this.originalType = declaration.getType().getFullyQualifiedName();
            this.name = "<companion>".equals(declaration.getSimpleName()) ? "Companion" : declaration.getSimpleName();
            this.type = type;
            for (Statement statement : declaration.getBody().getStatements()) {
                if (statement instanceof J.MethodDeclaration &&
                        statement.getMarkers().findFirst(Extension.class).isPresent()) {
                    extensions.add(((J.MethodDeclaration) statement).getSimpleName());
                }
            }
        }
    }
}
