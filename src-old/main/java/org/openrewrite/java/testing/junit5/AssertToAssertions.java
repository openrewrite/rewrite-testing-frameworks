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

import org.openrewrite.AutoConfigure;
import org.openrewrite.Formatting;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoRefactorVisitor;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Change JUnit4's org.junit.Assert into JUnit5's org.junit.jupiter.api.Assertions
 * The most significant difference between these classes is that in JUnit4 the optional String message is the first
 * parameter, and the JUnit5 versions have the optional message as the final parameter
 */
@AutoConfigure
public class AssertToAssertions extends JavaIsoRefactorVisitor {

    @Override
    public J.ClassDecl visitClassDecl(J.ClassDecl classDecl) {
        ChangeType ct = new ChangeType();
        ct.setType("org.junit.Assert");
        ct.setTargetType("org.junit.jupiter.api.Assertions");
        andThen(ct);
        return super.visitClassDecl(classDecl);
    }

    @Override
    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method) {
        J.MethodInvocation m = super.visitMethodInvocation(method);
        if(!isJunitAssertMethod(m)) {
            return m;
        }
        List<Expression> args = m.getArgs().getArgs();
        Expression firstArg = args.get(0);
        // Suppress arg-switching for Assertions.assertEquals(String, String)
        if(args.size() == 2 && isString(firstArg.getType()) && isString(args.get(1).getType())) {
            return m;
        }
        if(isString(firstArg.getType())) {
            // Move the first arg to be the last argument, then switch the formatting of the first and last arguments
            Formatting firstFormatting = firstArg.getFormatting();
            Formatting lastFormatting = args.get(args.size() - 1).getFormatting();

            List<Expression> newArgs = Stream.concat(
                 args.stream().skip(1),
                 Stream.of(firstArg)
            ).collect(Collectors.toList());

            int lastIndex = newArgs.size() - 1;
            newArgs.set(0, newArgs.get(0).withFormatting(firstFormatting));
            newArgs.set(lastIndex, newArgs.get(lastIndex).withFormatting(lastFormatting));

            m = m.withArgs(m.getArgs().withArgs(newArgs));
        }

        return m;
    }

    private boolean isJunitAssertMethod(J.MethodInvocation method) {
        if(!(method.getSelect() instanceof J.Ident)) {
            return false;
        }
        J.Ident receiver = (J.Ident) method.getSelect();
        if(!(receiver.getType() instanceof JavaType.FullyQualified)) {
            return false;
        }
        JavaType.FullyQualified receiverType = (JavaType.FullyQualified) receiver.getType();
        return receiverType.getFullyQualifiedName().equals("org.junit.Assert");
    }

    private boolean isString(JavaType type) {
        return type instanceof JavaType.Primitive
                && ((JavaType.Primitive)type).name().equals("String");
    }
}
