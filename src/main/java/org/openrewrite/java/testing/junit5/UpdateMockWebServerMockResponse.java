package org.openrewrite.java.testing.junit5;

import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import static java.util.Objects.requireNonNull;

public class UpdateMockWebServerMockResponse extends Recipe {
    private static final String OLD_MOCKRESPONSE_FQN = "okhttp3.mockwebserver.MockResponse";
    private static final MethodMatcher OLD_MOCKRESPONSE_CONSTRUCTOR_MATCHER = new MethodMatcher(OLD_MOCKRESPONSE_FQN + " <constructor>()");
    private static final String OLD_MOCKRESPONSE_STATUS = OLD_MOCKRESPONSE_FQN + " status(java.lang.String)";
    private static final MethodMatcher OLD_MOCKRESPONSE_STATUS_MATCHER = new MethodMatcher(OLD_MOCKRESPONSE_STATUS);
    private static final String OLD_MOCKRESPONSE_SETSTATUS = OLD_MOCKRESPONSE_FQN + " setStatus(java.lang.String)";
    private static final MethodMatcher OLD_MOCKRESPONSE_SETSTATUS_MATCHER = new MethodMatcher(OLD_MOCKRESPONSE_SETSTATUS);
    private static final String NEW_MOCKRESPONSE_FQN = "mockwebserver3.MockResponse";
    private static final String NEW_MOCKRESPONSE_BUILDER_FQN = NEW_MOCKRESPONSE_FQN + ".Builder";
    private static final String NEW_MOCKRESPONSE_BUILDER_CONSTRUCTOR = NEW_MOCKRESPONSE_BUILDER_FQN + " <constructor>()";

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
            @Override
            public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
                J j = (J) tree;
                // TODO: look into whether I could have used ...MockResponse$Builder to any other effect
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
                j = (J) new ChangeType(
                        OLD_MOCKRESPONSE_FQN,
                        NEW_MOCKRESPONSE_BUILDER_FQN,
                        null
                ).getVisitor().visit(j, ctx);
                j = new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                        J.NewClass nc = super.visitNewClass(newClass, ctx);
                        if (new MethodMatcher(NEW_MOCKRESPONSE_BUILDER_CONSTRUCTOR).matches(nc)) {
                            maybeAddImport(NEW_MOCKRESPONSE_FQN);
                            maybeRemoveImport(NEW_MOCKRESPONSE_BUILDER_FQN);
                            return JavaTemplate
                                    .builder("new MockResponse.Builder()")
                                    .imports("mockwebserver3.MockResponse")
                                    .javaParser(JavaParser.fromJavaVersion().classpath("mockwebserver3"))
                                    .build()
                                    .apply(getCursor(), nc.getCoordinates().replace());
                        }
                        return nc;
                    }

                    @Override
                    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations varDecl, ExecutionContext ctx) {
                        J.VariableDeclarations vd = super.visitVariableDeclarations(varDecl, ctx);
                        if (TypeUtils.isAssignableTo(NEW_MOCKRESPONSE_BUILDER_FQN, requireNonNull(vd.getVariables().get(0).getVariableType()).getType())) {
                            return vd.withTypeExpression(requireNonNull(((J.Identifier) vd.getTypeExpression())).withSimpleName("MockResponse.Builder"));
                        }
                        return vd;
                    }
                }.visit(j, ctx);
//                j = (J) new ChangeMethodInvocationReturnType(
//                        OLD_MOCKRESPONSE_STATUS,
//                        NEW_MOCKRESPONSE_BUILDER_FQN
//                ).getVisitor().visit(j, ctx);
//                j = (J) new ChangeMethodInvocationReturnType(
//                        OLD_MOCKRESPONSE_SETSTATUS,
//                        NEW_MOCKRESPONSE_BUILDER_FQN
//                ).getVisitor().visit(j, ctx);
//                j = new ChangeFieldType<ExecutionContext>(
//                        (JavaType.FullyQualified) JavaType.buildType(OLD_MOCKRESPONSE_FQN),
//                        (JavaType.FullyQualified) JavaType.buildType(NEW_MOCKRESPONSE_BUILDER_FQN)
//                ).visit(j, ctx);
//                j = (J) new ChangeMethodName(
//                        OLD_MOCKRESPONSE_SETSTATUS,
//                        "status",
//                        null,
//                        null
//                ).getVisitor().visit(j, ctx);
//                j = new JavaIsoVisitor<ExecutionContext>() {
//                    @Override
//                    public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
//                        J.NewClass nc = super.visitNewClass(newClass, ctx);
//                        if (OLD_MOCKRESPONSE_CONSTRUCTOR_MATCHER.matches(nc)) {
//                            maybeAddImport(NEW_MOCKRESPONSE_FQN);
//                            maybeRemoveImport(OLD_MOCKRESPONSE_FQN);
//                            return JavaTemplate
//                                    .builder("new MockResponse.Builder()")
//                                    .imports("mockwebserver3.MockResponse", "mockwebserver3.MockResponse.Builder")
//                                    .javaParser(JavaParser.fromJavaVersion().classpath("mockwebserver3"))
//                                    .build()
//                                    .apply(getCursor(), nc.getCoordinates().replace());
//                        }
//                        return nc;
//                    }
//                }.visit(j, ctx);
//                j = new ChangeMethodInvocationReturnType()
//                j = new JavaIsoVisitor<ExecutionContext>() {
//                    @Override
//                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation methodInv, ExecutionContext ctx) {
//                        J.MethodInvocation mi = super.visitMethodInvocation(methodInv, ctx);
//                        if (OLD_MOCKRESPONSE_SETSTATUS_MATCHER.matches(mi)) {
//                            mi = JavaTemplate
//                                    .builder("status(#{any(java.lang.String)})")
//                                    .imports("mockwebserver3.MockResponse", "mockwebserver3.MockResponse.Builder")
//                                    .contextSensitive()
//                                    .build()
//                                    .apply(updateCursor(mi), mi.getCoordinates().replaceMethod(), mi.getArguments().get(0));
//                        }
////                        if (OLD_MOCKRESPONSE_STATUS_MATCHER.matches(mi)) {
////                            mi = JavaTemplate
////                                    .builder("status(#{any(java.lang.String)})")
////                                    .imports("mockwebserver3.MockResponse", "mockwebserver3.MockResponse.Builder")
////                                    .contextSensitive()
////                                    .build()
////                                    .apply(updateCursor(mi), mi.getCoordinates().replaceMethod(), mi.getArguments().get(0));
////                        }
//                        return mi;
//                    }
//                }.visit(tree, ctx);
                j = new JavaIsoVisitor<ExecutionContext>() {
//                    @Override
//                    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations varDecl, ExecutionContext ctx) {
//                        if (TypeUtils.isAssignableTo(OLD_MOCKRESPONSE_FQN, varDecl.getType())) {
//                            return varDecl.withTypeExpression(requireNonNull(((J.Identifier) varDecl.getTypeExpression()))
//                                    .withSimpleName("MockResponse.Builder")
//                                    .withType(JavaType.buildType(NEW_MOCKRESPONSE_BUILDER_FQN))
//                            );
//                        }
//                        return super.visitVariableDeclarations(varDecl, ctx);
//                    }
//
//                    @Override
//                    public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
//                        J.NewClass nc = super.visitNewClass(newClass, ctx);
//                        if (OLD_MOCKRESPONSE_CONSTRUCTOR_MATCHER.matches(nc)) {
//                            maybeAddImport(NEW_MOCKRESPONSE_FQN);
//                            maybeRemoveImport(OLD_MOCKRESPONSE_FQN);
//                            return JavaTemplate
//                                    .builder("new MockResponse.Builder()")
//                                    .imports("mockwebserver3.MockResponse")
//                                    .javaParser(JavaParser.fromJavaVersion().classpath("mockwebserver3"))
//                                    .build()
//                                    .apply(getCursor(), nc.getCoordinates().replace());
//                        }
//                        return nc;
//                    }
//
//                    @Override
//                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation methodInv, ExecutionContext ctx) {
//                        J.MethodInvocation mi = super.visitMethodInvocation(methodInv, ctx);
//                        if (OLD_MOCKRESPONSE_SETSTATUS_MATCHER.matches(mi)) {
//                            return JavaTemplate
//                                    .builder("status(#{any(java.lang.String)})")
//                                    .contextSensitive()
//                                    .build()
//                                    .apply(getCursor(), mi.getCoordinates().replaceMethod(), mi.getArguments().get(0));
//                        }
//                        return mi;
//                    }
                }.visit(j, ctx);
                return j;
            }
            //            @Override
//            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
//                J.NewClass nc = super.visitNewClass(newClass, ctx);
//                if (OLD_MOCKRESPONSE_CONSTRUCTOR_MATCHER.matches(nc)) {
//                    maybeAddImport(NEW_MOCKRESPONSE_FQN);
//                    maybeRemoveImport(OLD_MOCKRESPONSE_FQN);
//                    return JavaTemplate
//                            .builder("new MockResponse.Builder()")
//                            .imports("mockwebserver3.MockResponse")
//                            .javaParser(JavaParser.fromJavaVersion().classpath("mockwebserver3"))
//                            .build()
//                            .apply(getCursor(), nc.getCoordinates().replace());
//                }
//                return nc;
//            }

//            @Override
//            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation methodInv, ExecutionContext ctx) {
//                J.MethodInvocation mi = super.visitMethodInvocation(methodInv, ctx);
//                if (OLD_MOCKRESPONSE_STATUS_MATCHER.matches(mi)) {
//                    return JavaTemplate
//                            .builder("setStatus(#{any(java.lang.String)})")
//                            .contextSensitive()
//                            .build()
//                            .apply(getCursor(), mi.getCoordinates().replaceMethod(), mi.getArguments().get(0));
//                }
//                return mi;
//            }

            //            @Override
//            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
//
//                J.Block b = super.visitBlock(block, ctx);
//                return b;
//            }
        });
    }
}
