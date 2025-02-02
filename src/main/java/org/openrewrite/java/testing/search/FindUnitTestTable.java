/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.java.testing.search;

import lombok.Value;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.Recipe;

 public class FindUnitTestTable extends DataTable<FindUnitTestTable.Row> {
     public FindUnitTestTable(Recipe recipe) {
         super(recipe,
                 "Methods in unit tests",
                 "Method declarations used in unit tests");
     }

     @Value
     public static class Row {
         @Column(displayName = "Full method name",
                 description = "The fully qualified name of the method declaration")
         String fullyQualifiedMethodName;

         @Column(displayName = "Method name",
                 description = "The name of the method declaration")
         String methodName;

         @Column(displayName = "Method invocation",
                 description = "How the method declaration is used as method invocation in a unit test.")
         String methodInvocationExample;

         @Column(displayName = "Name of test",
                 description = "The name of the unit test where the method declaration is used.")
         String nameOfTest;

         @Column(displayName = "Location of test",
                 description = "The location of the unit test where the method declaration is used.")
         String locationOfTest;
     }
 }

