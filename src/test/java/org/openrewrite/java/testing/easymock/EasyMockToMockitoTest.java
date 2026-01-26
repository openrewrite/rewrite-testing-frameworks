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
package org.openrewrite.java.testing.easymock;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcTestJava;
import static org.openrewrite.maven.Assertions.pomXml;

class EasyMockToMockitoTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources( new InMemoryExecutionContext(),"junit-4", "easymock-5"))
          .recipeFromResources("org.openrewrite.java.testing.easymock.EasyMockToMockito");
    }

    @DocumentExample
    @Test
    void replaceEasyMockByMockito() {
        //language=java
        rewriteRun(
          java(
            """
              import org.easymock.EasyMockRunner;
              import org.easymock.Mock;
              import org.easymock.EasyMock;
              import org.easymock.TestSubject;
              import org.junit.Before;
              import org.junit.Test;
              import org.junit.runner.RunWith;

              import static org.junit.Assert.assertEquals;
              import static org.easymock.EasyMock.createNiceMock;
              import static org.easymock.EasyMock.expect;
              import static org.easymock.EasyMock.replay;
              import static org.easymock.EasyMock.verify;

              @RunWith(EasyMockRunner.class)
              public class ExampleTest {

                  private Service service;
                  private Dependency dependency;

                  @Mock
                  private Dependency dependency2;

                  @TestSubject
                  Service service2 = new Service();

                  @Before
                  public void setUp() {
                      dependency = createNiceMock(Dependency.class);
                      service = new Service(dependency);
                  }

                  @Test
                  public void testServiceMethod() {
                      expect(dependency.performAction()).andReturn("Mocked Result");
                      EasyMock.replay(dependency);
                      replay(dependency);
                      assertEquals("Mocked Result", service.useDependency());
                      verify(dependency);
                  }

                  class Service {
                      private Dependency dependency;

                      Service() {}

                      Service(Dependency dependency) {
                          this.dependency = dependency;
                      }

                      String useDependency() {
                          return dependency.performAction();
                      }
                  }

                  interface Dependency {
                      String performAction();
                  }
              }
              """,
            """
              import org.junit.Before;
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.mockito.InjectMocks;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;

              import static org.junit.Assert.assertEquals;
              import static org.mockito.Mockito.verify;
              import static org.mockito.Mockito.mock;
              import static org.mockito.Mockito.when;

              @RunWith(MockitoJUnitRunner.class)
              public class ExampleTest {

                  private Service service;
                  private Dependency dependency;

                  @Mock
                  private Dependency dependency2;

                  @InjectMocks
                  Service service2 = new Service();

                  @Before
                  public void setUp() {
                      dependency = mock(Dependency.class);
                      service = new Service(dependency);
                  }

                  @Test
                  public void testServiceMethod() {
                      when(dependency.performAction()).thenReturn("Mocked Result");
                      assertEquals("Mocked Result", service.useDependency());
                      verify(dependency).performAction();
                  }

                  class Service {
                      private Dependency dependency;

                      Service() {}

                      Service(Dependency dependency) {
                          this.dependency = dependency;
                      }

                      String useDependency() {
                          return dependency.performAction();
                      }
                  }

                  interface Dependency {
                      String performAction();
                  }
              }
              """
          )
        );
    }

    @Test
    void matchers() {
        // From: https://www.baeldung.com/easymock-argument-matchers
        //language=java
        rewriteRun(
          java(
            """
              import java.util.ArrayList;
              import java.util.List;

              class User {
                  private long id;
                  private String firstName;
                  private String lastName;
                  private double age;
                  private String email;
              }

              // The second `Object ignore` argument disables the removal-matcher-optimization code like:
              // expect(service.addUser(eq(new User()))) to expect(service.addUser(new User()));
              class UserService {
                  boolean addUser(User user, Object ignore) { return true; }
                  List<User> findByEmail(String email, Object ignore) { return new ArrayList<>(); }
                  List<User> findByAge(double age, Object ignore) { return new ArrayList<>(); }
              }
              """
          ),
          java(
            """
              import org.junit.Test;
              import static org.easymock.EasyMock.*;

              public class ExampleTest {
                  @Test
                  public void testServiceMethod() {
                      User user = new User();
                      UserService service = createNiceMock(UserService.class);

                      expect(service.addUser(eq(new User()), anyObject()));
                      expect(service.addUser(isNull(), anyObject()));
                      expect(service.addUser(same(user), anyObject()));
                      expect(service.findByEmail(anyString(), anyObject()));
                      expect(service.findByAge(lt(100.0), anyObject()));
                      expect(service.findByAge(and(gt(10.0),leq(100.0)), anyObject()));
                      expect(service.findByEmail(not(endsWith(".com")), anyObject()));
                  }
              }
              """,
            """
              import org.junit.Test;
              import static org.mockito.Mockito.*;
              import static org.mockito.AdditionalMatchers.*;

              public class ExampleTest {
                  @Test
                  public void testServiceMethod() {
                      User user = new User();
                      UserService service = mock(UserService.class);

                      when(service.addUser(eq(new User()), any()));
                      when(service.addUser(isNull(), any()));
                      when(service.addUser(same(user), any()));
                      when(service.findByEmail(anyString(), any()));
                      when(service.findByAge(lt(100.0), any()));
                      when(service.findByAge(and(gt(10.0),leq(100.0)), any()));
                      when(service.findByEmail(not(endsWith(".com")), any()));
                  }
              }
              """
          )
        );
    }

    @Nested
    class DependencyManagement {
        @Test
        void mockitoMavenDependencyAddedWithTestScope() {
            rewriteRun(
              mavenProject("project",
                srcTestJava(
                  //language=java
                  java(
                    """
                      import org.easymock.EasyMock;
                      import org.junit.Test;

                      public class ExampleTest {
                          @Test
                          public void test() {
                              Object mock = EasyMock.createNiceMock(Object.class);
                          }
                      }
                      """,
                    """
                      import org.junit.Test;
                      import org.mockito.Mockito;

                      public class ExampleTest {
                          @Test
                          public void test() {
                              Object mock = Mockito.mock(Object.class);
                          }
                      }
                      """
                  )
                ),
                //language=xml
                pomXml(
                  """
                    <project>
                        <modelVersion>4.0.0</modelVersion>
                        <groupId>com.example</groupId>
                        <artifactId>demo</artifactId>
                        <version>0.0.1-SNAPSHOT</version>
                        <dependencies>
                            <dependency>
                                <groupId>org.easymock</groupId>
                                <artifactId>easymock</artifactId>
                                <version>5.2.0</version>
                                <scope>test</scope>
                            </dependency>
                        </dependencies>
                    </project>
                    """,
                  sourceSpecs -> sourceSpecs.after(after -> """
                    <project>
                        <modelVersion>4.0.0</modelVersion>
                        <groupId>com.example</groupId>
                        <artifactId>demo</artifactId>
                        <version>0.0.1-SNAPSHOT</version>
                        <dependencies>
                            <dependency>
                                <groupId>org.mockito</groupId>
                                <artifactId>mockito-core</artifactId>
                                <version>%s</version>
                                <scope>test</scope>
                            </dependency>
                        </dependencies>
                    </project>
                    """.formatted(Pattern.compile("<version>(5\\..*)</version>").matcher(requireNonNull(after)).results().findFirst().orElseThrow().group(1)))
                )
              )
            );
        }

        @Test
        void mockitoGradleDependencyAddedWithTestScope() {
            rewriteRun(
              spec -> spec.beforeRecipe(withToolingApi()),
              mavenProject("project",
                srcTestJava(
                  //language=java
                  java(
                    """
                      import org.easymock.EasyMock;
                      import org.junit.Test;

                      public class ExampleTest {
                          @Test
                          public void test() {
                              Object mock = EasyMock.createNiceMock(Object.class);
                          }
                      }
                      """,
                    """
                      import org.junit.Test;
                      import org.mockito.Mockito;

                      public class ExampleTest {
                          @Test
                          public void test() {
                              Object mock = Mockito.mock(Object.class);
                          }
                      }
                      """
                  )
                ),
                //language=groovy
                buildGradle(
                  """
                    plugins {
                        id "java-library"
                    }

                    repositories {
                        mavenCentral()
                    }

                    dependencies {
                        testImplementation "org.easymock:easymock:5.2.0"
                    }
                    """,
                  sourceSpecs -> sourceSpecs.after(after -> """
                    plugins {
                        id "java-library"
                    }

                    repositories {
                        mavenCentral()
                    }

                    dependencies {
                        testImplementation "org.mockito:%s"
                    }
                    """.formatted(Pattern.compile("(mockito-core:[^\"]*)").matcher(requireNonNull(after)).results().findFirst().orElseThrow().group(1))
                  )
                )
              )
            );
        }
    }
}
