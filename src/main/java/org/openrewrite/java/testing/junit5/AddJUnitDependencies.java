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
import org.openrewrite.internal.lang.NonNull;
import org.openrewrite.maven.MavenVisitor;
import org.openrewrite.maven.tree.Maven;

public class AddJUnitDependencies extends Recipe {

    @NonNull
    private String version = "5.x";

    public void setVersion(String version) {
        this.version = version;
    }

    @Override
    public String getDisplayName() {
        return "Add JUnit Dependencies";
    }

    @Override
    public String getDescription() {
        return "Adds Junit Dependencies";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new AddJUnitDependenciesVisitor();
    }

    private class AddJUnitDependenciesVisitor extends MavenVisitor {
        @Override
        public Maven visitMaven(Maven maven, ExecutionContext ctx) {
            if (Boolean.TRUE.equals(ctx.pollMessage(FindJUnit5.JUNIT_REFS_EXIST_KEY))) {
                maybeAddDependency(
                        "org.junit.jupiter",
                        "junit-jupiter-api",
                        version,
                        null,
                        "test",
                        null
                );
                maybeAddDependency(
                        "org.junit.jupiter",
                        "junit-jupiter-engine",
                        version,
                        null,
                        "test",
                        null
                );
            }
            return super.visitMaven(maven, ctx);
        }
    }
}
