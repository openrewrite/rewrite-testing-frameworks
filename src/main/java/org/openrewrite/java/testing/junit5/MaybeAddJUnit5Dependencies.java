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

import org.openrewrite.AutoConfigure;
import org.openrewrite.CompositeRefactorVisitor;
import org.openrewrite.Validated;
import org.openrewrite.java.JavaRefactorVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.maven.AddDependency;
import org.openrewrite.maven.MavenRefactorVisitor;
import org.openrewrite.maven.tree.Maven;

import static org.openrewrite.Validated.required;

@AutoConfigure
public class MaybeAddJUnit5Dependencies extends CompositeRefactorVisitor {
    private String version = "5.x";

    private boolean junitReferencesExist = false;

    public MaybeAddJUnit5Dependencies() {
        addVisitor(new FindJUnit());
        addVisitor(new AddDependencies());
    }

    public void setVersion(String version) {
        this.version = version;
    }

    @Override
    public Validated validate() {
        return required("version", version);
    }

    private class FindJUnit extends JavaRefactorVisitor {
        @Override
        public J visitCompilationUnit(J.CompilationUnit cu) {
            junitReferencesExist = cu.hasType("junit.framework.Test") ||
                    cu.hasType("org.junit.jupiter.api.Test");
            return cu;
        }
    }

    private class AddDependencies extends MavenRefactorVisitor {
        @Override
        public Maven visitMaven(Maven maven) {
            if (junitReferencesExist) {
                AddDependency addJunitApi = new AddDependency();
                addJunitApi.setGroupId("org.junit.jupiter");
                addJunitApi.setArtifactId("junit-jupiter-api");
                addJunitApi.setVersion(version);
                addJunitApi.setScope("test");
                andThen(addJunitApi);

                AddDependency addJunitJupiterEngine = new AddDependency();
                addJunitJupiterEngine.setGroupId("org.junit.jupiter");
                addJunitJupiterEngine.setArtifactId("junit-jupiter-engine");
                addJunitJupiterEngine.setVersion(version);
                addJunitJupiterEngine.setScope("test");
                andThen(addJunitJupiterEngine);
            }

            return super.visitMaven(maven);
        }
    }
}
