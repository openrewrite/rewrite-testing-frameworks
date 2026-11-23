/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.java.testing.selenium;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

public class ExtendAbstractWebDriverEventListener extends Recipe {

    private static final String LISTENER = "org.openqa.selenium.support.events.WebDriverEventListener";
    private static final String ABSTRACT_LISTENER = "org.openqa.selenium.support.events.AbstractWebDriverEventListener";

    /**
     * The methods of `WebDriverEventListener` in Selenium 4, none of which has a default.
     */
    private static final List<String> LISTENER_METHODS = asList(
            "beforeAlertAccept", "afterAlertAccept", "afterAlertDismiss", "beforeAlertDismiss",
            "beforeNavigateTo", "afterNavigateTo", "beforeNavigateBack", "afterNavigateBack",
            "beforeNavigateForward", "afterNavigateForward", "beforeNavigateRefresh", "afterNavigateRefresh",
            "beforeFindBy", "afterFindBy", "beforeClickOn", "afterClickOn",
            "beforeChangeValueOf", "afterChangeValueOf", "beforeScript", "afterScript",
            "beforeSwitchToWindow", "afterSwitchToWindow", "onException",
            "beforeGetScreenshotAs", "afterGetScreenshotAs", "beforeGetText", "afterGetText");

    @Getter
    final String displayName = "Extend `AbstractWebDriverEventListener` rather than implementing `WebDriverEventListener`";

    @Getter
    final String description = "Selenium 3 releases kept adding methods to `WebDriverEventListener`, which has no " +
            "defaults for them, so a listener written for an earlier release no longer compiles. Where such a " +
            "listener extends no other class, it extends `AbstractWebDriverEventListener` instead, which does " +
            "nothing in the methods it does not declare.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(LISTENER, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                if (c.getKind() != J.ClassDeclaration.Kind.Type.Class || c.getExtends() != null ||
                    c.getImplements() == null || c.hasModifier(J.Modifier.Type.Abstract) ||
                    declaresEveryMethod(c.getBody())) {
                    return c;
                }
                TypeTree listener = null;
                for (TypeTree implemented : c.getImplements()) {
                    if (TypeUtils.isOfClassType(implemented.getType(), LISTENER)) {
                        listener = implemented;
                    }
                }
                if (listener == null) {
                    return c;
                }
                TypeTree removed = listener;
                List<TypeTree> remaining = ListUtils.filter(c.getImplements(), implemented -> implemented != removed);
                c = remaining.isEmpty() ? c.getPadding().withImplements(null) :
                        c.withImplements(ListUtils.mapFirst(remaining, first -> first.withPrefix(Space.SINGLE_SPACE)));
                maybeAddImport(ABSTRACT_LISTENER);
                maybeRemoveImport(LISTENER);
                return JavaTemplate.builder("AbstractWebDriverEventListener")
                        .imports(ABSTRACT_LISTENER)
                        .javaParser(SeleniumDurations.parser(ctx))
                        .build()
                        .apply(updateCursor(c), c.getCoordinates().replaceExtendsClause());
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass nc = super.visitNewClass(newClass, ctx);
                if (nc.getClazz() == null || nc.getBody() == null ||
                    !TypeUtils.isOfClassType(nc.getClazz().getType(), LISTENER) || declaresEveryMethod(nc.getBody())) {
                    return nc;
                }
                maybeAddImport(ABSTRACT_LISTENER);
                maybeRemoveImport(LISTENER);
                return nc.withClazz(new J.Identifier(Tree.randomId(), nc.getClazz().getPrefix(), Markers.EMPTY,
                        emptyList(), "AbstractWebDriverEventListener",
                        JavaType.ShallowClass.build(ABSTRACT_LISTENER), null));
            }
        });
    }

    private static boolean declaresEveryMethod(J.Block body) {
        Set<String> declared = new HashSet<>();
        for (Statement statement : body.getStatements()) {
            if (statement instanceof J.MethodDeclaration) {
                declared.add(((J.MethodDeclaration) statement).getSimpleName());
            }
        }
        return declared.containsAll(LISTENER_METHODS);
    }
}
