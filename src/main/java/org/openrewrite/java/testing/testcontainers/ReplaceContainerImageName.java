/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.testing.testcontainers;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.marker.Markers;

@Value
@EqualsAndHashCode(callSuper = false)
public class ReplaceContainerImageName extends Recipe {
    @Option(displayName = "Container class",
            description = "The fully qualified name of the container class to match.",
            example = "org.testcontainers.containers.KafkaContainer")
    String containerClass;

    @Option(displayName = "Image prefix to match",
            description = "The Docker image prefix to match (e.g. `confluentinc/cp-kafka`).",
            example = "confluentinc/cp-kafka")
    String imagePrefix;

    @Option(displayName = "New image",
            description = "The new Docker image to use, including tag.",
            example = "apache/kafka-native:3.8.0")
    String newImage;

    @Override
    public String getDisplayName() {
        return "Replace container image name";
    }

    @Override
    public String getDescription() {
        return "Replace a Docker image name in `DockerImageName.parse(image)` constructor arguments " +
               "for a specific container class.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        MethodMatcher dockerImageNameParse = new MethodMatcher(
                "org.testcontainers.utility.DockerImageName parse(String)");
        return Preconditions.check(new UsesType<>(containerClass, true), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal l = super.visitLiteral(literal, ctx);
                if (l.getType() != JavaType.Primitive.String || l.getValue() == null) {
                    return l;
                }
                String value = (String) l.getValue();
                if (!value.startsWith(imagePrefix)) {
                    return l;
                }
                // Verify this literal is inside DockerImageName.parse(...)
                Cursor parent = getCursor().getParentTreeCursor();
                if (!(parent.getValue() instanceof J.MethodInvocation)) {
                    return l;
                }
                J.MethodInvocation mi = parent.getValue();
                if (!dockerImageNameParse.matches(mi)) {
                    return l;
                }
                return l.withValue(newImage)
                        .withValueSource("\"" + newImage + "\"");
            }
        });
    }
}
