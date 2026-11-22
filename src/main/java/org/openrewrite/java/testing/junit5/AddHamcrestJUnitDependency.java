/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.testing.junit5;

import lombok.Getter;
import org.openrewrite.Recipe;
import org.openrewrite.java.dependencies.AddDependency;

import java.util.List;

import static java.util.Collections.singletonList;

public class AddHamcrestJUnitDependency extends Recipe {

    @Getter
    final String displayName = "Add Hamcrest JUnit dependency";

    @Getter
    final String description = "Add `org.hamcrest:hamcrest-junit` only to the modules that use its `org.hamcrest.junit` types, " +
            "such as `MatcherAssume`. Run this after `Assume#assumeThat` has been migrated.";

    @Override
    public List<Recipe> getRecipeList() {
        return singletonList(new AddDependency(
                "org.hamcrest",
                "hamcrest-junit",
                "2.x",
                null,
                "org.hamcrest.junit..*",
                null,
                null,
                null,
                null,
                "test",
                null,
                null,
                null,
                true
        ));
    }
}
