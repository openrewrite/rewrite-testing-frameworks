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
import org.openrewrite.Validated;
import org.openrewrite.internal.lang.NonNull;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.FindTypes;
import org.openrewrite.java.tree.J;
import org.openrewrite.maven.AddDependency;
import org.openrewrite.maven.MavenVisitor;
import org.openrewrite.maven.tree.Maven;

import static org.openrewrite.Validated.required;

public class MaybeAddJUnit5Dependencies extends Recipe {

    @NonNull
    private String version = "5.x";

    public void setVersion(String version) {
        this.version = version;
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new FindJUnitVisitor();
    }

    @Override
    public Validated validate() {
        return required("version", version);
    }

    private class FindJUnitVisitor extends JavaVisitor<ExecutionContext> {
        @Override
        public J visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
            boolean junitReferencesExist =
                    !FindTypes.find(cu, "junit.framework.Test").isEmpty()
                    || !FindTypes.find(cu, "org.junit.jupiter.api.Test").isEmpty();
            if (junitReferencesExist) {
                doAfterVisit(new AddDependencies());
            }
            return cu;
        }
    }

    private class AddDependencies extends Recipe {

        @Override
        protected TreeVisitor<?, ExecutionContext> getVisitor() {
            return new AddDependenciesVisitor();
        }

        private class AddDependenciesVisitor extends MavenVisitor<ExecutionContext> {
            @Override
            public Maven visitMaven(Maven maven, ExecutionContext ctx) {
                AddDependency addJunitApi = new AddDependency("org.junit.jupiter", "junit-jupiter-api", version);
                addJunitApi.setScope("test");
                doAfterVisit(addJunitApi);

                AddDependency addJunitJupiterEngine = new AddDependency("org.junit.jupiter", "junit-jupiter-engine", version);
                addJunitJupiterEngine.setScope("test");
                doAfterVisit(addJunitJupiterEngine);

                return super.visitMaven(maven, ctx);
            }
        }
    }
}
