/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.testing.assertj;

import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class CollapseConsecutiveAssertThatStatements extends Recipe {
    private static final String ASSERTJ_QUALIFIED_ASSERTIONS_CLASS_NAME = "org.assertj.core.api.Assertions";
    private static final MethodMatcher ASSERT_THAT = new MethodMatcher("org.assertj.core.api.Assertions assertThat(..)");

    @Override
    public String getDisplayName() {
        return "Collapse consecutive `assertThat` statements";
    }

    @Override
    public String getDescription() {
        return "Collapse consecutive `assertThat` statements into single assertThat chained statement. This recipe ignores assertThat statements that has method invocation as parameter.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(ASSERTJ_QUALIFIED_ASSERTIONS_CLASS_NAME, true), new CollapseConsecutiveAssertThatStatementsVisitor());
    }

    public static class CollapseConsecutiveAssertThatStatementsVisitor extends JavaIsoVisitor<ExecutionContext> {
        private JavaParser.Builder<?, ?> assertionsParser;

        private JavaParser.Builder<?, ?> assertionsParser(ExecutionContext ctx) {
            if (assertionsParser == null) {
                assertionsParser = JavaParser.fromJavaVersion()
                        .classpathFromResources(ctx, "assertj-core-3.24");
            }
            return assertionsParser;
        }

        @Override
        public J.Block visitBlock(J.Block _block, ExecutionContext ctx){
            J.Block block = super.visitBlock(_block,ctx);

            List<List<J.MethodInvocation>> consecutiveAssertThatStatementList = getConsecutiveAssertThatList(block.getStatements());

            List<Statement> modifiedStatements = new ArrayList<>();
            int currIndex = 0;
            while(currIndex<consecutiveAssertThatStatementList.size()){
                if(consecutiveAssertThatStatementList.get(currIndex).size()<=1){
                    modifiedStatements.add(block.getStatements().get(currIndex));
                    currIndex += 1;
                }else{
                    modifiedStatements.add(getCollapsedAssertThat(consecutiveAssertThatStatementList.get(currIndex)));
                    currIndex += consecutiveAssertThatStatementList.get(currIndex).size();
                }
            }

            block = block.withStatements(modifiedStatements);

            return maybeAutoFormat(_block, block.withStatements(modifiedStatements), ctx);
        }


        private List<List<J.MethodInvocation>> getConsecutiveAssertThatList(List<Statement> statements){
            List<List<J.MethodInvocation>> consecutiveAssertThatList = new ArrayList<>();
            String prevArg = "";
            int currListIndex=0;
            int statementListSize = statements.size();
            List<J.MethodInvocation> currList = new ArrayList<>();
            for (int currIndex=0; currIndex<statementListSize; currIndex++) {

                consecutiveAssertThatList.add(new ArrayList<>());

                Statement statement = statements.get(currIndex);
                if (statement instanceof J.MethodInvocation) {
                    Optional<J.MethodInvocation> assertThatMi = getAssertThatMi(statement);
                    if (assertThatMi.isPresent()) {
                        if (isAssertThatValid(statement)) {
                            if (!getFirstArgumentName(assertThatMi.get()).equals(prevArg)) {
                                currListIndex = currIndex;
                            }
                            consecutiveAssertThatList.get(currListIndex).add((J.MethodInvocation) statement);
                            prevArg = getFirstArgumentName(assertThatMi.get());
                            continue;
                        }
                    }
                }
                prevArg = "";
            }
            return consecutiveAssertThatList;
        }

        private Optional<J.MethodInvocation> getAssertThatMi(J subtree){
            AtomicReference<J.MethodInvocation> assertThatMi = new AtomicReference<>(null);
            new JavaIsoVisitor<AtomicReference<J.MethodInvocation>>(){

                @Override
                public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, AtomicReference<J.MethodInvocation> assertThatMiHolder){
                    if(ASSERT_THAT.matches(mi)){
                        assertThatMiHolder.set(mi);
                        return mi;
                    }
                    return super.visitMethodInvocation(mi,assertThatMi);
                }

            }.reduce(subtree,assertThatMi);
            return Optional.of(assertThatMi.get());
        }

        private boolean isAssertThatValid(J subtree){
            AtomicInteger chainCount = new AtomicInteger(0);
            AtomicBoolean isValid = new AtomicBoolean(true);
            new JavaIsoVisitor<AtomicInteger>(){
                @Override
                public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, AtomicInteger chainCount){
                    chainCount.set(chainCount.get()+1);

                    if(ASSERT_THAT.matches(mi)){
                        if(chainCount.get()>2)
                            isValid.set(false);

                        J assertThatArgument = mi.getArguments().get(0);
                        if( assertThatArgument instanceof J.MethodInvocation || assertThatArgument instanceof J.Lambda )
                            isValid.set(false);
                    }

                    return super.visitMethodInvocation(mi,chainCount);
                }
            }.reduce(subtree,chainCount);
            return isValid.get();
        }

        private String getFirstArgumentName(J.MethodInvocation mi){
            return ((J.Identifier) mi.getArguments().get(0)).getSimpleName();
        }

        private J.MethodInvocation getCollapsedAssertThat(List<J.MethodInvocation> consecutiveAssertThatStatement){
            J.MethodInvocation collapsedAssertThatMi = null;
            for(J.MethodInvocation mi : consecutiveAssertThatStatement){
                if(Objects.isNull(collapsedAssertThatMi)){
                    collapsedAssertThatMi = mi;
                    continue;
                }
                collapsedAssertThatMi = mi.withSelect(collapsedAssertThatMi.withPrefix(Space.EMPTY));
            }
            return collapsedAssertThatMi;
        }
    }

}
