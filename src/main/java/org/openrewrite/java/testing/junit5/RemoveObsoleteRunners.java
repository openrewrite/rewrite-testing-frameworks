/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.testing.junit5;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveAnnotation;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import java.util.List;

@Value
@EqualsAndHashCode(callSuper = false)
public class RemoveObsoleteRunners extends Recipe {
    @Option(displayName = "Obsolete Runners",
            description = "The fully qualified class names of the JUnit 4 runners to be removed.",
            example = "org.junit.runners.JUnit4")
    List<String> obsoleteRunners;

    @Override
    public String getDisplayName() {
        return "Remove JUnit 4 `@RunWith` annotations that do not require an `@ExtendsWith` replacement";
    }

    @Override
    public String getDescription() {
        return "Some JUnit 4 `@RunWith` annotations do not require replacement with an equivalent JUnit Jupiter `@ExtendsWith` annotation. " +
                "This can be used to remove those runners that either do not have a JUnit Jupiter equivalent or do not require a replacement as part of JUnit 4 to 5 migration.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        @SuppressWarnings("unchecked") TreeVisitor<?, ExecutionContext> check =
                Preconditions.or(obsoleteRunners.stream().map(r -> new UsesType<>(r, false)).toArray(UsesType[]::new));
        return Preconditions.check(check, new RemoveObsoleteRunnersVisitor());
    }

    public class RemoveObsoleteRunnersVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            for (String runner : obsoleteRunners) {
                //noinspection ConstantConditions
                doAfterVisit(new RemoveAnnotation("@org.junit.runner.RunWith(" + runner + ".class)").getVisitor());
                maybeRemoveImport(runner);
            }
            maybeRemoveImport("org.junit.runner.RunWith");
            return classDecl;
        }
    }
}
