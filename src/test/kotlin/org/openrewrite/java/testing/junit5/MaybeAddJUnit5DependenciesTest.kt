/*
 * Copyright 2020 the original author or authors.
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
package org.openrewrite.java.testing.junit5

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.openrewrite.Refactor
import org.openrewrite.RefactorVisitorTest
import org.openrewrite.java.JavaParser
import org.openrewrite.maven.MavenParser

class MaybeAddJUnit5DependenciesTest : RefactorVisitorTest {
    @Test
    fun addDependenciesWhenJUnitTestsExist() {
        val java = JavaParser.fromJavaVersion()
                .classpath(JavaParser.dependenciesFromClasspath("junit-jupiter-api", "apiguardian-api"))
                .build().parse("""
                    import org.junit.jupiter.api.Test;                   
                    class MyTest {
                        @Test
                        void test() {
                        }
                    }
                """.trimIndent())

        val maven = MavenParser.builder().build().parse("""
            <project>
              <groupId>com.mycompany.app</groupId>
              <artifactId>my-app</artifactId>
              <version>1</version>
            </project>
        """.trimIndent())

        val changes = Refactor()
                .visit(MaybeAddJUnit5Dependencies())
                .fix(maven + java)

        assertThat(changes).hasSize(1)
        assertThat(changes.first()?.fixed?.printTrimmed()).isEqualTo("""
            <project>
              <groupId>com.mycompany.app</groupId>
              <artifactId>my-app</artifactId>
              <version>1</version>
              <dependencies>
                <dependency>
                  <groupId>org.junit.jupiter</groupId>
                  <artifactId>junit-jupiter-api</artifactId>
                  <version>5.7.0</version>
                  <scope>test</scope>
                </dependency>
                <dependency>
                  <groupId>org.junit.jupiter</groupId>
                  <artifactId>junit-jupiter-api</artifactId>
                  <version>5.7.0</version>
                  <scope>test</scope>
                </dependency>
                <dependency>
                  <groupId>org.junit.jupiter</groupId>
                  <artifactId>junit-jupiter-engine</artifactId>
                  <version>5.7.0</version>
                  <scope>test</scope>
                </dependency>
                <dependency>
                  <groupId>org.junit.jupiter</groupId>
                  <artifactId>junit-jupiter-engine</artifactId>
                  <version>5.7.0</version>
                  <scope>test</scope>
                </dependency>
              </dependencies>
            </project>
        """.trimIndent())
    }
}
