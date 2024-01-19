/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.java.testing.testcontainers;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import java.util.Collections;

import static java.util.Collections.singletonList;

@RequiredArgsConstructor
public class ExplicitContainerImage extends Recipe {
    @Option(displayName = "Container class",
            description = "The fully qualified name of the container class to use.",
            example = "org.testcontainers.containers.NginxContainer")
    private final String containerClass;

    @Option(displayName = "Image to use",
            description = "The image to use for the container.",
            example = "nginx:1.9.4")
    private final String image;

    @Option(displayName = "Parse image",
            description = "Whether to call `DockerImageName.parse(image)`.",
            required = false)
    private final Boolean parseImage;

    @Override
    public String getDisplayName() {
        return "Add image argument to container constructor";
    }

    @Override
    public String getDescription() {
        return "Set the image to use for a container explicitly if unset, rather than relying on the default image for the container class.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        final MethodMatcher methodMatcher = new MethodMatcher(containerClass + " <constructor>()");
        return Preconditions.check(new UsesMethod<>(methodMatcher), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass nc = super.visitNewClass(newClass, ctx);

                if (methodMatcher.matches(nc)) {
                    Expression constructorArgument = getConstructorArgument(nc);
                    return nc.withArguments(Arrays.asList(constructorArgument))
                            .withMethodType(nc.getMethodType()
                                    .withParameterTypes(singletonList(constructorArgument.getType()))
                                    .withParameterNames(singletonList("image")));
                }
                return nc;
            }

            @NotNull
            private Expression getConstructorArgument(J.NewClass newClass) {
                if (parseImage != null && parseImage) {
                    maybeAddImport("org.testcontainers.utility.DockerImageName");
                    return JavaTemplate.builder("DockerImageName.parse(\"" + image + "\")")
                            .imports("org.testcontainers.utility.DockerImageName")
                            .javaParser(JavaParser.fromJavaVersion().classpath("testcontainers"))
                            .build()
                            .apply(getCursor(), newClass.getCoordinates().replace())
                            .withPrefix(Space.EMPTY);
                }
                return new J.Literal(Tree.randomId(), Space.EMPTY, Markers.EMPTY, image, "\"" + image + "\"", null, JavaType.Primitive.String);
            }
        });
    }
}
