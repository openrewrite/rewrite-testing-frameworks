package org.openrewrite.java.testing.junit5;

import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;
import java.util.stream.IntStream;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.openrewrite.Tree.randomId;

public class UpdateMockWebServerMockResponse extends Recipe {
    private static final String OLD_MOCKRESPONSE_FQN = "okhttp3.mockwebserver.MockResponse";
    private static final String OLD_MOCKRESPONSE_STATUS = OLD_MOCKRESPONSE_FQN + " status(java.lang.String)";
    private static final String OLD_MOCKRESPONSE_SETSTATUS = OLD_MOCKRESPONSE_FQN + " setStatus(java.lang.String)";
    private static final String OLD_MOCKRESPONSE_HEADERS = OLD_MOCKRESPONSE_FQN + " headers(okhttp3.Headers)";
    private static final String OLD_MOCKRESPONSE_SETHEADERS = OLD_MOCKRESPONSE_FQN + " setHeaders(okhttp3.Headers)";
    private static final String NEW_MOCKRESPONSE_FQN = "mockwebserver3.MockResponse";
    private static final String NEW_MOCKRESPONSE_BUILDER_FQN = NEW_MOCKRESPONSE_FQN + "$Builder";

    @Override
    public String getDisplayName() {
        return "OkHttp `MockWebServer` `MockResponse` to 5.x `MockWebServer3` `MockResponse`";
    }

    @Override
    public String getDescription() {
        return "Replace usages of OkHttp MockWebServer `MockResponse` with 5.x MockWebServer3 `MockResponse` and it's `Builder`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(OLD_MOCKRESPONSE_FQN, false), new JavaIsoVisitor<ExecutionContext>() {
            private final Map<UUID, List<Integer>> methodInvocationsToAdjust = new HashMap<>();

            @Override
            public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
                J j = (J) tree;
                j = new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation methodInv, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(methodInv, ctx);
                        List<Integer> indexes = IntStream.range(0, mi.getArguments().size())
                                .filter(x -> TypeUtils.isAssignableTo(OLD_MOCKRESPONSE_FQN, mi.getArguments().get(x).getType()))
                                .boxed().collect(toList());
                        if (!indexes.isEmpty() && !methodInvocationsToAdjust.containsKey(mi.getId())) {
                            methodInvocationsToAdjust.put(mi.getId(), indexes);
                        }
                        return mi;
                    }
                }.visit(j, ctx);
                j = (J) new ChangeMethodInvocationReturnType(
                        OLD_MOCKRESPONSE_STATUS,
                        OLD_MOCKRESPONSE_FQN
                ).getVisitor().visit(j, ctx);
                j = (J) new ChangeMethodName(
                        OLD_MOCKRESPONSE_SETSTATUS,
                        "status",
                        null,
                        null
                ).getVisitor().visit(j, ctx);
                j = (J) new ChangeMethodInvocationReturnType(
                        OLD_MOCKRESPONSE_HEADERS,
                        OLD_MOCKRESPONSE_FQN
                ).getVisitor().visit(j, ctx);
                j = (J) new ChangeMethodName(
                        OLD_MOCKRESPONSE_SETHEADERS,
                        "headers",
                        null,
                        null
                ).getVisitor().visit(j, ctx);
                j = (J) new ChangeType(
                        OLD_MOCKRESPONSE_FQN,
                        NEW_MOCKRESPONSE_BUILDER_FQN,
                        null
                ).getVisitor().visit(j, ctx);
                j = new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation methodInv, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(methodInv, ctx);
                        List<Integer> indexes = methodInvocationsToAdjust.getOrDefault(mi.getId(), emptyList());
                        if (!indexes.isEmpty()) {
                            methodInvocationsToAdjust.remove(mi.getId());
                            List<Expression> newArgs = ListUtils.map(mi.getArguments(), (index, arg) -> {
                                if (indexes.contains(index)) {
                                    return new J.MethodInvocation(
                                            arg.getId(),
                                            Space.EMPTY,
                                            Markers.EMPTY,
                                            new JRightPadded<>(arg.withId(randomId()), Space.EMPTY, arg.getMarkers()),
                                            null,
                                            new J.Identifier(
                                                randomId(),
                                                Space.EMPTY,
                                                Markers.EMPTY,
                                                emptyList(),
                                                "build",
                                                null,
                                                null
                                            ),
                                            JContainer.build(Space.EMPTY, emptyList(), Markers.EMPTY),
                                            new JavaType.Method(
                                                    null,
                                                    Flag.Public.getBitMask(),
                                                    TypeUtils.asFullyQualified(arg.getType()),
                                                    "build",
                                                    JavaType.buildType(NEW_MOCKRESPONSE_FQN),
                                                    emptyList(),
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    null
                                            )
                                    );
                                }
                                return arg;
                            });
                            J.MethodInvocation updated = mi.withArguments(newArgs);
                            updated = updated.getPadding().withArguments(JContainer.withElements(mi.getPadding().getArguments(), newArgs));
                            return updated;
                        }
                        return mi;
                    }
                }.visit(j, ctx);
                return j;
            }
        });
    }
}
