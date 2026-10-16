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

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.marker.ImplicitReturn;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.kotlin.KotlinTemplate;
import org.openrewrite.kotlin.marker.AnonymousFunction;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.marker.Markers;

import java.util.UUID;

import static java.util.Collections.emptyList;
import static org.openrewrite.Tree.randomId;

public final class KotlinJUnit5Support {
    private static final boolean KOTLIN_AVAILABLE = kotlinAvailable();

    private KotlinJUnit5Support() {
    }

    static boolean available() {
        return KOTLIN_AVAILABLE;
    }

    private static boolean kotlinAvailable() {
        try {
            Class.forName("org.openrewrite.kotlin.tree.K$CompilationUnit", false, KotlinJUnit5Support.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError unavailable) {
            return false;
        }
    }

    public static boolean isKotlin(Object tree) {
        return tree != null && "org.openrewrite.kotlin.tree.K$CompilationUnit".equals(tree.getClass().getName());
    }

    public static boolean isKotlin(Cursor cursor) {
        return cursor.getPathAsStream().anyMatch(KotlinJUnit5Support::isKotlin);
    }

    static JavaTemplate annotationTemplate(Cursor cursor, ExecutionContext ctx, String java, String kotlin,
                                           String[] imports, String... classpath) {
        return template(cursor, ctx, java, kotlin, imports, new String[0], false, classpath);
    }

    static JavaTemplate annotationTemplate(Cursor cursor, ExecutionContext ctx, String annotation,
                                           String[] imports, String... classpath) {
        return annotationTemplate(cursor, ctx, annotation, annotation.replace(".class", "::class"), imports, classpath);
    }

    static JavaTemplate template(Cursor cursor, ExecutionContext ctx, String java, String kotlin,
                                 String[] imports, String[] staticImports, boolean contextSensitive, String... classpath) {
        if (isKotlin(cursor)) {
            return KotlinTemplates.template(ctx, kotlin, imports, staticImports, classpath);
        }
        JavaTemplate.Builder builder = JavaTemplate.builder(java).imports(imports).staticImports(staticImports)
                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, classpath));
        return (contextSensitive ? builder.contextSensitive() : builder).build();
    }

    static J.Block lambdaBody(J.Block body, String assertion, Cursor parent, ExecutionContext ctx) {
        return KotlinTemplates.lambdaBody(body, assertion, parent, ctx);
    }

    static TreeVisitor<?, ExecutionContext> parameterizedVisitor() {
        return KotlinTemplates.parameterizedVisitor();
    }

    static TreeVisitor<?, ExecutionContext> junitParamsVisitor() {
        return KotlinTemplates.junitParamsVisitor();
    }

    static TreeVisitor<?, ExecutionContext> nestedFixturesVisitor(UUID enclosingClass) {
        return KotlinTemplates.nestedFixturesVisitor(enclosingClass);
    }

    static J.ClassDeclaration normalizeCompanions(J.ClassDeclaration clazz) {
        return clazz.withBody(clazz.getBody().withStatements(ListUtils.map(clazz.getBody().getStatements(), member -> {
            if (!(member instanceof J.ClassDeclaration) || ((J.ClassDeclaration) member).getModifiers().stream()
                    .noneMatch(modifier -> "companion".equals(modifier.getKeyword()))) {
                return member;
            }
            J.ClassDeclaration companion = (J.ClassDeclaration) member;
            return companion.withBody(companion.getBody().withStatements(ListUtils.mapFirst(
                    companion.getBody().getStatements(), first -> {
                        first = first.withPrefix(first.getPrefix().withWhitespace("\n" + first.getPrefix().getIndent()));
                        if (first instanceof J.MethodDeclaration) {
                            J.MethodDeclaration method = (J.MethodDeclaration) first;
                            return method.withLeadingAnnotations(ListUtils.mapFirst(method.getLeadingAnnotations(),
                                    annotation -> annotation.withPrefix(annotation.getPrefix().withWhitespace(""))));
                        }
                        return first;
                    })));
        })));
    }

    private static final class KotlinTemplates {
        private static TreeVisitor<?, ExecutionContext> parameterizedVisitor() {
            return new KotlinParameterizedRunnerVisitor();
        }

        private static TreeVisitor<?, ExecutionContext> junitParamsVisitor() {
            return new KotlinJUnitParamsRunnerVisitor();
        }

        private static TreeVisitor<?, ExecutionContext> nestedFixturesVisitor(UUID enclosingClass) {
            return new KotlinNestedStaticFixturesVisitor(enclosingClass);
        }

        private static JavaTemplate template(ExecutionContext ctx, String template, String[] imports,
                                             String[] staticImports, String[] classpath) {
            return KotlinTemplate.builder(template).parser(KotlinParser.builder().classpathFromResources(ctx, classpath))
                    .imports(imports).staticImports(staticImports).build();
        }

        private static J.Block lambdaBody(J.Block body, String assertion, Cursor parent, ExecutionContext ctx) {
            return (J.Block) new LambdaBodyVisitor(assertion).visitNonNull(body, ctx, parent);
        }

        private static final class LambdaBodyVisitor extends KotlinIsoVisitor<ExecutionContext> {
            private final String assertion;

            private LambdaBodyVisitor(String assertion) {
                this.assertion = assertion;
            }

            @Override
            public K.Return visitReturn(K.Return statement, ExecutionContext ctx) {
                K.Return r = super.visitReturn(statement, ctx);
                return r.getLabel() == null && !r.getMarkers().findFirst(ImplicitReturn.class).isPresent() ?
                        r.withLabel(new J.Identifier(randomId(), Space.EMPTY,
                        Markers.EMPTY, emptyList(), assertion, null, null)) : r;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                return method;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration clazz, ExecutionContext ctx) {
                return clazz;
            }

            @Override
            public J.Lambda visitLambda(J.Lambda lambda, ExecutionContext ctx) {
                return lambda.getMarkers().findFirst(AnonymousFunction.class).isPresent() ? lambda :
                        super.visitLambda(lambda, ctx);
            }
        }
    }
}
