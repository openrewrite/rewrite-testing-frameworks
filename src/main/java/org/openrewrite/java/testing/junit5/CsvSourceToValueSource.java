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

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaCoordinates;

import java.util.ArrayList;
import java.util.List;

public class CsvSourceToValueSource extends Recipe {
    private static final AnnotationMatcher CSV_SOURCE_MATCHER = new AnnotationMatcher("@org.junit.jupiter.params.provider.CsvSource");

    @Override
    public String getDisplayName() {
        return "Replace `@CsvSource` with `@ValueSource` for single method arguments";
    }

    @Override
    public String getDescription() {
        return "Replaces JUnit 5's `@CsvSource` annotation with `@ValueSource` when the parameterized test has only a single method argument.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.junit.jupiter.params.provider.CsvSource", false), new CsvSourceVisitor());
    }

    private static class CsvSourceVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

            // Check if method has exactly one parameter
            if (m.getParameters().size() != 1) {
                return m;
            }

            // Check if there's only one non-receiver parameter
            long paramCount = m.getParameters().stream()
                    .filter(p -> p instanceof J.VariableDeclarations)
                    .count();
            if (paramCount != 1) {
                return m;
            }

            // Find @CsvSource annotation
            for (J.Annotation annotation : m.getLeadingAnnotations()) {
                if (CSV_SOURCE_MATCHER.matches(annotation)) {
                    // Get the parameter type
                    J.VariableDeclarations param = (J.VariableDeclarations) m.getParameters().get(0);
                    String paramType = getParameterType(param);
                    if (paramType == null) {
                        continue;
                    }

                    // Extract values from CsvSource
                    List<String> values = extractCsvValues(annotation);
                    if (values.isEmpty()) {
                        continue;
                    }

                    // Build the ValueSource annotation
                    String valueSourceAnnotation = buildValueSourceAnnotation(paramType, values);
                    if (valueSourceAnnotation == null) {
                        continue;
                    }

                    // Replace the annotation
                    maybeRemoveImport("org.junit.jupiter.params.provider.CsvSource");
                    maybeAddImport("org.junit.jupiter.params.provider.ValueSource");
                    return JavaTemplate.builder(valueSourceAnnotation)
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-params-5"))
                            .imports("org.junit.jupiter.params.provider.ValueSource")
                            .build()
                            .apply(getCursor(), annotation.getCoordinates().replace());
                }
            }

            return m;
        }

        private List<String> extractCsvValues(J.Annotation annotation) {
            List<String> values = new ArrayList<>();
            if (annotation.getArguments() != null) {
                for (org.openrewrite.java.tree.Expression arg : annotation.getArguments()) {
                    if (arg instanceof J.Assignment) {
                        J.Assignment assignment = (J.Assignment) arg;
                        if (assignment.getVariable() instanceof J.Identifier) {
                            J.Identifier id = (J.Identifier) assignment.getVariable();
                            if ("value".equals(id.getSimpleName())) {
                                extractValuesFromExpression(assignment.getAssignment(), values);
                            }
                        }
                    } else if (arg instanceof J.NewArray) {
                        extractValuesFromExpression(arg, values);
                    } else if (arg instanceof J.Literal) {
                        J.Literal literal = (J.Literal) arg;
                        if (literal.getValue() instanceof String) {
                            values.add((String) literal.getValue());
                        }
                    }
                }
            }
            return values;
        }

        private void extractValuesFromExpression(org.openrewrite.java.tree.Expression expr, List<String> values) {
            if (expr instanceof J.NewArray) {
                J.NewArray array = (J.NewArray) expr;
                if (array.getInitializer() != null) {
                    for (org.openrewrite.java.tree.Expression element : array.getInitializer()) {
                        if (element instanceof J.Literal) {
                            J.Literal literal = (J.Literal) element;
                            if (literal.getValue() instanceof String) {
                                values.add((String) literal.getValue());
                            }
                        }
                    }
                }
            } else if (expr instanceof J.Literal) {
                J.Literal literal = (J.Literal) expr;
                if (literal.getValue() instanceof String) {
                    values.add((String) literal.getValue());
                }
            }
        }

        private @Nullable String buildValueSourceAnnotation(String paramType, List<String> values) {
            String attributeName;
            String formattedValues;

            switch (paramType) {
                case "String":
                    attributeName = "strings";
                    formattedValues = formatStringValues(values);
                    break;
                case "int":
                case "Integer":
                    attributeName = "ints";
                    formattedValues = format(values);
                    break;
                case "long":
                case "Long":
                    attributeName = "longs";
                    formattedValues = formatLongValues(values);
                    break;
                case "double":
                case "Double":
                    attributeName = "doubles";
                    formattedValues = format(values);
                    break;
                case "float":
                case "Float":
                    attributeName = "floats";
                    formattedValues = formatFloatValues(values);
                    break;
                case "boolean":
                case "Boolean":
                    attributeName = "booleans";
                    formattedValues = format(values);
                    break;
                case "char":
                case "Character":
                    attributeName = "chars";
                    formattedValues = formatCharValues(values);
                    break;
                case "byte":
                case "Byte":
                    attributeName = "bytes";
                    formattedValues = format(values);
                    break;
                case "short":
                case "Short":
                    attributeName = "shorts";
                    formattedValues = format(values);
                    break;
                default:
                    return null;
            }

            if (values.size() == 1) {
                return "@ValueSource(" + attributeName + " = " + formattedValues + ")";
            }
            return "@ValueSource(" + attributeName + " = {" + formattedValues + "})";
        }

        private static String format(List<String> values) {
            return String.join(", ", values.stream()
                    .map(String::trim)
                    .toArray(String[]::new));
        }

        private String formatStringValues(List<String> values) {
            return String.join(", ", values.stream()
                    .map(v -> "\"" + v + "\"")
                    .toArray(String[]::new));
        }

        private String formatLongValues(List<String> values) {
            return String.join(", ", values.stream()
                    .map(v -> v.trim().endsWith("L") || v.trim().endsWith("l") ? v.trim() : v.trim() + "L")
                    .toArray(String[]::new));
        }

        private String formatFloatValues(List<String> values) {
            return String.join(", ", values.stream()
                    .map(v -> v.trim().endsWith("f") || v.trim().endsWith("F") ? v.trim() : v.trim() + "f")
                    .toArray(String[]::new));
        }

        private String formatCharValues(List<String> values) {
            return String.join(", ", values.stream()
                    .map(v -> "'" + v.trim() + "'")
                    .toArray(String[]::new));
        }

        private @Nullable String getParameterType(J.VariableDeclarations param) {
            if (param.getType() == null) {
                return null;
            }

            if (param.getType() instanceof org.openrewrite.java.tree.JavaType.Primitive) {
                org.openrewrite.java.tree.JavaType.Primitive primitive = (org.openrewrite.java.tree.JavaType.Primitive) param.getType();
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
                    default:
                        return null;
                }
            } else if (param.getTypeAsFullyQualified() != null) {
                return param.getTypeAsFullyQualified().getClassName();
            }

            return null;
        }
    }
}
