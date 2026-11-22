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
package org.openrewrite.java.testing.hamcrest;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeMethodTargetToStatic;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Calls each Hamcrest matcher factory through {@code org.hamcrest.Matchers}, so that the recipes migrating matchers
 * only have to recognize one class. Only static factories that {@code Matchers} also declares move, which leaves the
 * instance methods of the matcher classes, such as {@code both(a).and(b)}, as they are.
 */
public class HamcrestFactoriesToMatchers extends Recipe {

    private static final Pattern MATCHER_PACKAGES = Pattern.compile(
            "org\\.hamcrest\\.(core|collection|number|text|beans|object|xml)\\.[A-Za-z0-9_$]+");

    /**
     * The static factories of {@code org.hamcrest.Matchers} in Hamcrest 3.0. Hamcrest 1.3's {@code Matchers} declares
     * a subset of them under the same names.
     */
    private static final Set<String> MATCHERS_FACTORIES = new HashSet<>(Arrays.asList(
            "aMapWithSize", "allOf", "anEmptyMap", "any", "anyOf", "anything", "array", "arrayContaining",
            "arrayContainingInAnyOrder", "arrayWithSize", "blankOrNullString", "blankString", "both", "closeTo",
            "comparesEqualTo", "contains", "containsInAnyOrder", "containsInRelativeOrder", "containsString",
            "containsStringIgnoringCase", "describedAs", "either", "empty", "emptyArray", "emptyCollectionOf",
            "emptyIterable", "emptyIterableOf", "emptyOrNullString", "emptyString", "endsWith", "endsWithIgnoringCase",
            "equalTo", "equalToCompressingWhiteSpace", "equalToIgnoringCase", "equalToIgnoringWhiteSpace",
            "equalToObject", "eventFrom", "everyItem", "greaterThan", "greaterThanOrEqualTo", "hasEntry", "hasItem",
            "hasItemInArray", "hasItems", "hasKey", "hasLength", "hasProperty", "hasSize", "hasToString", "hasValue",
            "hasXPath", "in", "instanceOf", "is", "isA", "isEmptyOrNullString", "isEmptyString", "isIn", "isOneOf",
            "iterableWithSize", "lessThan", "lessThanOrEqualTo", "matchesPattern", "matchesRegex", "not", "notANumber",
            "notNullValue", "nullValue", "oneOf", "sameInstance", "samePropertyValuesAs", "startsWith",
            "startsWithIgnoringCase", "stringContainsInOrder", "theInstance", "typeCompatibleWith"));

    @Getter
    final String displayName = "Call Hamcrest matcher factories through `Matchers`";

    @Getter
    final String description = "Call the static factories of Hamcrest's matcher classes, such as `IsEqual.equalTo(..)` " +
            "or `IsCollectionWithSize.hasSize(..)`, through `org.hamcrest.Matchers`, leaving the instance methods of " +
            "matchers as they are.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.hamcrest..*", false), new JavaIsoVisitor<ExecutionContext>() {
            final Set<String> scheduled = new HashSet<>();

            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                scheduled.clear();
                return super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                JavaType.Method type = mi.getMethodType();
                if (type == null || !type.hasFlags(Flag.Static) || !MATCHERS_FACTORIES.contains(type.getName())) {
                    return mi;
                }
                String owner = type.getDeclaringType().getFullyQualifiedName();
                if (MATCHER_PACKAGES.matcher(owner).matches() && scheduled.add(owner + " " + type.getName())) {
                    doAfterVisit(new ChangeMethodTargetToStatic(owner + " " + type.getName() + "(..)",
                            "org.hamcrest.Matchers", null, null, false).getVisitor());
                }
                return mi;
            }
        });
    }
}
