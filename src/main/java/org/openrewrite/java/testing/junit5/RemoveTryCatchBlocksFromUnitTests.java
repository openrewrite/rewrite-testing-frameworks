/*
 * Copyright 2023 the original author or authors.
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
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;

import java.util.*;
import java.util.stream.Collectors;

public class RemoveTryCatchBlocksFromUnitTests extends Recipe {
    private static final List<String> TEST_PATTERNS = Arrays.asList(
            "@org.junit.jupiter.api.Test",
            "@org.junit.jupiter.api.RepeatedTest",
            "@org.junit.jupiter.params.ParameterizedTest",
            "@org.junit.Test"
    );

    private static final List<AnnotationMatcher> MATCHERS = TEST_PATTERNS.stream().map(AnnotationMatcher::new).collect(Collectors.toList());

    @Override
    public String getDisplayName() {
        return "Unit test should throw exceptions instead of using `try-catch` blocks";
    }

    @Override
    public String getDescription() {
        return "When the code under test in a unit test throws an exception, the test itself fails. " +
                "Therefore, there is no need to surround the tested code with a try-catch.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-3658");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new RemoveTryCatchBlocksFromUnitsTestsVisitor();
    }

    private static class RemoveTryCatchBlocksFromUnitsTestsVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration mi, ExecutionContext ctx) {
            J.MethodDeclaration m = super.visitMethodDeclaration(mi, ctx);
            List<J.Annotation> annotations = m.getAllAnnotations();
            if (annotations.stream().noneMatch(ann -> MATCHERS.stream().anyMatch(matcher -> matcher.matches(ann)))) {
                return m;
            }

            if (m.getBody() == null) {
                return m;
            }

            List<Statement> statements = m.getBody().getStatements();
            J.Block newBody;
            List<NameTree> exceptions = new ArrayList<>();

            for (Statement s : statements) {
                if (s instanceof J.Try) {
                    newBody = ((J.Try) s).getBody();
                    m = m.withBody(newBody);
                }else if (s instanceof J.Throw) {
                    // ! how can I get the NameTree type from the exception here?
                    exceptions.add((NameTree) ((J.Throw) s).getException());
                    m = m.withThrows(exceptions);
                }
            }

            maybeRemoveImport("org.junit.Assert.fail");

            return maybeAutoFormat(mi, m, ctx);
        }
    }
}
