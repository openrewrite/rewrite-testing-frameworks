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
import org.openrewrite.java.dependencies.DependencyInsight;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.marker.SearchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Arrays.asList;

@Value
@EqualsAndHashCode(callSuper = false)
public class StaysOnSeleniumThree extends ScanningRecipe<AtomicBoolean> {

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
    public AtomicBoolean getInitialValue(ExecutionContext ctx) {
        return new AtomicBoolean();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(AtomicBoolean staysOnSeleniumThree) {
        List<TreeVisitor<?, ExecutionContext>> libraries = new ArrayList<>();
        for (String[] library : SELENIUM_THREE_LIBRARIES) {
            libraries.add(new DependencyInsight(library[0], library[1], library[2], null).getVisitor());
        }
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile) || staysOnSeleniumThree.get()) {
                    return tree;
                }
                if (tree instanceof JavaSourceFile) {
                    for (String serverPackage : SERVER_PACKAGES) {
                        if (new UsesType<>(serverPackage, false).visit(tree, ctx) != tree) {
                            staysOnSeleniumThree.set(true);
                            return tree;
                        }
                    }
                }
                // Build files, among which a Gradle build script is a `JavaSourceFile` as well
                for (TreeVisitor<?, ExecutionContext> library : libraries) {
                    if (library.isAcceptable((SourceFile) tree, ctx) && library.visit(tree, ctx) != tree) {
                        staysOnSeleniumThree.set(true);
                        return tree;
                    }
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(AtomicBoolean staysOnSeleniumThree) {
        boolean mark = staysOnSeleniumThree.get() != Boolean.TRUE.equals(invertMarking);
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                return mark && tree instanceof SourceFile ? SearchResult.found(tree) : tree;
            }
        };
    }
}
