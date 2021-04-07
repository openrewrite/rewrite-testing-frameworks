package org.openrewrite.java.testing.junit5

import org.junit.jupiter.api.Test
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class UpdateMockWebServerTest : JavaRecipeTest {

    override val parser: JavaParser = JavaParser.fromJavaVersion()
        .classpath("junit", "mockwebserver")
        .build()
    override val recipe: Recipe
        get() = UpdateMockWebServer()

    @Test
    fun mockWebServerRuleUpdated() = assertChanged(
        before = """
            import okhttp3.mockwebserver.MockWebServer;
            import org.junit.Rule;
            class A {
                @Rule
                public MockWebServer server = new MockWebServer();
            }
        """,
        after = """
            import okhttp3.mockwebserver.MockWebServer;
            import org.junit.jupiter.api.AfterEach;
        
            import java.io.IOException;
        
            class A {
            
                public MockWebServer server = new MockWebServer();
            
                @AfterEach
                void afterEachTest() throws IOException {
                    server.close();
                }
            }
        """
    )

    @Test
    fun mockWebServerRuleUpdatedExistingAfterEachStatement() = assertChanged(
        before = """
            import okhttp3.mockwebserver.MockWebServer;
            import org.junit.Rule;
            import org.junit.jupiter.api.AfterEach;
            
            class A {
                @Rule
                public MockWebServer server = new MockWebServer();
                
                @AfterEach
                void afterEachTest() {
                    String s = "s";
                }
            }
        """,
        after = """
            import okhttp3.mockwebserver.MockWebServer;
            import org.junit.jupiter.api.AfterEach;
            
            import java.io.IOException;
        
            class A {
            
                public MockWebServer server = new MockWebServer();
            
                @AfterEach
                void afterEachTest() throws IOException {
                    String s = "s";
                    server.close();
                }
            }
        """
    )
}