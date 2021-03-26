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

import org.junit.jupiter.api.Test
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class ParameterizedRunnerToParameterizedTest : JavaRecipeTest {

    override val parser: JavaParser = JavaParser.fromJavaVersion()
        .classpath("junit")
        .build()
    override val recipe: Recipe
        get() = ParameterizedRunnerToParameterized()

    @Test
    fun parametersNameHasParameters() = assertChanged(
        before = """
            import org.junit.Test;
            import org.junit.runner.RunWith;
            import org.junit.runners.Parameterized;
            import org.junit.runners.Parameterized.Parameters;
        
            import java.util.Arrays;
            import java.util.List;
            
            @RunWith(Parameterized.class)
            public class VetTests {
            
                private String firstName;
                private String lastName;
                private Integer id;
            
                public VetTests(String firstName, String lastName, Integer id) {
                    this.firstName = firstName;
                    this.lastName = lastName;
                    this.id = id;
                }
            
                @Test
                public void testSerialization() {
                    Vet vet = new Vet();
                    vet.setFirstName(firstName);
                    vet.setLastName(lastName);
                    vet.setId(id);
                }
            
                @Parameters(name="{index}: {0} {1} - {2}")
                public static List<Object[]> parameters() {
                    return Arrays.asList(
                        new Object[] { "Otis", "TheDog", 124 },
                        new Object[] { "Garfield", "TheBoss", 126 });
                }
            }
        """,
        after = """
            import org.junit.jupiter.params.ParameterizedTest;
            import org.junit.jupiter.params.provider.MethodSource;
            
            import java.util.Arrays;
            import java.util.List;
        
        
        
            public class VetTests {
            
                private String firstName;
                private String lastName;
                private Integer id;
            
                public void initVetTests(String firstName, String lastName, Integer id) {
                    this.firstName = firstName;
                    this.lastName = lastName;
                    this.id = id;
                }
            
                @MethodSource("parameters")
                @ParameterizedTest(name = "{index}: {0} {1} - {2}")
                public void testSerialization(String firstName, String lastName, Integer id) {
                    initVetTests(firstName, lastName, id);
                    Vet vet = new Vet();
                    vet.setFirstName(firstName);
                    vet.setLastName(lastName);
                    vet.setId(id);
                }
            
                
                public static List<Object[]> parameters() {
                    return Arrays.asList(
                        new Object[] { "Otis", "TheDog", 124 },
                        new Object[] { "Garfield", "TheBoss", 126 });
                }
            }
        """
    )
    @Test
    fun parameterizedTestToParameterizedTestsWithMethodSource() = assertChanged(
        before = """
            import org.junit.Test;
            import org.junit.runner.RunWith;
            import org.junit.runners.Parameterized;
            import org.junit.runners.Parameterized.Parameters;
            
            import java.util.Arrays;
            import java.util.List;
        
            @RunWith(Parameterized.class)
            public class VetTests {
            
                private String firstName;
                private String lastName;
                private Integer id;
            
                public VetTests(String firstName, String lastName, Integer id) {
                    this.firstName = firstName;
                    this.lastName = lastName;
                    this.id = id;
                }
            
                @Test
                public void testSerialization() {
                    Vet vet = new Vet();
                    vet.setFirstName(firstName);
                    vet.setLastName(lastName);
                    vet.setId(id);
                }
            
                @Parameters
                public static List<Object[]> parameters() {
                    return Arrays.asList(
                        new Object[] { "Otis", "TheDog", 124 },
                        new Object[] { "Garfield", "TheBoss", 126 });
                }
            }
        """,
        after = """
            import org.junit.jupiter.params.ParameterizedTest;
            import org.junit.jupiter.params.provider.MethodSource;
            
            import java.util.Arrays;
            import java.util.List;
        
        
        
            public class VetTests {
            
                private String firstName;
                private String lastName;
                private Integer id;
            
                public void initVetTests(String firstName, String lastName, Integer id) {
                    this.firstName = firstName;
                    this.lastName = lastName;
                    this.id = id;
                }
            
                @MethodSource("parameters")
                @ParameterizedTest
                public void testSerialization(String firstName, String lastName, Integer id) {
                    initVetTests(firstName, lastName, id);
                    Vet vet = new Vet();
                    vet.setFirstName(firstName);
                    vet.setLastName(lastName);
                    vet.setId(id);
                }
            
                
                public static List<Object[]> parameters() {
                    return Arrays.asList(
                        new Object[] { "Otis", "TheDog", 124 },
                        new Object[] { "Garfield", "TheBoss", 126 });
                }
            }
        """
    )

    @Test
    fun parameterizedFieldInjectionToParameterizedTest() = assertChanged(
        before = """
            import org.junit.Test;
            import org.junit.runner.RunWith;
            import org.junit.runners.Parameterized;
            import org.junit.runners.Parameterized.Parameter;
            import org.junit.runners.Parameterized.Parameters;

            import java.util.Arrays;
            import java.util.List;

            @RunWith(Parameterized.class)
            public class RewriteTests {
                // param anno comment
                @Parameter(1)
                // vdecl 1 modifier comment
                public String name;
                @Parameter(2)
                // should have modifier test for preserving comments
                String nickName;
                @Parameter(0)
                public Integer id;

                @Parameters(name = "{index}: {0} {1} - {2}")
                public static List<Object[]> parameters() {
                    return Arrays.asList(new Object[]{124, "Otis", "TheDog"}, new Object[]{126, "Garfield", "TheBoss"});
                }

                @Test
                public void checkName() {
                }
            }
        """,
        after = """
            import org.junit.jupiter.params.ParameterizedTest;
            import org.junit.jupiter.params.provider.MethodSource;
            
            import java.util.Arrays;
            import java.util.List;
        
        
            public class RewriteTests {
                // param anno comment
                // vdecl 1 modifier comment
                public String name;
                // should have modifier test for preserving comments
                String nickName;
                public Integer id;
            
            
                public static List<Object[]> parameters() {
                    return Arrays.asList(new Object[]{124, "Otis", "TheDog"}, new Object[]{126, "Garfield", "TheBoss"});
                }
            
                @MethodSource("parameters")
                @ParameterizedTest(name = "{index}: {0} {1} - {2}")
                public void checkName(Integer id, String name, String nickName) {
                    initRewriteTests(id, name, nickName);
                }
            
                public void initRewriteTests(Integer id, String name, String nickName) {
                    this.id = id;
                    this.name = name;
                    this.nickName = nickName;
                }
            }
        """
    )
}