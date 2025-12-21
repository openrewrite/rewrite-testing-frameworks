package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class HandleExternalResourceRulesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new HandleExternalResourceRules())
          .parser(
            JavaParser.fromJavaVersion()
              .classpathFromResources(new InMemoryExecutionContext(),
                "junit-4",
                "junit-jupiter-api-5",
                "junit-jupiter-migrationsupport-5",
                "grpc-testing"));
    }

    @Test
    void emptyTestClassWithExternalResourceSupport() {
        rewriteRun(
          // language=java
          java(
            """
            import org.junit.Rule;
            import org.junit.jupiter.migrationsupport.rules.ExternalResourceSupport;
            import org.junit.jupiter.api.extension.ExtendWith;
            import io.grpc.testing.GrpcCleanupRule;

            @ExtendWith(ExternalResourceSupport.class)
            public class EmptyTestClassWithExternalResourceSupport {
                @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();
            }
            """));
    }

    @Test
    void emptyTestClassWithNonSupportedRule() {
        rewriteRun(
          // language=java
          java(
            // no change expected since since ErrorCollector is not of type ExternalResource
            """
            import org.junit.Rule;
            import org.junit.rules.ErrorCollector;

            public class EmptyTestClassWithNonSupportedRule {
                @Rule public final ErrorCollector errorCollector = new ErrorCollector();
            }
            """));
    }

    @Test
    void emptyTestClassWithExternalResourceRule() {
        rewriteRun(
          // language=java
          java(
            """
            import org.junit.Rule;
            import io.grpc.testing.GrpcCleanupRule;

            public class EmptyTestClassWithExternalResourceRule {
                @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();
            }
            """,
            """
            import org.junit.Rule;
            import org.junit.jupiter.api.extension.ExtendWith;
            import org.junit.jupiter.migrationsupport.rules.ExternalResourceSupport;
            import io.grpc.testing.GrpcCleanupRule;

            @ExtendWith(ExternalResourceSupport.class)
            public class EmptyTestClassWithExternalResourceRule {
                @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();
            }
            """));
    }

    @Test
    void emptyTestClassWithAnotherExternalResourceRule() {
        rewriteRun(
          // language=java
          java(
            """
            import org.junit.Rule;
            import org.junit.rules.TemporaryFolder;

            public class EmptyTestClassWithAnotherExternalResourceRule {
                @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();
            }
            """,
            """
            import org.junit.Rule;
            import org.junit.jupiter.api.extension.ExtendWith;
            import org.junit.jupiter.migrationsupport.rules.ExternalResourceSupport;
            import org.junit.rules.TemporaryFolder;

            @ExtendWith(ExternalResourceSupport.class)
            public class EmptyTestClassWithAnotherExternalResourceRule {
                @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();
            }
            """));
    }

    @Test
    void testClassWithExternalResourceRuleAndExtendWithAnnotation() {
        rewriteRun(
          // language=java
          java(
            // no change expected since the class already has @ExtendWith(ExternalResourceSupport.class))
            """
            import org.junit.Rule;
            import org.junit.jupiter.api.Test;
            import org.junit.jupiter.api.extension.ExtendWith;
            import org.junit.jupiter.migrationsupport.rules.ExternalResourceSupport;
            import org.junit.rules.TemporaryFolder;

            @ExtendWith(ExternalResourceSupport.class)
            public class TestClassWithExternalResourceRule {
                @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();
                @Test
                public void test() {
                    temporaryFolder.newFolder();
                }
            }
            """));
    }

    @Test
    void testClassWithExternalResourceRuleAndExtendWithAnnotationButNoRule() {
        rewriteRun(
          // language=java
          java(
            // no change since the class has no rules
            """
            import org.junit.Rule;
            import org.junit.jupiter.api.Test;
            import org.junit.jupiter.api.extension.ExtendWith;
            import org.junit.jupiter.migrationsupport.rules.ExternalResourceSupport;
            import org.junit.rules.TemporaryFolder;

            @ExtendWith(ExternalResourceSupport.class)
            public class TestClassWithExternalResourceRuleAndExtendWithAnnotationButNoRule {
                public final TemporaryFolder temporaryFolder = new TemporaryFolder();
                @Test
                public void test() {
                }
            }
            """));
    }

    @Test
    void testClassWithExternalResourceRuleAndTestMethod() {
        rewriteRun(
          // language=java
          java(
            // before
            """
            import org.junit.Rule;
            import org.junit.jupiter.api.Test;
            import org.junit.jupiter.api.extension.ExtendWith;
            import org.junit.jupiter.migrationsupport.rules.ExternalResourceSupport;
            import org.junit.rules.TemporaryFolder;

            public class TestClassWithExternalResourceRuleAndTestMethod {
                @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();
                @Test
                public void test() {
                }
            }
            """,
            """
            import org.junit.Rule;
            import org.junit.jupiter.api.Test;
            import org.junit.jupiter.api.extension.ExtendWith;
            import org.junit.jupiter.migrationsupport.rules.ExternalResourceSupport;
            import org.junit.rules.TemporaryFolder;

            @ExtendWith(ExternalResourceSupport.class)
            public class TestClassWithExternalResourceRuleAndTestMethod {
                @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();
                @Test
                public void test() {
                }
            }
            """));
    }

    @Test
    void testClassWithExternResourceRuleAsInitializer() {
        rewriteRun(
          // language=java
          java(
            // before
            """
            import org.junit.Rule;
            import org.junit.rules.TestRule;
            import org.junit.rules.TemporaryFolder;

            public class TestClassWithExternResourceRuleAsInitializer {
                @Rule public final TestRule temporaryFolder = new TemporaryFolder();
            }
            """,
            // after
            """
            import org.junit.Rule;
            import org.junit.jupiter.api.extension.ExtendWith;
            import org.junit.jupiter.migrationsupport.rules.ExternalResourceSupport;
            import org.junit.rules.TestRule;
            import org.junit.rules.TemporaryFolder;

            @ExtendWith(ExternalResourceSupport.class)
            public class TestClassWithExternResourceRuleAsInitializer {
                @Rule public final TestRule temporaryFolder = new TemporaryFolder();
            }
            """));
    }

    @Test
    void testClassWithMethodReturnTypeExternalResourceRule() {
        rewriteRun(
          // language=java
          java(
            // before
            """
            import org.junit.Rule;
            import org.junit.jupiter.api.Test;
            import org.junit.rules.TemporaryFolder;
            public class TestClassWithMethodReturnTypeExternalResourceRule {

                @Rule
                public TemporaryFolder createTemporaryFolder() {
                    return new TemporaryFolder();
                }

                @Test
                public void test() {
                }
            }
            """,
            """
            import org.junit.Rule;
            import org.junit.jupiter.api.Test;
            import org.junit.jupiter.api.extension.ExtendWith;
            import org.junit.jupiter.migrationsupport.rules.ExternalResourceSupport;
            import org.junit.rules.TemporaryFolder;

            @ExtendWith(ExternalResourceSupport.class)
            public class TestClassWithMethodReturnTypeExternalResourceRule {

                @Rule
                public TemporaryFolder createTemporaryFolder() {
                    return new TemporaryFolder();
                }

                @Test
                public void test() {
                }
            }
            """));
    }

    @Test
    void testClassWithMethodReturnTypeNonExternalResourceRule() {
        rewriteRun(
          // language=java
          java(
            //no change expected since the method return type is not an ExternalResourceRule
            """
            import org.junit.Rule;
            import org.junit.jupiter.api.Test;
            import org.junit.rules.ErrorCollector;
            public class TestClassWithMethodReturnTypeNonExternalResourceRule {

                @Rule
                public ErrorCollector createErrorCollector() {
                    return new ErrorCollector();
                }

                @Test
                public void test() {
                }
            }
            """));
    }
}
