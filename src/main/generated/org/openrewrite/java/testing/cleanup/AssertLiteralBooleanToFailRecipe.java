package org.openrewrite.java.testing.cleanup;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.NonNullApi;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.*;
import org.openrewrite.java.template.Primitive;
import org.openrewrite.java.template.function.*;
import org.openrewrite.java.template.internal.AbstractRefasterJavaVisitor;
import org.openrewrite.java.tree.*;

import javax.annotation.Generated;
import java.util.*;

import static org.openrewrite.java.template.internal.AbstractRefasterJavaVisitor.EmbeddingOption.*;

/**
 * OpenRewrite recipe created for Refaster template {@code AssertLiteralBooleanToFail}.
 */
@SuppressWarnings("all")
@NonNullApi
@Generated("org.openrewrite.java.template.processor.RefasterTemplateProcessor")
public class AssertLiteralBooleanToFailRecipe extends Recipe {

    /**
     * Instantiates a new instance.
     */
    public AssertLiteralBooleanToFailRecipe() {}

    @Override
    public String getDisplayName() {
        return "Replace JUnit `assertTrue(false, \"reason\")` and `assertFalse(true, \"reason\")` with `fail(\"reason\")`";
    }

    @Override
    public String getDescription() {
        return "Using fail is more direct and clear.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        JavaVisitor<ExecutionContext> javaVisitor = new AbstractRefasterJavaVisitor() {
            final JavaTemplate assertFalseBefore = JavaTemplate
                    .builder("org.junit.jupiter.api.Assertions.assertFalse(true, #{message:any(java.lang.String)});")
                    .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                    .build();
            final JavaTemplate assertTrueBefore = JavaTemplate
                    .builder("org.junit.jupiter.api.Assertions.assertTrue(false, #{message:any(java.lang.String)});")
                    .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                    .build();
            final JavaTemplate after = JavaTemplate
                    .builder("org.junit.jupiter.api.Assertions.fail(#{message:any(java.lang.String)});")
                    .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                    .build();

            @Override
            public J visitMethodInvocation(J.MethodInvocation elem, ExecutionContext ctx) {
                JavaTemplate.Matcher matcher;
                if ((matcher = assertFalseBefore.matcher(getCursor())).find()) {
                    maybeRemoveImport("org.junit.jupiter.api.Assertions.assertFalse");
                    return embed(
                            after.apply(getCursor(), elem.getCoordinates().replace(), matcher.parameter(0)),
                            getCursor(),
                            ctx,
                            SHORTEN_NAMES
                    );
                }
                if ((matcher = assertTrueBefore.matcher(getCursor())).find()) {
                    maybeRemoveImport("org.junit.jupiter.api.Assertions.assertTrue");
                    return embed(
                            after.apply(getCursor(), elem.getCoordinates().replace(), matcher.parameter(0)),
                            getCursor(),
                            ctx,
                            SHORTEN_NAMES
                    );
                }
                return super.visitMethodInvocation(elem, ctx);
            }

        };
        return Preconditions.check(
                Preconditions.or(
                    new UsesMethod<>("org.junit.jupiter.api.Assertions assertFalse(..)"),
                    new UsesMethod<>("org.junit.jupiter.api.Assertions assertTrue(..)")
                ),
                javaVisitor
        );
    }
}

