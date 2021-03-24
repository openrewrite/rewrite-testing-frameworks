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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RemoveObsoleteRunners extends Recipe {
    @Override
    public String getDisplayName() {
        return "Remove JUnit4 @RunWith annotations with no JUnit5 equivalent";
    }

    @Override
    public String getDescription() {
        return "Some JUnit4 @RunWith() annotations do not require replacement with an equivalent JUnit 5 @ExtendsWith() annotation. " +
        "This removes @RunWith(JUnit4.class) and @RunWith(BlockJUnit4ClassRunner.class) annotations as part of JUnit 4 to 5 migration.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new RemoveObsoleteRunnersVisitor();
    }

    public static class RemoveObsoleteRunnersVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final List<String> obsoleteRunners = Arrays.asList(
                "org.junit.runners.JUnit4", "org.junit.runners.BlockJUnit4ClassRunner");

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
            List<J.Annotation> filteredAnnotations = null;
            for (String runner : obsoleteRunners) {
                for (J.Annotation runWith : FindAnnotations.find(classDecl.withBody(null), "@org.junit.runner.RunWith(" + runner + ".class)")) {
                    if(filteredAnnotations == null) {
                        filteredAnnotations = new ArrayList<>(classDecl.getLeadingAnnotations());
                    }
                    filteredAnnotations.remove(runWith);
                    maybeRemoveImport(runner);
                }
            }
            if(filteredAnnotations != null) {
                classDecl = classDecl.withLeadingAnnotations(filteredAnnotations);
                maybeRemoveImport("org.junit.runner.RunWith");
            }
            return classDecl;
        }
    }
}
