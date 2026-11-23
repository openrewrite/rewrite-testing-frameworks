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
import org.openrewrite.java.marker.JavaVersion;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.marker.SearchResult;

import java.util.List;

import static java.util.Arrays.asList;

@Value
@EqualsAndHashCode(callSuper = false)
public class StaysOnSelenium413 extends ScanningRecipe<StaysOnSelenium413.Accumulator> {

    /**
     * Types that Selenium 4.13.0 still has and later Selenium 4 releases removed.
     */
    private static final List<String> REMOVED_TYPES = asList(
            // Removed in 4.17.0, in favor of `EventFiringDecorator` and `WebDriverListener`
            "org.openqa.selenium.support.events.EventFiringWebDriver",
            "org.openqa.selenium.support.events.WebDriverEventListener",
            "org.openqa.selenium.support.events.AbstractWebDriverEventListener",
            "org.openqa.selenium.firefox.FirefoxBinary",
            "org.openqa.selenium.firefox.Extension",
            "org.openqa.selenium.firefox.FileExtension",
            "org.openqa.selenium.firefox.ClasspathExtension",
            "org.openqa.selenium.ContextAware",
            "org.openqa.selenium.NoSuchContextException",
            "org.openqa.selenium.WebDriverInfo",
            "org.openqa.selenium.mobile.NetworkConnection",
            "org.openqa.selenium.remote.mobile.RemoteNetworkConnection",
            "org.openqa.selenium.logging.LocalLogs",
            "org.openqa.selenium.logging.NeedsLocalLogs",
            "org.openqa.selenium.logging.SessionLogs",
            "org.openqa.selenium.logging.profiler..*",
            "org.openqa.selenium.os.CommandLine",
            "org.openqa.selenium.os.OsProcess",
            "org.openqa.selenium.remote.RemoteStatus");

    @Option(displayName = "Invert marking",
            description = "Mark the files of the repositories that move to the latest Selenium 4 instead.",
            required = false)
    @Nullable
    Boolean invertMarking;

    String displayName = "Repository stays on Selenium 4.13";

    String description = "Intended as a precondition, this marks every file of a repository that moves to Selenium " +
            "4.13.0 rather than the latest Selenium 4: one whose oldest Java version is older than 11, which later " +
            "releases require, or whose code uses a type that 4.13.0 still has and a later release removed, such as " +
            "`EventFiringWebDriver`, `WebDriverEventListener` and `FirefoxBinary`. A repository without Java sources " +
            "is marked in neither case.";

    public static class Accumulator {
        @Nullable
        Integer oldestJavaVersion;

        boolean usesRemovedApi;
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof JavaSourceFile) {
                    tree.getMarkers().findFirst(JavaVersion.class).ifPresent(javaVersion -> {
                        if (acc.oldestJavaVersion == null || javaVersion.getMajorVersion() < acc.oldestJavaVersion) {
                            acc.oldestJavaVersion = javaVersion.getMajorVersion();
                        }
                    });
                    if (!acc.usesRemovedApi) {
                        for (String removedType : REMOVED_TYPES) {
                            if (new UsesType<>(removedType, false).visit(tree, ctx) != tree) {
                                acc.usesRemovedApi = true;
                                break;
                            }
                        }
                    }
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        boolean mark = acc.oldestJavaVersion != null &&
                       (acc.oldestJavaVersion < 11 || acc.usesRemovedApi) != Boolean.TRUE.equals(invertMarking);
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                return mark && tree instanceof SourceFile ? SearchResult.found(tree) : tree;
            }
        };
    }
}
