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

import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the {@code java.time.Duration} that Selenium 4 takes in place of an amount and a {@code TimeUnit}.
 */
final class SeleniumDurations {

    static final String TIME_UNIT = "java.util.concurrent.TimeUnit";
    static final JavaType.FullyQualified DURATION = JavaType.ShallowClass.build("java.time.Duration");

    private static final Map<String, String> FACTORY_BY_UNIT = new HashMap<>();

    static {
        FACTORY_BY_UNIT.put("NANOSECONDS", "Duration.ofNanos(#{any(long)})");
        FACTORY_BY_UNIT.put("MICROSECONDS", "Duration.of(#{any(long)}, ChronoUnit.MICROS)");
        FACTORY_BY_UNIT.put("MILLISECONDS", "Duration.ofMillis(#{any(long)})");
        FACTORY_BY_UNIT.put("SECONDS", "Duration.ofSeconds(#{any(long)})");
        FACTORY_BY_UNIT.put("MINUTES", "Duration.ofMinutes(#{any(long)})");
        FACTORY_BY_UNIT.put("HOURS", "Duration.ofHours(#{any(long)})");
        FACTORY_BY_UNIT.put("DAYS", "Duration.ofDays(#{any(long)})");
    }

    private SeleniumDurations() {
    }

    /**
     * @return The {@code TimeUnit} constant {@code unit} names, or {@code null} when it is not a constant.
     */
    static @Nullable String timeUnitConstant(Expression unit) {
        JavaType.Variable field = null;
        if (unit instanceof J.FieldAccess) {
            field = ((J.FieldAccess) unit).getName().getFieldType();
        } else if (unit instanceof J.Identifier) {
            field = ((J.Identifier) unit).getFieldType();
        }
        if (field != null && TypeUtils.isOfClassType(field.getOwner(), TIME_UNIT) &&
            FACTORY_BY_UNIT.containsKey(field.getName())) {
            return field.getName();
        }
        return null;
    }

    /**
     * Replace {@code amount} with the {@code Duration} of that many {@code unit}. Selenium 3 converted both
     * to milliseconds, so a {@code TimeUnit} that is not a constant is converted the same way.
     */
    static Expression amountOf(Cursor amountCursor, Expression amount, Expression unit, ExecutionContext ctx) {
        String constant = timeUnitConstant(unit);
        if (constant != null) {
            return JavaTemplate.builder(FACTORY_BY_UNIT.get(constant))
                    .imports("java.time.Duration", "java.time.temporal.ChronoUnit")
                    .build()
                    .apply(amountCursor, amount.getCoordinates().replace(), amount);
        }
        return JavaTemplate.builder("Duration.ofMillis(#{any(java.util.concurrent.TimeUnit)}.toMillis(#{any(long)}))")
                .imports("java.time.Duration")
                .build()
                .apply(amountCursor, amount.getCoordinates().replace(), unit, amount);
    }

    /**
     * Replace {@code amount} with the {@code Duration} of that many units, as {@code factory} such as
     * {@code ofSeconds} names them.
     */
    static Expression amountOf(Cursor amountCursor, Expression amount, String factory) {
        return JavaTemplate.builder("Duration." + factory + "(#{any(long)})")
                .imports("java.time.Duration")
                .build()
                .apply(amountCursor, amount.getCoordinates().replace(), amount);
    }

    static boolean usesChronoUnit(Expression unit) {
        return "MICROSECONDS".equals(timeUnitConstant(unit));
    }

    /**
     * The same method, renamed and with its parameters replaced, so that later recipes in the same run see the
     * Selenium 4 signature.
     */
    static JavaType.@Nullable Method withSignature(JavaType.@Nullable Method type, String name, List<JavaType> parameterTypes) {
        if (type == null) {
            return null;
        }
        List<String> names = new ArrayList<>(parameterTypes.size());
        for (int i = 0; i < parameterTypes.size(); i++) {
            names.add(i < type.getParameterNames().size() ? type.getParameterNames().get(i) : "arg" + i);
        }
        return type.withName(name).withParameterTypes(parameterTypes).withParameterNames(names);
    }

    /**
     * The constructor a {@code new} calls. For an anonymous class that is the one of the class it extends,
     * which a {@code MethodMatcher} of that class should match.
     */
    static JavaType.@Nullable Method constructorType(J.NewClass newClass) {
        JavaType.Method type = newClass.getConstructorType();
        if (type != null && newClass.getBody() != null && newClass.getClazz() != null) {
            JavaType.FullyQualified extended = TypeUtils.asFullyQualified(newClass.getClazz().getType());
            if (extended != null) {
                return type.withDeclaringType(extended);
            }
        }
        return type;
    }

    /**
     * The call with {@code arguments} in place of its arguments. Unlike {@code withArguments}, this keeps the space
     * after each argument, and so the line break before the closing parenthesis, which belongs to the last argument.
     */
    static J.MethodInvocation withArguments(J.MethodInvocation method, List<Expression> arguments) {
        return method.getPadding().withArguments(withElements(method.getPadding().getArguments(), arguments));
    }

    static J.NewClass withArguments(J.NewClass newClass, List<Expression> arguments) {
        JContainer<Expression> container = newClass.getPadding().getArguments();
        return container == null ? newClass.withArguments(arguments) :
                newClass.getPadding().withArguments(withElements(container, arguments));
    }

    private static JContainer<Expression> withElements(JContainer<Expression> container, List<Expression> arguments) {
        List<JRightPadded<Expression>> before = container.getPadding().getElements();
        List<JRightPadded<Expression>> after = new ArrayList<>(arguments.size());
        for (int i = 0; i < arguments.size(); i++) {
            // The last argument takes the space before the closing parenthesis, wherever it was
            int from = i == arguments.size() - 1 ? before.size() - 1 : Math.min(i, before.size() - 1);
            after.add(before.get(from).withElement(arguments.get(i)));
        }
        return container.getPadding().withElements(after);
    }

    static JavaParser.Builder<?, ?> parser(ExecutionContext ctx) {
        return JavaParser.fromJavaVersion().classpathFromResources(ctx,
                "selenium-api-4",
                "selenium-chrome-driver-4",
                "selenium-chromium-driver-4",
                "selenium-edge-driver-4",
                "selenium-firefox-driver-4",
                "selenium-ie-driver-4",
                "selenium-remote-driver-4",
                "selenium-safari-driver-4",
                "selenium-support-4");
    }
}
