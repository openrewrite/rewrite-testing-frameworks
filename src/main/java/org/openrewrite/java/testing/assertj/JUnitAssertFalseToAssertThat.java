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
package org.openrewrite.java.testing.assertj;

import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.List;

public class JUnitAssertFalseToAssertThat extends AbstractJUnitAssertToAssertThatRecipe {

    @Override
    public String getDisplayName() {
        return "JUnit `assertFalse` to AssertJ";
    }

    @Override
    public String getDescription() {
        return "Convert JUnit-style `assertFalse()` to AssertJ's `assertThat().isFalse()`.";
    }

    public JUnitAssertFalseToAssertThat() {
        super("assertFalse(boolean, ..)", "assertFalse(.., boolean)");
    }

    @Override
    protected JUnitAssertionVisitor getJUnitAssertionVisitor(JUnitAssertionConfig config) {
        return new JUnitAssertionVisitor(config) {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (!config.matches(mi)) {
                    return mi;
                }

                maybeAddImport(ASSERTJ, "assertThat", false);
                maybeRemoveImport(config.getAssertionClass());

                List<Expression> args = mi.getArguments();
                if (args.size() == 1) {
                    Expression actual = args.get(0);
                    return JavaTemplate.builder("assertThat(#{any(boolean)}).isFalse();")
                            .staticImports(ASSERT_THAT)
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, ASSERTJ_CORE))
                            .build()
                            .apply(getCursor(), mi.getCoordinates().replace(), actual);
                }

                Expression message = config.isMessageIsFirstArg() ? args.get(0) : args.get(1);
                Expression actual = config.isMessageIsFirstArg() ? args.get(1) : args.get(0);
                return JavaTemplate.builder("assertThat(#{any(boolean)}).as(#{any()}).isFalse();")
                        .staticImports(ASSERT_THAT)
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, ASSERTJ_CORE))
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replace(), actual, message);
            }
        };
    }
}
