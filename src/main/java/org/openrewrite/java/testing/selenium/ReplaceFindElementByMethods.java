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
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static java.util.Collections.singletonList;

public class ReplaceFindElementByMethods extends Recipe {

    private static final String SEARCH_CONTEXT = "org.openqa.selenium.SearchContext";
    private static final JavaType.FullyQualified BY = JavaType.ShallowClass.build("org.openqa.selenium.By");

    /**
     * The {@code FindsBy*} interface declaring each kind of lookup, and the {@code By} factory replacing it.
     */
    private static final Map<String, String> LOOKUPS = new LinkedHashMap<>();

    static {
        LOOKUPS.put("Id", "id");
        LOOKUPS.put("Name", "name");
        LOOKUPS.put("XPath", "xpath");
        LOOKUPS.put("CssSelector", "cssSelector");
        LOOKUPS.put("ClassName", "className");
        LOOKUPS.put("TagName", "tagName");
        LOOKUPS.put("LinkText", "linkText");
        LOOKUPS.put("PartialLinkText", "partialLinkText");
    }

    private static final Map<MethodMatcher, String[]> REPLACEMENTS = new LinkedHashMap<>();

    static {
        for (Map.Entry<String, String> lookup : LOOKUPS.entrySet()) {
            String findsBy = "org.openqa.selenium.internal.FindsBy" + lookup.getKey();
            REPLACEMENTS.put(new MethodMatcher(findsBy + " findElementBy" + lookup.getKey() + "(String)", true),
                    new String[]{"findElement", lookup.getValue(), findsBy});
            REPLACEMENTS.put(new MethodMatcher(findsBy + " findElementsBy" + lookup.getKey() + "(String)", true),
                    new String[]{"findElements", lookup.getValue(), findsBy});
        }
    }

    @Getter
    final String displayName = "Replace `findElementBy*` with `findElement(By)`";

    @Getter
    final String description = "Selenium 4 removed the `FindsBy*` interfaces and with them methods such as " +
            "`RemoteWebDriver.findElementById(String)` and `findElementsByXPath(String)`. Call `findElement` and " +
            "`findElements` with the matching `By` locator instead, dropping a cast to a `FindsBy*` interface.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(REPLACEMENTS.keySet().stream()
                        .map(UsesMethod::new)
                        .toArray(TreeVisitor[]::new)),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                        String[] replacement = replacement(m);
                        if (replacement == null) {
                            return m;
                        }
                        String newName = replacement[0];
                        String factory = replacement[1];
                        Expression locator = m.getArguments().get(0);
                        Expression by = JavaTemplate.builder("By." + factory + "(#{any(java.lang.String)})")
                                .imports("org.openqa.selenium.By")
                                .javaParser(SeleniumDurations.parser(ctx))
                                .build()
                                .apply(new Cursor(updateCursor(m), locator), locator.getCoordinates().replace(), locator);
                        maybeAddImport("org.openqa.selenium.By");
                        maybeRemoveImport(replacement[2]);

                        JavaType.Method type = SeleniumDurations.withSignature(m.getMethodType(), newName, singletonList(BY));
                        return m.withSelect(withoutFindsByCast(m.getSelect()))
                                .withArguments(singletonList(by.withPrefix(locator.getPrefix())))
                                .withName(m.getName().withSimpleName(newName).withType(type))
                                .withMethodType(type);
                    }

                    private String @Nullable [] replacement(J.MethodInvocation m) {
                        for (Map.Entry<MethodMatcher, String[]> entry : REPLACEMENTS.entrySet()) {
                            if (entry.getKey().matches(m)) {
                                return entry.getValue();
                            }
                        }
                        return null;
                    }

                    /**
                     * {@code ((FindsById) context).findElementById(id)} keeps working as
                     * {@code context.findElement(By.id(id))} as long as {@code context} is a {@code SearchContext}.
                     */
                    private @Nullable Expression withoutFindsByCast(@Nullable Expression select) {
                        Expression unwrapped = select;
                        while (unwrapped instanceof J.Parentheses) {
                            unwrapped = (Expression) ((J.Parentheses<?>) unwrapped).getTree();
                        }
                        if (!(unwrapped instanceof J.TypeCast)) {
                            return select;
                        }
                        J.TypeCast cast = (J.TypeCast) unwrapped;
                        JavaType.FullyQualified castTo = TypeUtils.asFullyQualified(cast.getClazz().getTree().getType());
                        if (castTo == null || !castTo.getFullyQualifiedName().startsWith("org.openqa.selenium.internal.FindsBy") ||
                            !TypeUtils.isAssignableTo(SEARCH_CONTEXT, cast.getExpression().getType())) {
                            return select;
                        }
                        Expression expression = cast.getExpression();
                        if (expression instanceof J.MethodInvocation || expression instanceof J.Identifier ||
                            expression instanceof J.FieldAccess || expression instanceof J.Parentheses) {
                            return expression.withPrefix(select.getPrefix());
                        }
                        // e.g. a ternary has to stay in parentheses to be the target of a method call
                        return select;
                    }
                });
    }
}
