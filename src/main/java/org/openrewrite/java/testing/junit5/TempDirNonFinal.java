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
package org.openrewrite.java.testing.junit5;

import lombok.Getter;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.Modifier.Type;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import java.time.Duration;
import java.util.Iterator;

import static java.util.Collections.emptyList;

public class TempDirNonFinal extends Recipe {

    private static final AnnotationMatcher TEMPDIR_ANNOTATION_MATCHER = new AnnotationMatcher(
            "@org.junit.jupiter.api.io.TempDir");

    @Getter
    final String displayName = "Make `@TempDir` fields non final";

    @Getter
    final String description = "Make JUnit 5's `org.junit.jupiter.api.io.TempDir` fields non final.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.junit.jupiter.api.io.TempDir", false), new TempDirVisitor());
    }

    private static class TempDirVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
            J.VariableDeclarations varDecls = super.visitVariableDeclarations(multiVariable, ctx);
            if (varDecls.getLeadingAnnotations().stream().anyMatch(TEMPDIR_ANNOTATION_MATCHER::matches) &&
                varDecls.hasModifier(Type.Final) && isField(getCursor())) {
                if (KotlinJUnit5Support.isKotlin(getCursor())) {
                    boolean lateinit = varDecls.getVariables().stream().anyMatch(variable -> variable.getInitializer() == null);
                    varDecls = varDecls.withModifiers(ListUtils.map(varDecls.getModifiers(), modifier ->
                            modifier.getType() == Type.Final ? modifier.withType(Type.LanguageExtension).withKeyword("var")
                                    .withPrefix(modifier.getPrefix().withWhitespace(" ")) : modifier));
                    if (lateinit) {
                        varDecls = varDecls.withLeadingAnnotations(ListUtils.map(varDecls.getLeadingAnnotations(), annotation ->
                                new AnnotationMatcher("@kotlin.jvm.JvmField").matches(annotation) ? null : annotation));
                        varDecls = varDecls.withModifiers(ListUtils.concat(
                                new J.Modifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, "lateinit", Type.LanguageExtension,
                                        emptyList()), varDecls.getModifiers()));
                    }
                    return maybeAutoFormat(multiVariable, varDecls, ctx, getCursor().getParentOrThrow());
                }
                return maybeAutoFormat(varDecls, varDecls.withModifiers(ListUtils
                                .map(varDecls.getModifiers(), modifier -> modifier.getType() == Type.Final ? null : modifier)),
                        ctx, getCursor().getParentOrThrow());
            }
            return varDecls;
        }
    }

    // copied from org.openrewrite.java.search.FindFieldsOfType.isField(Cursor), should probably become part of the API
    private static boolean isField(Cursor cursor) {
        Iterator<Object> path = cursor.getPath();
        while (path.hasNext()) {
            Object o = path.next();
            if (o instanceof J.MethodDeclaration) {
                return false;
            }
            if (o instanceof J.ClassDeclaration) {
                return true;
            }
        }
        return true;
    }

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(1);
}
