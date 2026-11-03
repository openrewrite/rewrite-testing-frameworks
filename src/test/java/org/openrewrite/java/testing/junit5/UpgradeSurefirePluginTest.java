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
package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Tree;
import org.openrewrite.marker.BuildTool;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeSurefirePluginTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.testing.junit5.UpgradeSurefirePlugin");
    }

    @DocumentExample
    @Test
    void bumpSurefireOnOlderMavenVersions() {
        rewriteRun(
          pomXml(
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>dev.ted</groupId>
                  <artifactId>testcontainer-migrate</artifactId>
                  <version>0.0.1</version>
              </project>
              """,
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>dev.ted</groupId>
                  <artifactId>testcontainer-migrate</artifactId>
                  <version>0.0.1</version>
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.apache.maven.plugins</groupId>
                              <artifactId>maven-surefire-plugin</artifactId>
                              <version>3.2.5</version>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """,
            spec -> spec.markers(new BuildTool(Tree.randomId(), BuildTool.Type.Maven, "3.5.4"))
          )
        );
    }

    @Test
    void upgradeOlderSurefireOnOlderMavenVersions() {
        rewriteRun(
          pomXml(
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>dev.ted</groupId>
                  <artifactId>testcontainer-migrate</artifactId>
                  <version>0.0.1</version>
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.apache.maven.plugins</groupId>
                              <artifactId>maven-surefire-plugin</artifactId>
                              <version>2.19.1</version>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """,
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>dev.ted</groupId>
                  <artifactId>testcontainer-migrate</artifactId>
                  <version>0.0.1</version>
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.apache.maven.plugins</groupId>
                              <artifactId>maven-surefire-plugin</artifactId>
                              <version>3.2.5</version>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """,
            spec -> spec.markers(new BuildTool(Tree.randomId(), BuildTool.Type.Maven, "3.5.0"))
          )
        );
    }

    @Test
    void noJunitPlatformSurefireProviderForSurefire3() {
        rewriteRun(
          pomXml(
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>dev.ted</groupId>
                  <artifactId>testcontainer-migrate</artifactId>
                  <version>0.0.1</version>
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.apache.maven.plugins</groupId>
                              <artifactId>maven-surefire-plugin</artifactId>
                              <version>3.5.2</version>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """,
            spec -> spec.markers(new BuildTool(Tree.randomId(), BuildTool.Type.Maven, "3.5.0"))
          )
        );
    }
}
