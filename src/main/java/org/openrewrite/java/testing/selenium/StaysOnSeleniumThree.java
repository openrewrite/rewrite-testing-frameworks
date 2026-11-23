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
import org.openrewrite.java.dependencies.search.ModuleHasDependency;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.marker.SearchResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Arrays.asList;

@Value
@EqualsAndHashCode(callSuper = false)
public class StaysOnSeleniumThree extends ScanningRecipe<StaysOnSeleniumThree.Accumulator> {

    /**
     * Libraries whose releases before these only run on Selenium 3, as group id, artifact id and versions.
     */
    private static final List<String[]> SELENIUM_THREE_LIBRARIES = asList(
            new String[]{"io.appium", "java-client", "[1,8)"},
            new String[]{"com.codeborne", "selenide*", "[1,6)"},
            new String[]{"org.jboss.arquillian.extension", "arquillian-drone-*", "[1,3)"},
            new String[]{"org.jboss.arquillian.graphene", "graphene-*", "[1,3)"},
            new String[]{"com.vaadin", "vaadin-testbench*", "[1,8)"});

    /**
     * The packages of `selenium-server` and `selenium-server-standalone` that the client API does not include.
     */
    private static final List<String> SERVER_PACKAGES = asList(
            "org.openqa.grid..*",
            "org.openqa.selenium.remote.server..*",
            "org.openqa.selenium.server..*");

    @Option(displayName = "Invert marking",
            description = "Mark the files of the repositories that can move to Selenium 4 instead.",
            required = false)
    @Nullable
    Boolean invertMarking;

    String displayName = "Repository stays on Selenium 3";

    String description = "Intended as a precondition, this marks every file of a repository in which a module " +
            "depends on a library release that only runs on Selenium 3, Appium `java-client` before 8, Selenide " +
            "before 6, Arquillian Drone or Graphene before 3 or Vaadin TestBench before 8, or whose code uses the " +
            "Selenium Grid or server classes that Selenium 4 replaced. The modules of a repository share Selenium's " +
            "version through parent POMs and BOMs, so that moving the others to Selenium 4 would move these too.";

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        Accumulator acc = new Accumulator();
        for (int i = 0; i < SELENIUM_THREE_LIBRARIES.size(); i++) {
            acc.modulesOnLibrary.add(new HashSet<>());
        }
        return acc;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        List<TreeVisitor<?, ExecutionContext>> libraries = new ArrayList<>();
        for (int i = 0; i < SELENIUM_THREE_LIBRARIES.size(); i++) {
            String[] library = SELENIUM_THREE_LIBRARIES.get(i);
            libraries.add(new ModuleHasDependency(library[0], library[1], null, library[2], null, null)
                    .getScanner(acc.modulesOnLibrary.get(i)));
        }
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                if (!acc.usesServer && tree instanceof JavaSourceFile) {
                    for (String serverPackage : SERVER_PACKAGES) {
                        if (new UsesType<>(serverPackage, false).visit(tree, ctx) != tree) {
                            acc.usesServer = true;
                            break;
                        }
                    }
                }
                for (TreeVisitor<?, ExecutionContext> library : libraries) {
                    library.visit(tree, ctx);
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        boolean staysOnSeleniumThree = acc.usesServer;
        for (Set<JavaProject> modules : acc.modulesOnLibrary) {
            staysOnSeleniumThree |= !modules.isEmpty();
        }
        boolean mark = staysOnSeleniumThree != Boolean.TRUE.equals(invertMarking);
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                return mark && tree instanceof SourceFile ? SearchResult.found(tree) : tree;
            }
        };
    }

    public static class Accumulator {
        boolean usesServer;

        /**
         * For each library of {@link #SELENIUM_THREE_LIBRARIES}, the modules that depend on a release of it that only
         * runs on Selenium 3.
         */
        final List<Set<JavaProject>> modulesOnLibrary = new ArrayList<>();
    }
}
