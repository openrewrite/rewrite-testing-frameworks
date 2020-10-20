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

import org.openrewrite.java.AddImport;
import org.openrewrite.java.AutoFormat;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaRefactorVisitor;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.openrewrite.Formatting.EMPTY;
import static org.openrewrite.Formatting.format;
import static org.openrewrite.Tree.randomId;

public class ChangeTestAnnotation extends JavaRefactorVisitor {
    public ChangeTestAnnotation() {
        setCursoringOn();
    }

    @Override
    public J visitCompilationUnit(J.CompilationUnit cu) {
        ChangeType changeType = new ChangeType();
        changeType.setType("org.junit.Test");
        changeType.setTargetType("org.junit.jupiter.api.Test");
        andThen(changeType);

        return super.visitCompilationUnit(cu);
    }

    @Override
    public J visitMethod(J.MethodDecl method) {
        J.MethodDecl m = refactor(method, super::visitMethod);

        boolean changed = false;
        List<J.Annotation> annotations = new ArrayList<>(m.getAnnotations());
        for (int i = 0, annotationsSize = annotations.size(); i < annotationsSize; i++) {
            J.Annotation a = annotations.get(i);
            if (TypeUtils.isOfClassType(a.getType(), "org.junit.Test")) {
                annotations.set(i, a.withArgs(null));

                List<Expression> args = a.getArgs().getArgs();
                for (Expression arg : args) {
                    if (arg instanceof J.Assign) {
                        J.Assign assign = (J.Assign) arg;
                        switch (((J.Ident) assign.getVariable()).getSimpleName()) {
                            case "expected":
                                Expression e = assign.getAssignment();
                                List<Statement> statements = m.getBody().getStatements();

                                Statement assertBlock = statements.size() == 1 ?
                                        statements.get(0).withPrefix(" ") :
                                        new J.Block<>(
                                                randomId(),
                                                null,
                                                statements,
                                                format(" "),
                                                new J.Block.End(randomId(), format("\n"))
                                        );

                                J.MethodInvocation assertThrows = new J.MethodInvocation(
                                        randomId(),
                                        null,
                                        null,
                                        J.Ident.build(randomId(), "assertThrows", JavaType.Primitive.Void, EMPTY),
                                        new J.MethodInvocation.Arguments(
                                                randomId(),
                                                Arrays.asList(
                                                        e.withFormatting(EMPTY),
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
                                        null,
                                        format("\n")
                                );

                                AddImport addAssertThrows = new AddImport();
                                addAssertThrows.setType("org.junit.jupiter.api.Assertions");
                                addAssertThrows.setStaticMethod("assertThrows");
                                addAssertThrows.setOnlyIfReferenced(false);
                                andThen(addAssertThrows);

                                andThen(new AutoFormat(assertThrows));
                                andThen(new AutoFormat(assertBlock));

                                m = method.withBody(m.getBody().withStatements(singletonList(assertThrows)));
                                break;
                        }
                    }
                    changed = true;
                }
            }
        }

        if (changed) {
            m = m.withAnnotations(annotations);
        }

        return m;
    }
}
