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
package org.openrewrite.java.testing.selenium;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.marker.SearchResult;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.Arrays.asList;

@Value
@EqualsAndHashCode(callSuper = false)
public class ModuleUsesSeleniumServer extends ScanningRecipe<Set<JavaProject>> {

    /**
     * The packages of `selenium-server` and `selenium-server-standalone` that the client API does not include.
     */
    private static final List<String> SERVER_PACKAGES = asList(
            "org.openqa.grid..*",
            "org.openqa.selenium.remote.server..*",
            "org.openqa.selenium.server..*");

    @Option(displayName = "Invert marking",
            description = "Mark the files of the modules whose code does not use the Selenium Grid or server classes instead.",
            required = false)
    @Nullable
    Boolean invertMarking;

    String displayName = "Module uses the Selenium Grid or server";

    String description = "Intended as a precondition, this marks every file of a module whose code uses the Selenium " +
            "Grid or server classes of `selenium-server` or `selenium-server-standalone`, which Selenium 4 replaced. " +
            "A module that only uses the client API can depend on `selenium-java` instead.";

    @Override
    public Set<JavaProject> getInitialValue(ExecutionContext ctx) {
        return new HashSet<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Set<JavaProject> serverModules) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof JavaSourceFile) {
                    JavaSourceFile sourceFile = (JavaSourceFile) tree;
                    Optional<JavaProject> project = sourceFile.getMarkers().findFirst(JavaProject.class);
                    if (project.isPresent() && !serverModules.contains(project.get()) && usesServer(sourceFile, ctx)) {
                        serverModules.add(project.get());
                    }
                }
                return tree;
            }
        };
    }

    private static boolean usesServer(JavaSourceFile sourceFile, ExecutionContext ctx) {
        for (String serverPackage : SERVER_PACKAGES) {
            if (new UsesType<>(serverPackage, false).visit(sourceFile, ctx) != sourceFile) {
                return true;
            }
        }
        return false;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Set<JavaProject> serverModules) {
        boolean invert = Boolean.TRUE.equals(invertMarking);
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile) {
                    boolean usesServer = tree.getMarkers().findFirst(JavaProject.class)
                            .map(serverModules::contains)
                            .orElse(false);
                    if (usesServer != invert) {
                        return SearchResult.found(tree);
                    }
                }
                return tree;
            }
        };
    }
}
