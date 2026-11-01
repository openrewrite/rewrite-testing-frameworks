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
package org.openrewrite.java.testing.testcontainers;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.*;
import org.openrewrite.semver.Semver;
import org.openrewrite.semver.VersionComparator;

@Value
@EqualsAndHashCode(callSuper = false)
public class MigrateKafkaContainer extends ScanningRecipe<MigrateKafkaContainer.Accumulator> {
    private static final String OLD_TYPE = "org.testcontainers.containers.KafkaContainer";
    private static final MethodMatcher PARSE = new MethodMatcher("org.testcontainers.utility.DockerImageName parse(String)");

    private static final VersionComparator CONFLUENT_VERSIONS = Semver.validate("[7.4.0,)", null).getValue();

    String displayName = "Migrate Kafka containers preserving the image provider";

    String description = "Migrate legacy Kafka containers to ConfluentKafkaContainer for explicit Confluent images, " +
            "or KafkaContainer for explicit Apache images, without changing the image. " +
            "Confluent images must specify a version of at least 7.4.0. " +
            "Leave migrations with unresolved images, mixed providers, subclasses, or legacy Kafka-specific APIs for manual review. " +
            "All legacy constructions must identify the same provider so declarations shared across source files remain consistent.";

    static class Accumulator {
        String provider;
        boolean unsafe;
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass n = super.visitNewClass(newClass, ctx);
                if (n.getClazz() == null || !TypeUtils.isOfClassType(n.getClazz().getType(), OLD_TYPE)) {
                    return n;
                }
                String provider = null;
                if (n.getBody() == null && n.getArguments().size() == 1 && n.getArguments().get(0) instanceof J.MethodInvocation) {
                    J.MethodInvocation image = (J.MethodInvocation) n.getArguments().get(0);
                    if (PARSE.matches(image) && image.getArguments().get(0) instanceof J.Literal) {
                        Object value = ((J.Literal) image.getArguments().get(0)).getValue();
                        if (value instanceof String) {
                            String name = ((String) value).split("[:@]", 2)[0];
                            if ("confluentinc/cp-kafka".equals(name) && ((String) value).startsWith(name + ":") &&
                                    CONFLUENT_VERSIONS.isValid(null, ((String) value).substring(name.length() + 1))) {
                                provider = "org.testcontainers.kafka.ConfluentKafkaContainer";
                            } else if ("apache/kafka".equals(name) || "apache/kafka-native".equals(name)) {
                                provider = "org.testcontainers.kafka.KafkaContainer";
                            }
                        }
                    }
                }
                if (provider == null || acc.provider != null && !acc.provider.equals(provider)) {
                    acc.unsafe = true;
                } else {
                    acc.provider = provider;
                }
                return n;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                if (c.getExtends() != null && TypeUtils.isOfClassType(c.getExtends().getType(), OLD_TYPE)) {
                    acc.unsafe = true;
                }
                return c;
            }

            @Override
            public J.MemberReference visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
                J.MemberReference m = super.visitMemberReference(memberRef, ctx);
                if (m.getMethodType() != null && TypeUtils.isOfClassType(m.getMethodType().getDeclaringType(), OLD_TYPE) &&
                        !"getBootstrapServers".equals(m.getReference().getSimpleName())) {
                    acc.unsafe = true;
                }
                return m;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier i = super.visitIdentifier(identifier, ctx);
                if (i.getFieldType() != null && i.getFieldType().getOwner() instanceof JavaType.FullyQualified &&
                        TypeUtils.isOfClassType(i.getFieldType().getOwner(), OLD_TYPE)) {
                    acc.unsafe = true;
                }
                return i;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (m.getMethodType() != null && TypeUtils.isOfClassType(m.getMethodType().getDeclaringType(), OLD_TYPE) &&
                        !"getBootstrapServers".equals(m.getSimpleName())) {
                    acc.unsafe = true;
                }
                return m;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return acc.unsafe || acc.provider == null ? TreeVisitor.noop() : new ChangeType(OLD_TYPE, acc.provider, false).getVisitor();
    }
}
