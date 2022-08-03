/*
 * Copyright 2021 the original author or authors.
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

class CategoryToTagTest : JavaRecipeTest {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
        .classpath("junit", "apiguardian-api")
        .build()

    override val recipe: Recipe
        get() = CategoryToTag()

    @Test
    fun categoriesHavingJAssignmentArguments() = assertChanged(
        dependsOn = arrayOf(
            "public interface FastTests {}",
            "public interface SlowTests {}"
        ),
        before = """
            import org.junit.experimental.categories.Category;

            @Category(value = SlowTests.class)
            public class B {
            
            }
            @Category(value = {SlowTests.class, FastTests.class})
            public class C {
            
            }
        """,
        after = """
            import org.junit.jupiter.api.Tag;

            @Tag("SlowTests")
            public class B {
            
            }
            
            @Tag("SlowTests")
            @Tag("FastTests")
            public class C {
            
            }
        """
    )
    @Test
    fun multipleCategoriesToTags() = assertChanged(
        dependsOn = arrayOf(
                "public interface FastTests {}",
                "public interface SlowTests {}"
        ),
        before = """
            import org.junit.experimental.categories.Category;

            @Category({FastTests.class, SlowTests.class})
            public class B {

            }
        """,
        after = """
            import org.junit.jupiter.api.Tag;

            @Tag("FastTests")
            @Tag("SlowTests")
            public class B {

            }
        """
    )

    @Test
    fun changeCategoryToTagOnClassAndMethod() = assertChanged(
        dependsOn = arrayOf(
                "public interface FastTests {}",
                "public interface SlowTests {}"
        ),
        before = """
            import org.junit.Test;
            import org.junit.experimental.categories.Category;

            @Category(SlowTests.class)
            public class B {

                @Category(FastTests.class)
                @Test
                public void b() {
                }
            
                @Test
                public void d() {
                }
            }
        """,
        after = """
            import org.junit.Test;
            import org.junit.jupiter.api.Tag;

            @Tag("SlowTests")
            public class B {

                @Tag("FastTests")
                @Test
                public void b() {
                }
            
                @Test
                public void d() {
                }
            }
        """
    )

    @Test
    fun maintainAnnotationPositionAmongOtherAnnotations() = assertChanged(
        dependsOn = arrayOf(
                "public interface FastTests {}",
                "public interface SlowTests {}",
        ),
        before = """
            import lombok.Data;
            import org.junit.experimental.categories.Category;
            
            import java.lang.annotation.Documented;
            
            @Documented
            @Category({FastTests.class, SlowTests.class})
            @Data
            public class B {

            }
        """,
        after = """
            import lombok.Data;
            import org.junit.jupiter.api.Tag;
            
            import java.lang.annotation.Documented;
            
            @Documented
            @Tag("FastTests")
            @Tag("SlowTests")
            @Data
            public class B {
            
            }
        """,
        typeValidation =  { identifiers = false; }
    )

    @Test
    fun removesDefunctImport() = assertChanged(
        dependsOn = arrayOf("""
            package a;
            
            public interface FastTests {}
            """),
        before = """
            package b;
            
            import a.FastTests;
            import org.junit.experimental.categories.Category;
            
            @Category({FastTests.class})
            public class B {
            }
        """,
        after = """
            package b;
            
            import org.junit.jupiter.api.Tag;
            
            @Tag("FastTests")
            public class B {
            }
        """
    )
}
