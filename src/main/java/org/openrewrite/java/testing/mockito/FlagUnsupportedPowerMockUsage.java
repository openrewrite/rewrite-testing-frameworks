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
package org.openrewrite.java.testing.mockito;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static java.util.Collections.emptyMap;

public class FlagUnsupportedPowerMockUsage extends Recipe {

    @Getter
    final String displayName = "Flag PowerMock usage that cannot be migrated to Mockito";

    @Getter
    final String description = "Adds a comment to PowerMock usages that cannot be migrated to Mockito, such as " +
            "`MemberModifier.suppress`, `PowerMockito.verifyNew` or stubbing private methods. Repositories with such " +
            "usage are left on PowerMock by `ReplacePowerMockito`, so these need to be migrated manually first.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.powermock..*", false), new JavaIsoVisitor<ExecutionContext>() {
            private Map<UUID, String> unsupported = emptyMap();

            @Override
            public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof JavaSourceFile) {
                    unsupported = UnsupportedPowerMockUsage.find((JavaSourceFile) tree, ctx);
                    if (unsupported.isEmpty()) {
                        return (J) tree;
                    }
                }
                return super.visit(tree, ctx);
            }

            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = super.visitBlock(block, ctx);
                return b.withStatements(ListUtils.map(b.getStatements(), statement -> flag(statement, statement)));
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                cd = flag(cd, cd.getExtends());
                if (cd.getImplements() != null) {
                    for (TypeTree implemented : cd.getImplements()) {
                        cd = flag(cd, implemented);
                    }
                }
                return cd;
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                String usage = unsupported.get(annotation.getId());
                return usage == null ? annotation : withComment(annotation, usage);
            }

            private <T extends J> T flag(T tree, @Nullable J searched) {
                if (searched == null) {
                    return tree;
                }
                Set<String> usages = new LinkedHashSet<>();
                new JavaIsoVisitor<Set<String>>() {
                    @Override
                    public J.Block visitBlock(J.Block nested, Set<String> p) {
                        return nested;
                    }

                    @Override
                    public J.Annotation visitAnnotation(J.Annotation annotation, Set<String> p) {
                        return annotation;
                    }

                    @Override
                    public @Nullable J preVisit(J tree, Set<String> p) {
                        String usage = unsupported.get(tree.getId());
                        if (usage != null) {
                            p.add(usage);
                        }
                        return tree;
                    }
                }.visit(searched, usages, getCursor());
                T flagged = tree;
                for (String usage : usages) {
                    flagged = withComment(flagged, usage);
                }
                return flagged;
            }

            private <T extends J> T withComment(T tree, String usage) {
                String comment = " " + usage + "; migrate it manually to replace PowerMock ";
                Space prefix = tree.getPrefix();
                for (Comment existing : prefix.getComments()) {
                    if (existing instanceof TextComment && ((TextComment) existing).getText().equals(comment)) {
                        return tree;
                    }
                }
                String precedingWhitespace = prefix.getComments().isEmpty() ? prefix.getWhitespace() :
                        prefix.getComments().get(prefix.getComments().size() - 1).getSuffix();
                int lineStart = precedingWhitespace.lastIndexOf('\n');
                String suffix = lineStart < 0 ? " " : precedingWhitespace.substring(lineStart);
                return tree.withPrefix(prefix.withComments(ListUtils.concat(prefix.getComments(),
                        new TextComment(true, comment, suffix, Markers.EMPTY))));
            }
        });
    }
}
