package org.openrewrite.java.testing.junit5

import org.junit.jupiter.api.Test
import org.openrewrite.RefactorVisitorTestForParser
import org.openrewrite.java.JavaParser
import org.openrewrite.java.tree.J

class CategoryToTagTest : RefactorVisitorTestForParser<J.CompilationUnit> {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
            .classpath("junit")
            .build()

    @Test
    fun multipleCategoriesToTags() = assertRefactored(
            visitors = listOf (CategoryToTag()),
            dependencies = listOf(
                    "public interface FastTests {}",
                    "public interface SlowTests {}"
            ),
            before = """
                import org.junit.experimental.categories.Category;

                @Category({SlowTests.class,FastTests.class})
                public class B {

                }
            """,
            after = """
                import org.junit.jupiter.api.Tag;

                @Tag(SlowTests.class)
                @Tag(FastTests.class)
                public class B {

                }
            """
    )

    @Test
    fun changeCategoryToTagOnClassAndMethod() = assertRefactored(
            visitors = listOf(CategoryToTag()),
            dependencies = listOf(
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

                @Tag(SlowTests.class)
                public class B {

                    @Tag(FastTests.class)
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
    fun maintainAnnotationPositionAmongOtherAnnotations() = assertRefactored(
            visitors = listOf (CategoryToTag()),
            dependencies = listOf(
                    "public interface FastTests {}",
                    "public interface SlowTests {}"
            ),
            before = """
                import lombok.Data;
                import org.junit.experimental.categories.Category;
                
                import java.lang.annotation.Documented;
                
                @Documented
                @Category({SlowTests.class,FastTests.class})
                @Data
                public class B {

                }
            """,
            after = """
                import lombok.Data;
                import org.junit.jupiter.api.Tag;
                
                import java.lang.annotation.Documented;
                
                @Documented
                @Tag(SlowTests.class)
                @Tag(FastTests.class)
                @Data
                public class B {
                
                }
            """
    )

    @Test
    fun maintainsCategoryFormattingOnOnlyFirstTag() = assertRefactored(
            visitors = listOf (CategoryToTag()),
            dependencies = listOf(
                    "public interface FastTests {}",
                    "public interface SlowTests {}",
            ),
            before = """
                import org.junit.experimental.categories.Category;



                @Category({SlowTests.class,FastTests.class})
                public class B {
                
                }
            """,
            after = """
                import org.junit.jupiter.api.Tag;



                @Tag(SlowTests.class)
                @Tag(FastTests.class)
                public class B {

                }
            """
    )
}
