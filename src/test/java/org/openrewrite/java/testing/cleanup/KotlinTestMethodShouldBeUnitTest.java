/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.java.testing.cleanup;

import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.SourceFile;
import org.openrewrite.java.tree.J;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpec;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.openrewrite.kotlin.Assertions.kotlin;

//
public class KotlinTestMethodShouldBeUnitTest implements RewriteTest {

    @Test
    void showNullMethodInvocationType() {
        //language=kotlin
        SourceSpec<?> source = getOnly(
          kotlin(
            """
              class ExampleTest {
                  fun myTest() = run {
                      return 1
                  }
              }
              """
          ));
        Parser parser = KotlinParser.builder().build();
        Path sourcePath = parser.sourcePathFromSourceText(source.getDir(), source.getBefore());
        ExecutionContext ctx = new InMemoryExecutionContext();
        Parser.Input input = Parser.Input.fromString(
          sourcePath, source.getBefore(), parser.getCharset(ctx));
        SourceFile sourceFile = getOnly(
          parser.parseInputs(Collections.singleton(input), null, ctx).toList());
        new FindMissingTypesVisitor().visit(sourceFile, ctx);
    }

    private static class FindMissingTypesVisitor extends KotlinIsoVisitor<ExecutionContext> {

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method,
                                                        ExecutionContext ctx) {
            assertNotNull(method.getMethodType());
            return method;
        }
    }

    private static <T> T getOnly(Iterable<T> iterable) {
        Iterator<T> iter = iterable.iterator();
        T only = iter.next();
        assertFalse(iter.hasNext());
        return only;
    }
}
