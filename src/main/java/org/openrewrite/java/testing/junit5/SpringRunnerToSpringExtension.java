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

import org.openrewrite.Formatting;
import org.openrewrite.java.JavaIsoRefactorVisitor;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.openrewrite.Formatting.EMPTY;
import static org.openrewrite.Tree.randomId;

/**
 * JUnit4 Spring test classes are annotated with @RunWith(SpringRunner.class)
 * Turn this into the JUnit5-compatible @ExtendsWith(SpringExtension.class)
 */
public class SpringRunnerToSpringExtension extends JavaIsoRefactorVisitor {
    private static final JavaType.Class runWithType = JavaType.Class.build("org.junit.runner.RunWith");
    private static final J.Ident runWithIdent = J.Ident.build(
            randomId(),
            "RunWith",
            runWithType,
            EMPTY);
    private static final JavaType.Class springRunnerType = JavaType.Class.build("org.springframework.test.context.junit4.SpringRunner");
    private static final JavaType.Class extendWithType = JavaType.Class.build("org.junit.jupiter.api.extension.ExtendWith");
    private static final J.Ident extendWithIdent = J.Ident.build(
            randomId(),
            "ExtendWith",
            extendWithType,
            EMPTY
    );
    private static final JavaType.Class springExtensionType = JavaType.Class.build("org.springframework.test.context.junit.jupiter.SpringExtension");


    // Reference @RunWith(SpringRunner.class) annotation for semantically equal to compare against
    private static final J.Annotation runWithSpringRunnerAnnotation = new J.Annotation(
            randomId(),
            runWithIdent,
            new J.Annotation.Arguments(
                    randomId(),
                    Collections.singletonList(
                            new J.FieldAccess(
                                    randomId(),
                                    J.Ident.build(
                                            randomId(),
                                            "SpringRunner",
                                            springRunnerType,
                                            EMPTY
                                    ),
                                    J.Ident.build(randomId(), "class", null, EMPTY),
                                    JavaType.Class.build("java.lang.Class"),
                                    EMPTY
                            )
                    ),
                    EMPTY
            ),
            EMPTY
    );

    private static final J.Annotation extendWithSpringExtensionAnnotation = new J.Annotation(
            randomId(),
            extendWithIdent,
            new J.Annotation.Arguments(
                    randomId(),
                    Collections.singletonList(
                            new J.FieldAccess(
                                    randomId(),
                                    J.Ident.build(
                                            randomId(),
                                            "SpringExtension",
                                            springExtensionType,
                                            EMPTY
                                    ),
                                    J.Ident.build(randomId(), "class", null, EMPTY),
                                    JavaType.Class.build("java.lang.Class"),
                                    EMPTY
                            )
                    ),
                    EMPTY
            ),
            EMPTY
    );

    public SpringRunnerToSpringExtension() {
        setCursoringOn();
    }

    @Override
    public J.ClassDecl visitClassDecl(J.ClassDecl cd) {
        List<J.Annotation> annotations = cd.getAnnotations().stream()
                .map(this::springRunnerToSpringExtension)
                .collect(Collectors.toList());

        return cd.withAnnotations(annotations);
    }

    /**
     * Converts annotations like @RunWith(SpringRunner.class) into @ExtendWith(SpringExtension.class)
     * Leaves other annotations untouched and returns as-is.
     *
     * NOT a pure function. Side effects include:
     *      Adding imports for ExtendWith and SpringExtension
     *      Removing imports for RunWith and SpringRunner
     */
    private J.Annotation springRunnerToSpringExtension(J.Annotation maybeSpringRunner) {
        if(!new SemanticallyEqual(runWithSpringRunnerAnnotation).visit(maybeSpringRunner)) {
            return maybeSpringRunner;
        }
        Formatting originalFormatting = maybeSpringRunner.getFormatting();
        J.ClassDecl parent = getCursor().firstEnclosing(J.ClassDecl.class);
        assert parent != null;
        J.Annotation extendWithSpringExtension = extendWithSpringExtensionAnnotation.withFormatting(originalFormatting);
        maybeAddImport(extendWithType);
        maybeAddImport(springExtensionType);
        maybeRemoveImport(springRunnerType);
        maybeRemoveImport(runWithType);

        return extendWithSpringExtension;
    }
}
