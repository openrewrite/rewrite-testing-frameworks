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
import org.openrewrite.java.*;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * This refactor visitor will replace JUnit 4's "Before", "BeforeClass", "After", and "AfterClass" annotations with their
 * JUnit 5 equivalents. Additionally, this visitor will reduce the visibility of methods marked with those annotations
 * to "package" to comply with JUnit 5 best practices.
 *
 * <PRE>
 *  org.junit.Before --> org.junit.jupiter.api.BeforeEach
 *  org.junit.After --> org.junit.jupiter.api.AfterEach
 *  org.junit.BeforeClass --> org.junit.jupiter.api.BeforeAll
 *  org.junit.AfterClass --> org.junit.jupiter.api.AfterAll
 * </PRE>
 */
@AutoConfigure
public class UpdateBeforeAfterAnnotations extends JavaIsoRefactorVisitor {

    @Override
    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu) {
        //This visitor handles changing the method visibility for any method annotated with one of the four before/after
        //annotations. It registers visitors that will sweep behind it making the type changes.
        ChangeType changeType = new ChangeType();
        changeType.setType("org.junit.Before");
        changeType.setTargetType("org.junit.jupiter.api.BeforeEach");
        andThen(changeType);

        changeType = new ChangeType();
        changeType.setType("org.junit.After");
        changeType.setTargetType("org.junit.jupiter.api.AfterEach");
        andThen(changeType);

        changeType = new ChangeType();
        changeType.setType("org.junit.BeforeClass");
        changeType.setTargetType("org.junit.jupiter.api.BeforeAll");
        andThen(changeType);

        changeType = new ChangeType();
        changeType.setType("org.junit.AfterClass");
        changeType.setTargetType("org.junit.jupiter.api.AfterAll");
        andThen(changeType);

        return super.visitCompilationUnit(cu);
    }

    @Override
    public J.MethodDecl visitMethod(J.MethodDecl method) {
        J.MethodDecl m = super.visitMethod(method);

        boolean changed = false;
        List<J.Annotation> annotations = new ArrayList<>(m.getAnnotations());
        for (J.Annotation a : annotations) {

            if (TypeUtils.isOfClassType(a.getType(), "org.junit.Before") ||
                    TypeUtils.isOfClassType(a.getType(), "org.junit.After") ||
                    TypeUtils.isOfClassType(a.getType(), "org.junit.BeforeClass") ||
                    TypeUtils.isOfClassType(a.getType(), "org.junit.AfterClass")) {

                //If we found the annotation, we change the visibility of the method to package. Also need to format
                //the method declaration because the previous visibility likely had formatting that is removed.
                m = m.withModifiers(J.Modifier.withVisibility(m.getModifiers(), "package"));
                andThen(new AutoFormat(m));
                break;
            }
        }
        return m;
    }
}
