/*
 * Copyright 2020 the original author or authors.
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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.List;

@Value
@EqualsAndHashCode(callSuper = true)
public class RemoveObsoleteRunners extends Recipe {
    @Option(displayName = "Obsolete Runners",
            description = "The fully qualified class names of the JUnit4 runners to be removed.",
            example = "org.junit.runners.JUnit4")
    List<String> obsoleteRunners;

    @Override
    public String getDisplayName() {
        return "Remove JUnit 4 `@RunWith` annotations that do not require an `@ExtendsWith` replacement.";
    }

    @Override
    public String getDescription() {
        return "Some JUnit4 `@RunWith` annotations do not require replacement with an equivalent JUnit Jupiter `@ExtendsWith` annotation. " +
                "This can be used to remove those runners that either do not have a JUnit Jupiter equivalent or do not require a replacement as part of JUnit 4 to 5 migration.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
                for (String runner : obsoleteRunners) {
                    doAfterVisit(new UsesType<>(runner));
                }
                return cu;
            }
        };
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new RemoveObsoleteRunnersVisitor();
    }

    public class RemoveObsoleteRunnersVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
            List<J.Annotation> filteredAnnotations = null;
            for (String runner : obsoleteRunners) {
                //noinspection ConstantConditions
                for (J.Annotation runWith : FindAnnotations.find(classDecl.withBody(null), "@org.junit.runner.RunWith(" + runner + ".class)")) {
                    if (filteredAnnotations == null) {
                        filteredAnnotations = new ArrayList<>(classDecl.getLeadingAnnotations());
                    }
                    filteredAnnotations.remove(runWith);
                    maybeRemoveImport(runner);
                }
            }
            if (filteredAnnotations != null) {
                classDecl = classDecl.withLeadingAnnotations(filteredAnnotations);
                maybeRemoveImport("org.junit.runner.RunWith");
            }
            return classDecl;
        }
    }
}
