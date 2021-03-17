/*
 * Copyright 2020 the original author or authors.
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
package org.openrewrite.java.testing.junit5;

import lombok.SneakyThrows;
import org.openrewrite.*;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.FindFields;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.text.RuleBasedCollator;
import java.util.*;

/**
 * This is not functional yet. Make modifications wherever you see fit. TODO.
 * <p>
 * Translates JUnit 4's org.junit.rules.TestWatcher into JUnit 5's org.junit.jupiter.api.extension.TestWatcher.
 * <p>
 * In JUnit 4, TestWatcher is an abstract class which is instantiated and its API methods overridden by the user.
 * For JUnit 5, TestWatcher has been moved to an interface designed to be implemented by an Extension classes and registered with the Test class.
 * <p>
 * Therefore, to migrate from JUnit 4 to JUnit 5, practically-speaking, this recipe follows these steps:
 * <ul>
 * <li>We replace direct instantiations of TestWatcher with a new class we generate which implements TestWatcher.</li>
 * <li>We map JUnit 4's org.junit.rules.TestWatcher API signature method bodies to JUnit 5's API rough equivalents.</li>
 * <li>We then register our newly-created TestExtension with the Test class using the ExtendWith annotation.</li>
 * </ul>
 * <p>
 * Direct examples can be seen in the unit tests.
 * Details on mapping the TestWatcher API signatures are provided further below.
 * <p>
 * JUnit 4's TestWatcher has the following API signatures which we account for (at the time of writing):
 * <PRE>
 * protected void failed(Throwable e, Description description)
 * protected void finished(Description description)
 * protected void skipped(AssumptionViolatedException e, Description description)
 * protected void starting(Description description)
 * protected void succeeded(Description description)
 * </PRE>
 * <p>
 * Whereas JUnit 5's TestWatcher interface has the following:
 * <PRE>
 * default void testDisabled(ExtensionContext context, Optional<String> reason)
 * default void testSuccessful(ExtensionContext context)
 * default void testAborted(ExtensionContext context, Throwable cause)
 * default void testFailed(ExtensionContext context, Throwable cause)
 * </PRE>
 * <p>
 * Notice how the JUnit 5 TestWatcher interface does not have the same set of "lifecycle hooks" which the JUnit 4 TestWatcher handled.
 * Therefore, we cannot do an exact one-to-one mapping.
 * <p>
 * Here are the JUnit 4 TestWatcher API methods mapped to their JUnit 5 interface API equivalents:
 * <PRE>
 * protected void failed(Throwable e, Description description) == default void testFailed(ExtensionContext context, Throwable cause)
 * protected void finished(Description description) == NO_DIRECT_MAPPING_IN_TESTWATCHER
 * protected void skipped(AssumptionViolatedException e, Description description) == default void testDisabled(ExtensionContext context, Optional<String> reason)
 * protected void starting(Description description) == NO_DIRECT_MAPPING_IN_TESTWATCHER
 * protected void succeeded(Description description) == default void testSuccessful(ExtensionContext context)
 * </PRE>
 * This recipe migrates the JUnit 4 TestWatcher signatures to JUnit 5 on a best-match basis.
 * If the JUnit 4 TestWatcher API methods which can be mapped to a JUnit 5 equivalent are used, we update them.
 * However, if a JUnit 4 API method is being used which does not have a direct JUnit 5 equivalent, we conditionally add another "implements"
 * for the corresponding JUnit 5 lifecycle hook to be used in the generated "Extension".
 * <p>
 * Instead, for JUnit 4 TestWatcher API methods which do not have JUnit 5 TestWatcher equivalents, we implement other lifecycle callbacks
 * from org.junit.jupiter.api.extension.BeforeTestExecutionCallback and org.junit.jupiter.api.extension.AfterTestExecutionCallback:
 * <PRE>
 * protected void starting(Description description) == public void beforeTestExecution(ExtensionContext context) in org.junit.jupiter.api.extension.BeforeTestExecutionCallback
 * protected void finished(Description description) == public void afterTestExecution(ExtensionContext context) in org.junit.jupiter.api.extension.AfterTestExecutionCallback
 * </PRE>
 */
@Incubating(since = "1.0.2")
public class UpdateTestWatcher extends Recipe {

    private static final String JUNIT4_TEST_WATCHER_FQN = "org.junit.rules.TestWatcher";
    private static final String UPDATE_TEST_WATCHER_EXTEND_WITH_MARKER = "update_test_watcher_junit_4_to_5";

    private static String jUnitExtensionClassNameFromFieldName(String fieldName) {
        return StringUtils.capitalize(fieldName) + "TestExtension";
    }

    @Override
    public String getDisplayName() {
        return "Update TestWatcher";
    }

    @Override
    public String getDescription() {
        return "update JUnit 4's TestWatcher to the JUnit 5 equivalent.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new UpdateTestWatcherVisitor();
    }

    private class UpdateTestWatcherVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final JavaTemplate.Builder addGeneratedTestExtension = template("@ExtendWith(#{}.class)")
                .javaParser(JavaParser.fromJavaVersion()
                        .dependsOn(Arrays.asList(
                                Parser.Input.fromString("package org.junit.jupiter.api.extension.ExtendWith;\n" +
                                        "public @interface ExtendWith {\n" +
                                        "  Class[] value();\n" +
                                        "}")
                        ))
                        .build())
                .imports("org.junit.jupiter.api.extension.ExtendWith");


        @SneakyThrows
        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
            Set<J.VariableDeclarations> testWatcherFields = FindFields.find(cd, JUNIT4_TEST_WATCHER_FQN);
            if (testWatcherFields.size() > 0) {
                testWatcherFields.stream().forEach(field -> {

                    String fieldName = field.getVariables().get(0).getSimpleName();
                    String asJUnitExtensionClassName = jUnitExtensionClassNameFromFieldName(fieldName);
                    // mark the class as the addition target; do more with this, have more things trigger off this todo
                    getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, UPDATE_TEST_WATCHER_EXTEND_WITH_MARKER, asJUnitExtensionClassName);

                    // /////////
                    // this should really be a hash-lookup for find operations
                    JavaIsoVisitor<Set<J.Block>> viz = new JavaIsoVisitor<Set<J.Block>>() {
                        @Override
                        public J.NewClass visitNewClass(J.NewClass newClass, Set<J.Block> ctx) {
                            J.NewClass nc = super.visitNewClass(newClass, ctx);
                            assert nc.getBody() != null;
                            ctx.add(nc.getBody());
                            return nc;
                        }
                    };
                    Set<J.Block> blockToSet = new HashSet<>();
                    viz.visit(field, blockToSet);
                    // wouldn't work for multiple test watchers, just for trying
                    doAfterVisit(new GenerateJUnitExtensionFromTestWatcher(asJUnitExtensionClassName, blockToSet.stream().findFirst().get()));
                    // /////////

                });

                // Remove the TestWatcher Rule fields
                List<Statement> statements = new ArrayList<>(cd.getBody().getStatements());
                statements.removeAll(testWatcherFields);
                cd = cd.withBody(cd.getBody().withStatements(statements));

                // If applicable, we maybe remove any associated imports
                if (cd.getBody() != classDecl.getBody()) {
                    maybeRemoveImport(JUNIT4_TEST_WATCHER_FQN);
                }

                // now we have to plop on an extend with and such
                String asJUnitExtensionClassName = getCursor().getMessage(UPDATE_TEST_WATCHER_EXTEND_WITH_MARKER);
                if (asJUnitExtensionClassName != null) {
                    cd = cd.withTemplate(addGeneratedTestExtension.build(),
                            cd.getCoordinates().addAnnotation(Comparator.comparing(
                                    J.Annotation::getSimpleName,
                                    new RuleBasedCollator("< ExtendWith")
                            )),
                            asJUnitExtensionClassName
                    );
                    maybeAddImport("org.junit.jupiter.api.extension.ExtendWith");
                }
            }

            return cd;
        }

    }

    private class GenerateJUnitExtensionFromTestWatcher extends JavaIsoVisitor<ExecutionContext> {
        private final String jUnitExtensionClassName;
        private final J.Block jUnitExtensionClassBody;

        /**
         * @param jUnitExtensionClassName should already be formatted as the class name, e.g. capitalized and have TestExtension appended.
         */
        private GenerateJUnitExtensionFromTestWatcher(String jUnitExtensionClassName, J.Block jUnitExtensionClassBody) {
            this.jUnitExtensionClassName = jUnitExtensionClassName;
            this.jUnitExtensionClassBody = jUnitExtensionClassBody;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

            cd = cd.withTemplate(
                    template("class #{} #{}").build(),
                    cd.getBody().getCoordinates().lastStatement(),
                    jUnitExtensionClassName, jUnitExtensionClassBody
            );

            // // ... todo
            // // could look through the initializer, grab any methods, check for method matchers, then
            // // conditionally implement using ImplementInterface
            // maybeAddImport("org.junit.jupiter.api.extension.ExtensionContext");
            // maybeAddImport("org.junit.jupiter.api.extension.BeforeTestExecutionCallback");
            // maybeAddImport("org.junit.jupiter.api.extension.AfterTestExecutionCallback");
            // maybeAddImport("org.junit.jupiter.api.extension.TestWatcher");


            return cd;
        }
    }


    // private class MethodSwap extends JavaIsoVisitor<ExecutionContext> {
    //     private final MethodMatcher finished = new MethodMatcher("org.junit.rules.TestWatcher finished(Description description)");
    //
    //     @Override
    //     public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
    //         // doAfterVisit(new ChangeType("org.junit.Before", "org.junit.jupiter.api.BeforeEach"));
    //         return super.visitCompilationUnit(cu, ctx);
    //     }
    //
    // }

}

