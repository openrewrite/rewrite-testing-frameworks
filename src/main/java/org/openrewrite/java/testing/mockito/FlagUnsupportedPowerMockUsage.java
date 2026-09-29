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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.trait.Comments;

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
                return b.withStatements(ListUtils.map(b.getStatements(), statement -> flag(getCursor(), statement)));
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                return flag(getCursor().getParentOrThrow(), super.visitClassDeclaration(classDecl, ctx));
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                return flag(getCursor().getParentOrThrow(), annotation);
            }

            private <T extends J> T flag(Cursor parent, T searched) {
                Set<String> usages = new LinkedHashSet<>();
                new JavaIsoVisitor<Set<String>>() {
                    @Override
                    public @Nullable J preVisit(J tree, Set<String> p) {
                        // Annotations are flagged on themselves, and nested blocks on their own statements
                        if (tree instanceof J.Annotation && !(searched instanceof J.Annotation)) {
                            stopAfterPreVisit();
                            return tree;
                        }
                        String usage = unsupported.get(tree.getId());
                        if (usage != null) {
                            p.add(usage);
                        }
                        if (tree instanceof J.Block) {
                            stopAfterPreVisit();
                        }
                        return tree;
                    }
                }.visit(searched, usages, parent);
                T flagged = searched;
                for (String usage : usages) {
                    flagged = Comments.of(new Cursor(parent, flagged)).multilineComment(
                            " TODO " + usage + "; migrate it manually to replace PowerMock ",
                            Comments.Placement.BEFORE, lastLineOf(flagged.getPrefix()));
                }
                return flagged;
            }

            private String lastLineOf(Space prefix) {
                String whitespace = prefix.getComments().isEmpty() ? prefix.getWhitespace() :
                        prefix.getComments().get(prefix.getComments().size() - 1).getSuffix();
                int lineStart = whitespace.lastIndexOf('\n');
                return lineStart < 0 ? " " : whitespace.substring(lineStart);
            }
        });
    }
}
