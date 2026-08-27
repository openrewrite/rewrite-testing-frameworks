/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.java.testing.pmd;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.xml.Assertions.xml;

public class PmdXml6to7MigrationTest
  implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext()))
          .recipe(new PmdXml6to7Migration());
    }

    @DocumentExample
    @Test
    void shouldMigrateXml6toXml7() {
        //language=java
        rewriteRun(
          xml(
"""
<?xml version="1.0" encoding="UTF-8"?>
<ruleset name="exampleruleset"
         xmlns="http://sourceforge.net"
         xmlns:xsi="http://w3.org"
         xsi:schemaLocation="http://sourceforge.net https://sourceforge.net">

    <description>This is a description</description>
    <rule ref="category/java/errorprone.xml/DontImportSun" />
    <rule ref="category/java/errorprone.xml/EmptyCatchBlock" />
</ruleset>
""",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <ruleset name="exampleruleset"
                     xmlns="http://sourceforge.net"
                     xmlns:xsi="http://w3.org"
                     xsi:schemaLocation="http://sourceforge.net https://sourceforge.net">

                <description>This is a description</description>
                <rule ref="category/java/errorprone.xml/EmptyCatchBlock" />
                <rule ref="category/java/errorprone.xml/UnsupportedJdkApiUsage" />
            </ruleset>
            """
        ));
    }

    @Test
    void noChanges_on_empty_ruleset() {
        //language=java
        rewriteRun(
          xml(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <ruleset name="exampleruleset"
                     xmlns="http://sourceforge.net"
                     xmlns:xsi="http://w3.org"
                     xsi:schemaLocation="http://sourceforge.net https://sourceforge.net">
                <description>This is a description</description>
            </ruleset>
            """
          ));
    }

    @Test
    void preserve_custom_rule() {
        //language=java
        rewriteRun(
          xml(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <ruleset name="exampleruleset"
                     xmlns="http://sourceforge.net"
                     xmlns:xsi="http://w3.org"
                     xsi:schemaLocation="http://sourceforge.net https://sourceforge.net">
                <description>This is a description</description>
                <rule ref="aaa/bbb/mycorp/MyCustomRule" />
            </ruleset>
            """
          ));
    }

    @Test
    void replacementMovesToAnotherCategory() {
        rewriteRun(
          xml(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <ruleset name="exampleruleset">
                <rule ref="category/java/errorprone.xml/EmptyIfStmt" />
                <rule ref="category/java/errorprone.xml/MissingBreakInSwitch" />
            </ruleset>
            """,
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <ruleset name="exampleruleset">
                <rule ref="category/java/codestyle.xml/EmptyControlStatement" />
                <rule ref="category/java/errorprone.xml/ImplicitSwitchFallThrough" />
            </ruleset>
            """
          ));
    }

    @Test
    void dropRuleRemovedWithoutReplacement() {
        rewriteRun(
          xml(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <ruleset name="exampleruleset">
                <rule ref="category/java/codestyle.xml/AvoidFinalLocalVariable" />
                <rule ref="category/java/errorprone.xml/EmptyCatchBlock" />
            </ruleset>
            """,
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <ruleset name="exampleruleset">
                <rule ref="category/java/errorprone.xml/EmptyCatchBlock" />
            </ruleset>
            """
          ));
    }

    @Test
    void severalRemovedRulesShareOneReplacement() {
        rewriteRun(
          xml(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <ruleset name="exampleruleset">
                <rule ref="category/java/codestyle.xml/DuplicateImports" />
                <rule ref="category/java/bestpractices.xml/UnusedImports" />
            </ruleset>
            """,
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <ruleset name="exampleruleset">
                <rule ref="category/java/codestyle.xml/UnnecessaryImport" />
            </ruleset>
            """
          ));
    }

    @Test
    void doNotAddReplacementTheRulesetAlreadySelects() {
        rewriteRun(
          xml(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <ruleset name="exampleruleset">
                <rule ref="category/java/design.xml/CyclomaticComplexity" />
                <rule ref="category/java/design.xml/StdCyclomaticComplexity" />
            </ruleset>
            """,
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <ruleset name="exampleruleset">
                <rule ref="category/java/design.xml/CyclomaticComplexity" />
            </ruleset>
            """
          ));
    }

    @Test
    void retainNestedPropertiesOnReplacedRule() {
        rewriteRun(
          xml(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <ruleset name="exampleruleset">
                <rule ref="category/java/design.xml/ExcessiveMethodLength">
                    <properties>
                        <property name="minimum" value="100" />
                    </properties>
                </rule>
            </ruleset>
            """,
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <ruleset name="exampleruleset">
                <rule ref="category/java/design.xml/NcssCount">
                    <properties>
                        <property name="minimum" value="100" />
                    </properties>
                </rule>
            </ruleset>
            """
          ));
    }

    @Test
    void leaveRulesPmd7SplitsAcrossSeveralSuccessors() {
        rewriteRun(
          xml(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <ruleset name="exampleruleset">
                <rule ref="category/java/codestyle.xml/VariableNamingConventions" />
                <rule ref="category/java/performance.xml/IntegerInstantiation" />
            </ruleset>
            """
          ));
    }

    @Test
    void leaveRulesetTagThatIsNotTheDocumentRoot() {
        rewriteRun(
          xml(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <configuration>
                <ruleset name="notpmd">
                    <rule ref="category/java/errorprone.xml/DontImportSun" />
                </ruleset>
            </configuration>
            """
          ));
    }

    @Test
    void rulesStillValidInPmd7AreLeftAlone() {
        rewriteRun(
          xml(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <ruleset name="exampleruleset">
                <rule ref="category/java/bestpractices.xml" />
                <rule ref="category/java/errorprone.xml/EmptyCatchBlock" />
            </ruleset>
            """
          ));
    }
}
