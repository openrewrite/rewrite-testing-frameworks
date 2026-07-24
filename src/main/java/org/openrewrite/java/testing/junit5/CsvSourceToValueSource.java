/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.java.testing.junit5;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.trait.Annotated;
import org.openrewrite.java.trait.Literal;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.kotlin.KotlinTemplate;
import org.openrewrite.kotlin.tree.K;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static java.util.stream.Collectors.joining;
import static org.openrewrite.java.tree.JavaType.Primitive;

public class CsvSourceToValueSource extends Recipe {
    private static final AnnotationMatcher CSV_SOURCE_MATCHER = new AnnotationMatcher("@org.junit.jupiter.params.provider.CsvSource");
    private static final AnnotationMatcher VALUE_SOURCE_MATCHER = new AnnotationMatcher("@org.junit.jupiter.params.provider.ValueSource");

    @Getter
    final String displayName = "Replace `@CsvSource` with `@ValueSource` for single method arguments";

    @Getter
    final String description = "Replaces JUnit 5's `@CsvSource` annotation with `@ValueSource` when the parameterized test has only a single method argument.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>("org.junit.jupiter.params.provider.CsvSource", false),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                        boolean kotlin = getCursor().firstEnclosing(K.CompilationUnit.class) != null;

                        // Check if method has exactly one parameter
                        if (m.getParameters().size() != 1 || m.getParameters().get(0) instanceof J.Empty) {
                            return m;
                        }

                        // Check if parameter is ArgumentsAccessor or uses @AggregateWith or similar meta annotations
                        J.VariableDeclarations param = (J.VariableDeclarations) m.getParameters().get(0);
                        if (TypeUtils.isAssignableTo("org.junit.jupiter.params.aggregator.ArgumentsAccessor", param.getType()) ||
                                !param.getLeadingAnnotations().isEmpty()) {
                            return m;
                        }

                        // Find @CsvSource annotation
                        for (J.Annotation annotation : m.getLeadingAnnotations()) {
                            Optional<Annotated> annotated = new Annotated.Matcher(CSV_SOURCE_MATCHER).get(annotation, getCursor());
                            if (annotated.isPresent() && annotation.getArguments() != null && annotation.getArguments().size() == 1) {
                                // Get the parameter type
                                String paramType = getParameterType((J.VariableDeclarations) m.getParameters().get(0));
                                if (paramType == null) {
                                    return m;
                                }

                                Optional<Literal> textBlockAttribute = annotated.get().getAttribute("textBlock");
                                List<String> values;
                                boolean fromTextBlock = textBlockAttribute.isPresent();
                                if (fromTextBlock) {
                                    String textBlock = textBlockAttribute.get().getString();
                                    if (textBlock == null) {
                                        return m;
                                    }
                                    values = parseTextBlockLines(textBlock);
                                    if (values.isEmpty() || values.stream().anyMatch(v -> v.indexOf(',') >= 0)) {
                                        return m;
                                    }
                                    // Single quotes are CsvSource's default quote char; conversion would change the parsed value
                                    if (values.stream().anyMatch(v -> v.indexOf('\'') >= 0)) {
                                        return m;
                                    }
                                    // Non-String types can't represent values containing whitespace as unquoted literals
                                    if (!"String".equals(paramType) && values.stream().anyMatch(StringUtils::containsWhitespace)) {
                                        return m;
                                    }
                                } else if ("String".equals(paramType)) {
                                    // Preserve original argument formatting (e.g. text blocks, custom spacing)
                                    maybeRemoveImport("org.junit.jupiter.params.provider.CsvSource");
                                    maybeAddImport("org.junit.jupiter.params.provider.ValueSource");
                                    Expression templateArg = annotation.getArguments().get(0) instanceof J.Assignment ?
                                            ((J.Assignment) annotation.getArguments().get(0)).getAssignment() :
                                            annotation.getArguments().get(0);
                                    J.MethodDeclaration updated = applyValueSourceTemplate(
                                            kotlin ? "@ValueSource(strings = [])" : "@ValueSource(strings = {})",
                                            annotation, kotlin, ctx);
                                    return updated.withLeadingAnnotations(ListUtils.map(updated.getLeadingAnnotations(), ann ->
                                            VALUE_SOURCE_MATCHER.matches(ann) ?
                                                    ann.withArguments(ListUtils.map(ann.getArguments(),
                                                            arg -> ((J.Assignment) arg).withAssignment(templateArg.withPrefix(Space.SINGLE_SPACE)))) :
                                                    ann));
                                } else {
                                    Optional<Literal> valueAttribute = annotated.get().getDefaultAttribute("value");
                                    if (!valueAttribute.isPresent()) {
                                        return m;
                                    }
                                    values = valueAttribute.get().getStrings();
                                    if (values.isEmpty()) {
                                        return m;
                                    }
                                }

                                // Build a new ValueSource annotation
                                String valueSourceAnnotationTemplate = buildValueSourceAnnotation(paramType, values, fromTextBlock, kotlin);
                                if (valueSourceAnnotationTemplate == null) {
                                    return m;
                                }

                                // Replace the annotation
                                maybeRemoveImport("org.junit.jupiter.params.provider.CsvSource");
                                maybeAddImport("org.junit.jupiter.params.provider.ValueSource");
                                J.MethodDeclaration replaced = applyValueSourceTemplate(valueSourceAnnotationTemplate, annotation, kotlin, ctx);
                                if (fromTextBlock && values.size() > 1) {
                                    return autoFormat(replaced, ctx);
                                }
                                return replaced;
                            }
                        }

                        return m;
                    }

                    private J.MethodDeclaration applyValueSourceTemplate(String template, J.Annotation annotation, boolean kotlin, ExecutionContext ctx) {
                        if (kotlin) {
                            return KotlinTemplate.builder(template)
                                    .imports("org.junit.jupiter.params.provider.ValueSource")
                                    .parser(KotlinParser.builder().classpathFromResources(ctx, "junit-jupiter-params-5"))
                                    .build()
                                    .apply(getCursor(), annotation.getCoordinates().replace());
                        }
                        return JavaTemplate.builder(template)
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-params-5"))
                                .imports("org.junit.jupiter.params.provider.ValueSource")
                                .build()
                                .apply(getCursor(), annotation.getCoordinates().replace());
                    }

                    private @Nullable String buildValueSourceAnnotation(String paramType, List<String> values, boolean multiLine, boolean kotlin) {
                        String attributeName;
                        String formattedValues;

                        String delimiter = multiLine && values.size() > 1 ? ",\n " : ", ";
                        Function<String, String> mapper;
                        switch (paramType) {
                            case "String":
                                attributeName = "strings";
                                mapper = v -> "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
                                break;
                            case "int":
                            case "Integer":
                                attributeName = "ints";
                                mapper = Function.identity();
                                break;
                            case "long":
                            case "Long":
                                attributeName = "longs";
                                mapper = v -> v.endsWith("L") || v.endsWith("l") ? v : v + "L";
                                break;
                            case "double":
                            case "Double":
                                attributeName = "doubles";
                                mapper = Function.identity();
                                break;
                            case "float":
                            case "Float":
                                attributeName = "floats";
                                mapper = v -> v.endsWith("f") || v.endsWith("F") ? v : v + "f";
                                break;
                            case "boolean":
                            case "Boolean":
                                attributeName = "booleans";
                                mapper = Function.identity();
                                break;
                            case "char":
                            case "Character":
                                attributeName = "chars";
                                mapper = v -> "'" + v + "'";
                                break;
                            case "byte":
                            case "Byte":
                                attributeName = "bytes";
                                mapper = Function.identity();
                                break;
                            case "short":
                            case "Short":
                                attributeName = "shorts";
                                mapper = Function.identity();
                                break;
                            default:
                                return null;
                        }
                        formattedValues = values.stream()
                                .map(String::trim)
                                .map(mapper)
                                .collect(joining(delimiter));

                        if (values.size() == 1) {
                            return "@ValueSource(" + attributeName + " = " + formattedValues + ")";
                        }
                        String open = kotlin ? "[" : "{";
                        String close = kotlin ? "]" : "}";
                        if (multiLine) {
                            return "@ValueSource(" + attributeName + " = " + open + "\n" + formattedValues + "\n" + close + ")";
                        }
                        return "@ValueSource(" + attributeName + " = " + open + formattedValues + close + ")";
                    }

                    private List<String> parseTextBlockLines(String textBlock) {
                        List<String> result = new ArrayList<>();
                        for (String line : textBlock.split("\n")) {
                            String trimmed = line.trim();
                            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                                continue;
                            }
                            result.add(trimmed);
                        }
                        return result;
                    }

                    private @Nullable String getParameterType(J.VariableDeclarations param) {
                        if (param.getType() == null) {
                            return null;
                        }
                        if (param.getTypeAsFullyQualified() != null) {
                            // Boxed primitive types
                            return param.getTypeAsFullyQualified().getClassName();
                        }
                        if (param.getType() instanceof Primitive) {
                            Primitive primitive = (Primitive) param.getType();
                            switch (primitive) {
                                case Boolean:
                                    return "boolean";
                                case Byte:
                                    return "byte";
                                case Char:
                                    return "char";
                                case Double:
                                    return "double";
                                case Float:
                                    return "float";
                                case Int:
                                    return "int";
                                case Long:
                                    return "long";
                                case Short:
                                    return "short";
                            }
                        }

                        return null;
                    }
                });
    }

}
