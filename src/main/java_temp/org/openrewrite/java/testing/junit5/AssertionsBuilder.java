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
package org.openrewrite.java.testing.junit5;

import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.openrewrite.Formatting.EMPTY;
import static org.openrewrite.Formatting.format;
import static org.openrewrite.Tree.randomId;

class AssertionsBuilder {
    /**
     * Produces a method invocation of Assertions.assertThrows(Class<T> expectedType, executable executable)
     *
     * @param expectedTypeExpression an expression that is expected, but not verified, to evaluate to a Class<T extends Throwable>
     * @param executable the list of statements used to create the body of the lambda that is expected to produce a particular type of expression
     */
    public static J.MethodInvocation assertThrows(Expression expectedTypeExpression, List<Statement> executable) {
        // The Java 11 Specification says that lambda bodies can be either a single expression or a block.
        // So put a block around anything that isn't exactly one expression.
        boolean isSingleExpression = executable.size() == 1 && executable.get(0) instanceof Expression;
        Statement assertBlock;
        if(isSingleExpression) {
            assertBlock = executable.get(0).withPrefix(" ");
        } else {
            assertBlock = new J.Block<>(
                    randomId(),
                    null,
                    executable,
                    format(" "),
                    new J.Block.End(randomId(), format("\n"))
            );
        }

        return new J.MethodInvocation(
                randomId(),
                null,
                null,
                J.Ident.build(randomId(), "assertThrows", JavaType.Primitive.Void, EMPTY),
                new J.MethodInvocation.Arguments(
                        randomId(),
                        Arrays.asList(
                                expectedTypeExpression.withFormatting(EMPTY),
                                new J.Lambda(
                                        randomId(),
                                        new J.Lambda.Parameters(
                                                randomId(),
                                                true,
                                                Collections.emptyList()
                                        ),
                                        new J.Lambda.Arrow(randomId(), format(" ")),
                                        assertBlock,
                                        JavaType.Primitive.Void,
                                        format(" ")
                                )
                        ),
                        EMPTY
                ),
                JavaType.Method.build(
                        JavaType.Class.build("org.junit.jupiter.api.Assertions"),
                        "assertThrows",
                        null,
                        new JavaType.Method.Signature(
                                new JavaType.GenericTypeVariable("T", JavaType.Class.build("java.lang.Throwable")),
                                Arrays.asList(JavaType.Class.build("java.lang.Class"), JavaType.Class.build("org.junit.jupiter.api.function.executable"))),
                        Arrays.asList("arg0", "arg1"),
                        new HashSet<>(Arrays.asList(Flag.Public, Flag.Static))
                ),
                format("\n")
        );
    }
}
