package org.openrewrite.java.testing.junit5;

import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.dependencies.search.DoesNotIncludeDependency;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.search.DoesNotUseType;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.marker.SearchResult;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class TestNgGuard extends ScanningRecipe<TestNgGuard.Accumulator> {
    @Override
    public String getDisplayName() {
        return "JUnit Jupiter migration from JUnit 4.x";
    }

    @Override
    public String getDescription() {
        return "Migrates JUnit 4.x tests to JUnit Jupiter.";
    }

    @Value
    public static class Accumulator {
        Set<JavaProject> projectsWithoutTestNgDependency;
        Set<JavaProject> projectsWithoutTestNgTypeUsage;
        Set<JavaProject> projectsWithTestNgDependency;
        Set<JavaProject> projectsWithTestNgTypeUsage;
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator(new HashSet<>(), new HashSet<>(), new HashSet<>(), new HashSet<>());
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            private final TreeVisitor<?, ExecutionContext> dnut = new DoesNotUseType("org.testng..*", true).getVisitor();
            private final TreeVisitor<?, ExecutionContext> ut = new UsesType<>("org.testng..*", true);
            private final TreeVisitor<?, ExecutionContext> dnid = new DoesNotIncludeDependency("org.testng", "testng*", null, null, null).getVisitor();

            @Override
            public Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                assert tree != null;
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                SourceFile s = (SourceFile) tree;
                if (dnid.isAcceptable(s, ctx)) {
                    Tree after = dnid.visit(tree, ctx);
                    if (after != tree) {
                        tree
                            .getMarkers()
                            .findFirst(JavaProject.class)
                            .ifPresent(acc.projectsWithoutTestNgDependency::add);
                    } else {
                        tree
                            .getMarkers()
                            .findFirst(JavaProject.class)
                            .ifPresent(acc.projectsWithTestNgDependency::add);
                    }
                } else if (ut.isAcceptable(s, ctx)) {
                    Tree after = ut.visit(tree, ctx);
                    if (after == tree) {
                        tree
                            .getMarkers()
                            .findFirst(JavaProject.class)
                            .ifPresent(acc.projectsWithoutTestNgTypeUsage::add);
                    } else {
                        tree
                            .getMarkers()
                            .findFirst(JavaProject.class)
                            .ifPresent(acc.projectsWithTestNgTypeUsage::add);
                    }
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            private final TreeVisitor<?, ExecutionContext> ut = new UsesType<>("org.testng..*", true);
            private final TreeVisitor<?, ExecutionContext> dnid = new DoesNotIncludeDependency("org.testng", "testng*", null, null, null).getVisitor();

            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                return ut.isAcceptable(sourceFile, ctx) || dnid.isAcceptable(sourceFile, ctx);
            }

            @Override
            public Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                assert tree != null;
                Optional<JavaProject> maybeJp = tree.getMarkers().findFirst(JavaProject.class);
                if (!maybeJp.isPresent()) {
                    return tree;
                }
                JavaProject jp = maybeJp.get();
                boolean pwoTngd = acc.getProjectsWithoutTestNgDependency().contains(jp);
                boolean pwoTngtu = acc.getProjectsWithoutTestNgTypeUsage().contains(jp);
                boolean pwTngd = acc.getProjectsWithTestNgDependency().contains(jp);
                boolean pwTngtu = acc.getProjectsWithTestNgTypeUsage().contains(jp);
                if (pwTngtu || pwTngd) {
                    return tree;
                }
                if (!pwoTngd && !pwoTngtu) {
                    return tree;
                }
                if (
                    (pwoTngd || acc.getProjectsWithoutTestNgDependency().isEmpty())
                    && (pwoTngtu || acc.getProjectsWithoutTestNgTypeUsage().isEmpty())
                ) {
                    return SearchResult.found(tree);
                }
                return tree;
            }
        };
    }

    //    @Override
//    public TreeVisitor<?, ExecutionContext> getVisitor() {
//        return new TreeVisitor<Tree, ExecutionContext>() {
//            private final TreeVisitor<?, ExecutionContext> dnut = new DoesNotUseType("org.testng..*", true).getVisitor();
//            private final TreeVisitor<?, ExecutionContext> dnid = new DoesNotIncludeDependency("org.testng", "testng*", null, null, null).getVisitor();
//
//            @Override
//            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext executionContext) {
//                return dnut.isAcceptable(sourceFile, executionContext) || dnid.isAcceptable(sourceFile, executionContext);
//            }
//
//            @Override
//            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
//                if (!(tree instanceof SourceFile)) {
//                    return tree;
//                }
//                SourceFile s = (SourceFile) tree;
//                SourceFile after = s;
//                if (dnid.isAcceptable(s, ctx)) {
//                    after = (SourceFile) dnid.visitNonNull(s, ctx);
//                } else if (dnut.isAcceptable(s, ctx)) {
//                    after = (SourceFile) dnut.visitNonNull(s, ctx);
//                }
//                if (after == s) {
//                    return s;
//                }
//                return SearchResult.found(after);
//            }
//        };
//    }
}
