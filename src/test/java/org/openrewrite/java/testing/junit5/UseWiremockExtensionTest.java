package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("JUnitMalformedDeclaration")
@Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/170")
class UseWiremockExtensionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("junit", "wiremock-jre8"))
          .recipe(new UseWiremockExtension());
    }

    @Test
    void optionsArg() {
        //language=java
        rewriteRun(
          java(
            """
              import com.github.tomakehurst.wiremock.junit.WireMockRule;
              import org.junit.Rule;
              
              import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
              
              class Test {
                  @Rule
                  public WireMockRule wm = new WireMockRule(options().dynamicHttpsPort());
              }
              """,
            """
              import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
              import org.junit.jupiter.api.extension.RegisterExtension;
              
              import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
              
              class Test {
                  @RegisterExtension
                  public WireMockExtension wm = WireMockExtension.newInstance().options(options().dynamicHttpsPort()).build();
              }
              """
          )
        );
    }

    @Test
    void failOnUnmatchedRequests() {
        //language=java
        rewriteRun(
          java(
            """
              import com.github.tomakehurst.wiremock.junit.WireMockRule;
              import org.junit.Rule;
              
              import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
              
              class Test {
                  @Rule
                  public WireMockRule wm = new WireMockRule(options().dynamicHttpsPort(), false);
              }
              """,
            """
              import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
              import org.junit.jupiter.api.extension.RegisterExtension;
              
              import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
              
              class Test {
                  @RegisterExtension
                  public WireMockExtension wm = WireMockExtension.newInstance().options(options().dynamicHttpsPort()).failOnUnmatchedRequests(false).build();
              }
              """
          )
        );
    }

    @Test
    void port() {
        //language=java
        rewriteRun(
          java(
            """
              import com.github.tomakehurst.wiremock.junit.WireMockRule;
              import org.junit.Rule;
              
              class Test {
                  @Rule
                  public WireMockRule wm = new WireMockRule(7001);
              }
              """,
            """
              import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
              import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
              import org.junit.jupiter.api.extension.RegisterExtension;
              
              class Test {
                  @RegisterExtension
                  public WireMockExtension wm = WireMockExtension.newInstance().options(WireMockConfiguration.options().port(7001)).build();
              }
              """
          )
        );
    }

    @Test
    void portAndHttpsPort() {
        //language=java
        rewriteRun(
          java(
            """
              import com.github.tomakehurst.wiremock.junit.WireMockRule;
              import org.junit.Rule;
              
              class Test {
                  @Rule
                  public WireMockRule wm = new WireMockRule(7001, 7002);
              }
              """,
            """
              import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
              import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
              import org.junit.jupiter.api.extension.RegisterExtension;
              
              class Test {
                  @RegisterExtension
                  public WireMockExtension wm = WireMockExtension.newInstance().options(WireMockConfiguration.options().port(7001).httpsPort(7002)).build();
              }
              """
          )
        );
    }
}
