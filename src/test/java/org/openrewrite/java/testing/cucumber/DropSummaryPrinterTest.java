package org.openrewrite.java.testing.cucumber;

import org.junit.jupiter.api.Test;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.version;

@Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/264")
class DropSummaryPrinterTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new DropSummaryPrinter());
        spec.parser(JavaParser.fromJavaVersion()
                .logCompilationWarningsAndErrors(true)
                .classpath("cucumber-plugin"));
    }

    @Test
    void should_replace_summary_printer_with_plugin() {
        rewriteRun(version(java("""
                package com.example.app;

                import io.cucumber.plugin.SummaryPrinter;

                public class CucumberJava8Definitions implements SummaryPrinter {
                }""", """
                package com.example.app;

                import io.cucumber.plugin.Plugin;

                public class CucumberJava8Definitions implements Plugin {
                }"""),
                17));
    }

    @Test
    void should_not_duplicate_plugin() {
        rewriteRun(version(java("""
                package com.example.app;

                import io.cucumber.plugin.Plugin;
                import io.cucumber.plugin.SummaryPrinter;

                public class CucumberJava8Definitions implements Plugin, SummaryPrinter {
                }""", """
                package com.example.app;

                import io.cucumber.plugin.Plugin;

                public class CucumberJava8Definitions implements Plugin {
                }"""),
                17));
    }

}
