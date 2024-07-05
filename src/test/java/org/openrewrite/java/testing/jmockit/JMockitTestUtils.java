package org.openrewrite.java.testing.jmockit;

import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;

public class JMockitTestUtils {

    static final String MOCKITO_CORE_DEPENDENCY = "mockito-core-3.12";
    static final String JUNIT_5_JUPITER_DEPENDENCY = "junit-jupiter-api-5.9";
    static final String JMOCKIT_DEPENDENCY = "jmockit-1.49";
    static final String MOCKITO_JUPITER_DEPENDENCY = "mockito-junit-jupiter-3.12";

    static void setDefaultParserSettings(RecipeSpec spec) {
        setParserSettings(spec, JUNIT_5_JUPITER_DEPENDENCY,
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
          .recipeFromResource(
            "/META-INF/rewrite/jmockit.yml",
            "org.openrewrite.java.testing.jmockit.JMockitToMockito"
          );
    }
}
