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
package org.openrewrite.java.testing.dbrider;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ExecutionListenerToDbRiderAnnotationUnitTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-test-6.1", "rider-spring-1.18", "rider-junit5-1.44"))
          .recipe(new ExecutionListenerToDbRiderAnnotation());
    }

    @DocumentExample
    @Test
    void replaceAnnotationIfOnlyDbRiderListenerMergedWithDefaults() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.test.context.TestExecutionListeners;
              import com.github.database.rider.spring.DBRiderTestExecutionListener;
              
              @TestExecutionListeners(mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS, listeners = {DBRiderTestExecutionListener.class})
              public class Sample {}
              """,
            """
              import com.github.database.rider.junit5.api.DBRider;
              
              @DBRider
              public class Sample {}
              """
          )
        );
    }

    @Test
    void addAnnotationIfOnlyDbRiderListenerReplacedDefaults() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.test.context.TestExecutionListeners;
              import com.github.database.rider.spring.DBRiderTestExecutionListener;
              
              @TestExecutionListeners(mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS, listeners = {DBRiderTestExecutionListener.class})
              public class Sample {}
              """,
            """
              import org.springframework.test.context.TestExecutionListeners;
              import com.github.database.rider.junit5.api.DBRider;
              
              @DBRider
              @TestExecutionListeners
              public class Sample {}
              """
          )
        );
    }

    @Test
    void addAnnotationIfOnlyDbRiderListenerAndNoMergeModeSpecified() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.test.context.TestExecutionListeners;
              import com.github.database.rider.spring.DBRiderTestExecutionListener;
              
              @TestExecutionListeners(listeners = {DBRiderTestExecutionListener.class})
              public class Sample {}
              """,
            """
              import org.springframework.test.context.TestExecutionListeners;
              import com.github.database.rider.junit5.api.DBRider;
              
              @DBRider
              @TestExecutionListeners
              public class Sample {}
              """
          )
        );
    }

    @Test
    void addAnnotationIfOnlyDbRiderListenerThroughValue() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.test.context.TestExecutionListeners;
              import com.github.database.rider.spring.DBRiderTestExecutionListener;
              
              @TestExecutionListeners(DBRiderTestExecutionListener.class)
              public class Sample {}
              """,
            """
              import org.springframework.test.context.TestExecutionListeners;
              import com.github.database.rider.junit5.api.DBRider;
              
              @DBRider
              @TestExecutionListeners
              public class Sample {}
              """
          )
        );
    }

    @Test
    void addAnnotationIfOnlyDbRiderListenerThroughValueArray() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.test.context.TestExecutionListeners;
              import com.github.database.rider.spring.DBRiderTestExecutionListener;
              
              @TestExecutionListeners({DBRiderTestExecutionListener.class})
              public class Sample {}
              """,
            """
              import org.springframework.test.context.TestExecutionListeners;
              import com.github.database.rider.junit5.api.DBRider;
              
              @DBRider
              @TestExecutionListeners
              public class Sample {}
              """
          )
        );
    }

    @Test
    void keepAnnotationIfOnlyDbRiderListenerSetAndNonDefaultSetting() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.test.context.TestExecutionListeners;
              import com.github.database.rider.spring.DBRiderTestExecutionListener;
              
              @TestExecutionListeners(value = {DBRiderTestExecutionListener.class}, inheritListeners = false)
              public class Sample {}
              """,
            """
              import org.springframework.test.context.TestExecutionListeners;
              import com.github.database.rider.junit5.api.DBRider;
              
              @DBRider
              @TestExecutionListeners(inheritListeners = false)
              public class Sample {}
              """
          )
        );
    }

    @Test
    void removeListenerFromOtherListeners() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.test.context.TestExecutionListeners;
              import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
              import com.github.database.rider.spring.DBRiderTestExecutionListener;
              
              @TestExecutionListeners(mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS, listeners = {DBRiderTestExecutionListener.class, DirtiesContextTestExecutionListener.class})
              public class Sample {}
              """,
            """
              import org.springframework.test.context.TestExecutionListeners;
              import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
              import com.github.database.rider.junit5.api.DBRider;
              
              @DBRider
              @TestExecutionListeners(listeners = {DirtiesContextTestExecutionListener.class})
              public class Sample {}
              """
          )
        );
    }

    @Test
    void doNotTouchIfNoListenerPresent() {
        rewriteRun(
          //language=java
          java(
            """
              @Deprecated
              public class Sample {}
              """
          )
        );
    }

    @Test
    void doNotTouchIfDbRiderAlreadyPresent() {
        rewriteRun(
          //language=java
          java(
            """
              import com.github.database.rider.junit5.api.DBRider;
              import org.springframework.test.context.TestExecutionListeners;
              import com.github.database.rider.spring.DBRiderTestExecutionListener;
              
              @DBRider
              @TestExecutionListeners(listeners = {DBRiderTestExecutionListener.class}, inheritListeners = false)
              public class Sample {}
              """
          )
        );
    }
}
