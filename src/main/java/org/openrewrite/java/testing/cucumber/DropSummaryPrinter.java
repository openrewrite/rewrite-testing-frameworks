/*
 * Copyright 2022 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.cucumber;

import java.time.Duration;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveImport;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

public class DropSummaryPrinter extends Recipe {

    private static final String IO_CUCUMBER_PLUGIN_SUMMARY_PRINTER = "io.cucumber.plugin.SummaryPrinter";
    private static final String IO_CUCUMBER_PLUGIN_PLUGIN = "io.cucumber.plugin.Plugin";

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>(IO_CUCUMBER_PLUGIN_SUMMARY_PRINTER);
    }

    @Override
    public String getDisplayName() {
        return "Drop SummaryPrinter.";
    }

    @Override
    public String getDescription() {
        return "Replace SummaryPrinter with Plugin, if not already present.";
    }

    @Override
    public @Nullable Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new DropSummaryPrinterVisitor();
    }

    static final class DropSummaryPrinterVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, ExecutionContext p) {
            J.ClassDeclaration classDeclaration = super.visitClassDeclaration(cd, p);
            boolean implementsSummaryPrinter = Stream.of(classDeclaration.getImplements())
                    .filter(Objects::nonNull)
                    .flatMap(Collection::stream)
                    .anyMatch(t -> TypeUtils.isOfClassType(t.getType(), IO_CUCUMBER_PLUGIN_SUMMARY_PRINTER));
            boolean alreadyImplementsPlugin = Stream.of(classDeclaration.getImplements())
                    .filter(Objects::nonNull)
                    .flatMap(Collection::stream)
                    .anyMatch(t -> TypeUtils.isOfClassType(t.getType(), IO_CUCUMBER_PLUGIN_PLUGIN));
            if (!implementsSummaryPrinter) {
                return classDeclaration;
            }
            doAfterVisit(new ChangeType(
                    IO_CUCUMBER_PLUGIN_SUMMARY_PRINTER,
                    IO_CUCUMBER_PLUGIN_PLUGIN,
                    true));
            doAfterVisit(new RemoveImport<>(IO_CUCUMBER_PLUGIN_SUMMARY_PRINTER));
            return classDeclaration.withImplements(ListUtils.map(classDeclaration.getImplements(), i -> {
                // Remove duplicate implements
                if (TypeUtils.isOfClassType(i.getType(), IO_CUCUMBER_PLUGIN_SUMMARY_PRINTER)
                        && alreadyImplementsPlugin) {
                    return null;
                }
                return i;
            }));
        }
    }

}
