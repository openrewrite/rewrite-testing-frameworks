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
package org.openrewrite.java.testing.gatling;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;
import static org.openrewrite.Tree.randomId;

public class MigrateGatlingScalaDsl extends Recipe {

    // Deprecated in 3.7 or earlier and removed in 3.11, or renamed along with the injector in 3.12
    private static final Map<String, String> RENAMES = new HashMap<>();

    static {
        RENAMES.put("heavisideUsers", "stressPeakUsers");
        RENAMES.put("WhiteList", "AllowList");
        RENAMES.put("BlackList", "DenyList");
        RENAMES.put("polling", "poll");
        RENAMES.put("ignoreDefaultChecks", "ignoreProtocolChecks");
        RENAMES.put("ignoreDefaultHeaders", "ignoreProtocolHeaders");
        RENAMES.put("stopInjector", "stopLoadGenerator");
        RENAMES.put("stopInjectorIf", "stopLoadGeneratorIf");
    }

    // The connection counts the removed browser presets of `HttpProtocolBuilder` stood for
    private static final Map<String, Integer> MAX_CONNECTIONS_PER_HOST = new HashMap<>();

    static {
        MAX_CONNECTIONS_PER_HOST.put("maxConnectionsPerHostLikeFirefoxOld", 2);
        MAX_CONNECTIONS_PER_HOST.put("maxConnectionsPerHostLikeFirefox", 6);
        MAX_CONNECTIONS_PER_HOST.put("maxConnectionsPerHostLikeOperaOld", 4);
        MAX_CONNECTIONS_PER_HOST.put("maxConnectionsPerHostLikeOpera", 6);
        MAX_CONNECTIONS_PER_HOST.put("maxConnectionsPerHostLikeSafariOld", 4);
        MAX_CONNECTIONS_PER_HOST.put("maxConnectionsPerHostLikeSafari", 6);
        MAX_CONNECTIONS_PER_HOST.put("maxConnectionsPerHostLikeIE7", 2);
        MAX_CONNECTIONS_PER_HOST.put("maxConnectionsPerHostLikeIE8", 6);
        MAX_CONNECTIONS_PER_HOST.put("maxConnectionsPerHostLikeIE10", 8);
        MAX_CONNECTIONS_PER_HOST.put("maxConnectionsPerHostLikeChrome", 6);
    }

    @Getter
    final String displayName = "Migrate removed Gatling Scala DSL methods";

    @Getter
    final String description = "Rename the Gatling Scala DSL methods that were deprecated in 3.x and removed in 3.11 " +
            "and 3.12: `heavisideUsers` to `stressPeakUsers`, `WhiteList` and `BlackList` to `AllowList` and " +
            "`DenyList`, `polling` to `poll`, `ignoreDefaultChecks` and `ignoreDefaultHeaders` to " +
            "`ignoreProtocolChecks` and `ignoreProtocolHeaders`, `stopInjector` to `stopLoadGenerator` and the " +
            "`maxConnectionsPerHostLikeXXX` browser presets to `maxConnectionsPerHost(n)`. Also rename feeder " +
            "`convert` to `transform`, and the check `transform` and `transformOption` taking the `Session` to " +
            "`transformWithSession` and `transformOptionWithSession`, as done in 3.4. Methods are matched " +
            "by name in sources importing Gatling, as their declaring types changed between versions, skipping " +
            "those attributed to types outside Gatling, such as the source's own and the Scala library's.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(GatlingSources.importingGatling(false), new JavaVisitor<ExecutionContext>() {

            @Override
            public J visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier i = (J.Identifier) super.visitIdentifier(identifier, ctx);
                String newName = RENAMES.get(i.getSimpleName());
                if (newName == null || getCursor().getParentTreeCursor().getValue() instanceof J.MethodDeclaration ||
                    !mayBeGatling(i.getFieldType() != null ? i.getFieldType() : i.getType())) {
                    return i;
                }
                return i.withSimpleName(newName);
            }

            @Override
            public J visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                J j = super.visitFieldAccess(fieldAccess, ctx);
                if (!(j instanceof J.FieldAccess)) {
                    return j;
                }
                J.FieldAccess fa = (J.FieldAccess) j;
                Integer connections = MAX_CONNECTIONS_PER_HOST.get(fa.getSimpleName());
                if (connections == null || !mayBeGatling(fa.getTarget().getType())) {
                    return fa;
                }
                return new J.MethodInvocation(
                        randomId(),
                        fa.getPrefix(),
                        fa.getMarkers(),
                        JRightPadded.<Expression>build(fa.getTarget()).withAfter(fa.getPadding().getName().getBefore()),
                        null,
                        fa.getName().withSimpleName("maxConnectionsPerHost"),
                        connectionsArgument(connections),
                        null);
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J j = super.visitMethodInvocation(method, ctx);
                if (!(j instanceof J.MethodInvocation)) {
                    return j;
                }
                J.MethodInvocation m = (J.MethodInvocation) j;
                String name = m.getSimpleName();
                if (m.getSelect() == null || !mayBeGatling(m.getMethodType())) {
                    return m;
                }
                Integer connections = MAX_CONNECTIONS_PER_HOST.get(name);
                if (connections != null && hasNoArguments(m)) {
                    return m.withName(m.getName().withSimpleName("maxConnectionsPerHost"))
                            .getPadding().withArguments(connectionsArgument(connections));
                }
                if ("convert".equals(name) && isPartialFunction(m)) {
                    return m.withName(m.getName().withSimpleName("transform"));
                }
                if (("transform".equals(name) || "transformOption".equals(name)) && takesTheSession(m)) {
                    return m.withName(m.getName().withSimpleName(name + "WithSession"));
                }
                return m;
            }
        });
    }

    // Gatling's DSL may lack type attribution, while the source's own declarations and the Scala library have it
    private static boolean mayBeGatling(@Nullable JavaType type) {
        if (type == null || type instanceof JavaType.Unknown) {
            return true;
        }
        if (type instanceof JavaType.Method) {
            return mayBeGatling(((JavaType.Method) type).getDeclaringType());
        }
        if (type instanceof JavaType.Variable) {
            return mayBeGatling(((JavaType.Variable) type).getOwner());
        }
        return type instanceof JavaType.FullyQualified &&
                ((JavaType.FullyQualified) type).getFullyQualifiedName().startsWith("io.gatling.");
    }

    private static JContainer<Expression> connectionsArgument(int connections) {
        J.Literal literal = new J.Literal(randomId(), Space.EMPTY, Markers.EMPTY, connections,
                String.valueOf(connections), null, JavaType.Primitive.Int);
        return JContainer.build(Space.EMPTY, singletonList(JRightPadded.build(literal)), Markers.EMPTY);
    }

    private static boolean hasNoArguments(J.MethodInvocation method) {
        List<Expression> arguments = method.getArguments();
        return arguments.isEmpty() || (arguments.size() == 1 && arguments.get(0) instanceof J.Empty);
    }

    // Feeders took a `PartialFunction`, written as a block of `case` clauses
    private static boolean isPartialFunction(J.MethodInvocation method) {
        if (method.getArguments().size() != 1 || !(method.getArguments().get(0) instanceof J.Lambda)) {
            return false;
        }
        J body = ((J.Lambda) method.getArguments().get(0)).getBody();
        return body instanceof J.Block &&
                !((J.Block) body).getStatements().isEmpty() &&
                ((J.Block) body).getStatements().stream().allMatch(J.Case.class::isInstance);
    }

    // Before 3.4, checks overloaded `transform` with a function also taking the `Session`
    private static boolean takesTheSession(J.MethodInvocation method) {
        if (method.getArguments().size() != 1 || !(method.getArguments().get(0) instanceof J.Lambda)) {
            return false;
        }
        return ((J.Lambda) method.getArguments().get(0)).getParameters().getParameters().size() == 2;
    }
}
