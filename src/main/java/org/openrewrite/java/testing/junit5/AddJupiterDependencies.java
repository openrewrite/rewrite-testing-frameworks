/*
 * Copyright 2024 the original author or authors.
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
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.dependencies.AddDependency;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.maven.tree.ResolvedDependency;
import org.openrewrite.maven.tree.Scope;
import org.openrewrite.xml.tree.Xml;

import java.util.List;
import java.util.Optional;

@Value
@EqualsAndHashCode(callSuper = false)
public class AddJupiterDependencies extends ScanningRecipe<AddDependency.Accumulator> {
    @Override
    public String getDisplayName() {
        return "Add JUnit Jupiter dependencies";
    }

    @Override
    public String getDescription() {
        return "Adds JUnit Jupiter dependencies to a Maven or Gradle project. " +
               "Junit Jupiter can be added either with the artifact junit-jupiter, or both of junit-jupiter-api and junit-jupiter-engine. " +
               "This adds \"junit-jupiter\" dependency unless \"junit-jupiter-api\" or \"junit-jupiter-engine\" are already present.";
    }

    @Override
    public AddDependency.Accumulator getInitialValue(ExecutionContext ctx) {
        return addJupiterDependency().getInitialValue(ctx);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(AddDependency.Accumulator acc) {
        return addJupiterDependency().getScanner(acc);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(AddDependency.Accumulator acc) {
        AddJupiterGradle gv = new AddJupiterGradle(acc);
        AddJupiterMaven mv = new AddJupiterMaven(acc);
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx, Cursor parent) {
                if(!(tree instanceof SourceFile)) {
                    return tree;
                }
                SourceFile s = (SourceFile) tree;
                if(gv.isAcceptable(s, ctx)) {
                    s = (SourceFile) gv.visitNonNull(s, ctx);
                }
                if(mv.isAcceptable(s, ctx)) {
                    s = (SourceFile) mv.visitNonNull(s, ctx);
                }
                return s;
            }
        };
    }

    private static AddDependency addJupiterDependency() {
        return new AddDependency("org.junit.jupiter", "junit-jupiter", "5.x", null,
                "org.junit..*", null, null, null, null, "test",
                null, null, null, null);
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    private static class AddJupiterGradle extends GroovyIsoVisitor<ExecutionContext> {
        AddDependency.Accumulator acc;

        @Override
        public G.CompilationUnit visitCompilationUnit(G.CompilationUnit t, ExecutionContext ctx) {
            Optional<GradleProject> maybeGp = t.getMarkers().findFirst(GradleProject.class);
            if(!maybeGp.isPresent()) {
                return t;
            }
            GradleProject gp = maybeGp.get();
            GradleDependencyConfiguration trc = gp.getConfiguration("testRuntimeClasspath");
            if(trc == null) {
                return t;
            }
            ResolvedDependency jupiterApi = trc.findResolvedDependency("org.junit.jupiter", "junit-jupiter-api");
            if(jupiterApi == null) {
                t = (G.CompilationUnit) addJupiterDependency().getVisitor(acc)
                        .visitNonNull(t, ctx);
            }

            return t;
        }
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    private static class AddJupiterMaven extends MavenIsoVisitor<ExecutionContext> {
        AddDependency.Accumulator acc;
        @Override
        public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
            Xml.Document d = document;
            List<ResolvedDependency> jupiterApi = getResolutionResult().findDependencies("org.junit.jupiter", "junit-jupiter-api", Scope.Test);
            if(jupiterApi.isEmpty()) {
                d = (Xml.Document) addJupiterDependency().getVisitor(acc)
                        .visitNonNull(d, ctx);
            }
            return d;
        }
    }
}
