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
package org.openrewrite.java.testing.jmockit;

import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;

@SuppressWarnings("SpellCheckingInspection")
public class JMockitTestUtils {

    static final String MOCKITO_CORE_DEPENDENCY = "mockito-core-3.12";
    static final String JUNIT_5_JUPITER_DEPENDENCY = "junit-jupiter-api-5";
    static final String JUNIT_4_DEPENDENCY = "junit-4";
    static final String JMOCKIT_DEPENDENCY = "jmockit-1.49";
    static final String MOCKITO_JUPITER_DEPENDENCY = "mockito-junit-jupiter-3.12";

    static void setDefaultParserSettings(RecipeSpec spec) {
        setParserSettings(spec,
          JUNIT_5_JUPITER_DEPENDENCY,
          JMOCKIT_DEPENDENCY,
          MOCKITO_CORE_DEPENDENCY,
          MOCKITO_JUPITER_DEPENDENCY);
    }

    static void setParserSettings(RecipeSpec spec, String... javaParserTestDependencies) {
        spec.parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(),
              javaParserTestDependencies
            ))
          .recipeFromResources("org.openrewrite.java.testing.jmockit.JMockitToMockito");
    }
}
