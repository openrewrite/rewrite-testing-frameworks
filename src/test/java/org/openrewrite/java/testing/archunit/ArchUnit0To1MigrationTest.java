/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.archunit;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;

public class ArchUnit0To1MigrationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "archunit-0.23.1"))
          .recipe(Environment.builder()
            .scanRuntimeClasspath()
            .build()
            .activateRecipes("org.openrewrite.java.testing.archunit.ArchUnit0to1Migration"));
    }

    @Test
    void shouldMigrateMavenDependency() {
        rewriteRun(
          mavenProject("project",
            pomXml(
              """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <dependencies>
                    <dependency>
                        <groupId>com.tngtech.archunit</groupId>
                        <artifactId>archunit-junit5</artifactId>
                        <version>0.23.1</version>
                        <scope>test</scope>
                    </dependency>
                  </dependencies>
                </project>
                """,
              """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <dependencies>
                    <dependency>
                        <groupId>com.tngtech.archunit</groupId>
                        <artifactId>archunit-junit5</artifactId>
                        <version>1.2.0</version>
                        <scope>test</scope>
                    </dependency>
                  </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void shouldUseGetClassesInPackageTree() {
        //language=java
        rewriteRun(
          java(
            """
              import com.tngtech.archunit.core.domain.JavaPackage;
              import java.util.Set;
              import com.tngtech.archunit.core.domain.JavaClass;
              
              public class ArchUnitTest {
                  public Set<JavaClass> sample(JavaPackage javaPackage) {
                      return javaPackage.getAllClasses();
                  }
              }
              """,
            """
              import com.tngtech.archunit.core.domain.JavaPackage;
              import java.util.Set;
              import com.tngtech.archunit.core.domain.JavaClass;
              
              public class ArchUnitTest {
                  public Set<JavaClass> sample(JavaPackage javaPackage) {
                      return javaPackage.getClassesInPackageTree();
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldUseGetSubpackagesInTree() {
        //language=java
        rewriteRun(
          java(
            """
              import com.tngtech.archunit.core.domain.JavaPackage;
              import java.util.Set;
              import com.tngtech.archunit.core.domain.JavaClass;
              
              public class ArchUnitTest {
                  public Set<JavaClass> sample(JavaPackage javaPackage) {
                      return javaPackage.getAllSubpackages();
                  }
              }
              """,
            """
              import com.tngtech.archunit.core.domain.JavaPackage;
              import java.util.Set;
              import com.tngtech.archunit.core.domain.JavaClass;
              
              public class ArchUnitTest {
                  public Set<JavaClass> sample(JavaPackage javaPackage) {
                      return javaPackage.getSubpackagesInTree();
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldUseGetClassDependenciesFromThisPackageTree() {
        //language=java
        rewriteRun(
          java(
            """
              import com.tngtech.archunit.core.domain.JavaPackage;
              import java.util.Set;
              import com.tngtech.archunit.core.domain.Dependency;
              
              public class ArchUnitTest {
                  public Set<Dependency> sample(JavaPackage javaPackage) {
                      return javaPackage.getClassDependenciesFromSelf();
                  }
              }
              """,
            """
              import com.tngtech.archunit.core.domain.JavaPackage;
              import java.util.Set;
              import com.tngtech.archunit.core.domain.Dependency;
              
              public class ArchUnitTest {
                  public Set<Dependency> sample(JavaPackage javaPackage) {
                      return javaPackage.getClassDependenciesFromThisPackageTree();
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldUseGetClassDependenciesToThisPackageTree() {
        //language=java
        rewriteRun(
          java(
            """
              import com.tngtech.archunit.core.domain.JavaPackage;
              import java.util.Set;
              import com.tngtech.archunit.core.domain.Dependency;
              
              public class ArchUnitTest {
                  public Set<Dependency> sample(JavaPackage javaPackage) {
                      return javaPackage.getClassDependenciesToSelf();
                  }
              }
              """,
            """
              import com.tngtech.archunit.core.domain.JavaPackage;
              import java.util.Set;
              import com.tngtech.archunit.core.domain.Dependency;
              
              public class ArchUnitTest {
                  public Set<Dependency> sample(JavaPackage javaPackage) {
                      return javaPackage.getClassDependenciesToThisPackageTree();
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldUseGetPackageDependenciesFromThisPackageTree() {
        //language=java
        rewriteRun(
          java(
            """
              import com.tngtech.archunit.core.domain.JavaPackage;
              import java.util.Set;
              import com.tngtech.archunit.core.domain.JavaPackage;
              
              public class ArchUnitTest {
                  public Set<JavaPackage> sample(JavaPackage javaPackage) {
                      return javaPackage.getPackageDependenciesFromSelf();
                  }
              }
              """,
            """
              import com.tngtech.archunit.core.domain.JavaPackage;
              import java.util.Set;
              import com.tngtech.archunit.core.domain.JavaPackage;
              
              public class ArchUnitTest {
                  public Set<JavaPackage> sample(JavaPackage javaPackage) {
                      return javaPackage.getPackageDependenciesFromThisPackageTree();
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldUseGetPackageDependenciesToThisPackageTree() {
        //language=java
        rewriteRun(
          java(
            """
              import com.tngtech.archunit.core.domain.JavaPackage;
              import java.util.Set;
              import com.tngtech.archunit.core.domain.JavaPackage;
              
              public class ArchUnitTest {
                  public Set<JavaPackage> sample(JavaPackage javaPackage) {
                      return javaPackage.getPackageDependenciesToSelf();
                  }
              }
              """,
            """
              import com.tngtech.archunit.core.domain.JavaPackage;
              import java.util.Set;
              import com.tngtech.archunit.core.domain.JavaPackage;
              
              public class ArchUnitTest {
                  public Set<JavaPackage> sample(JavaPackage javaPackage) {
                      return javaPackage.getPackageDependenciesToThisPackageTree();
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldUseTraversePackageTree() {
        //language=java
        rewriteRun(
          java(
            """
              import com.tngtech.archunit.core.domain.JavaPackage;
              import static com.tngtech.archunit.core.domain.JavaPackage.ClassVisitor;
              
              public class ArchUnitTest {
                  public void sample(JavaPackage javaPackage, ClassVisitor visitor) {
                      javaPackage.accept(null, visitor);
                  }
              }
              """,
            """
              import com.tngtech.archunit.core.domain.JavaPackage;
              import static com.tngtech.archunit.core.domain.JavaPackage.ClassVisitor;
              
              public class ArchUnitTest {
                  public void sample(JavaPackage javaPackage, ClassVisitor visitor) {
                      javaPackage.traversePackageTree(null, visitor);
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldUsePlantUmlRulesPackage() {
        //language=java
        rewriteRun(
          java(
            """
              import com.tngtech.archunit.library.plantuml.PlantUmlArchCondition;
              
              public class ArchUnitTest {
                  public void sample(PlantUmlArchCondition condition) {
                      condition.ignoreDependencies("origin", "");
                  }
              }
              """,
            """
              import com.tngtech.archunit.library.plantuml.rules.PlantUmlArchCondition;
              
              public class ArchUnitTest {
                  public void sample(PlantUmlArchCondition condition) {
                      condition.ignoreDependencies("origin", "");
                  }
              }
              """
          )
        );
    }
}
