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
package org.openrewrite.java.testing.cleanup;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.*;

class RemoveVisibleForTestingAnnotationWhenUsedInProductionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new RemoveVisibleForTestingAnnotationWhenUsedInProduction());
    }

    @DocumentExample
    @Test
    void document() {
        //language=java
        rewriteRun(
          srcMainJava(
            java(
              """
                package com.example.domain;

                import org.jetbrains.annotations.VisibleForTesting;

                public class Production {

                    @VisibleForTesting
                    public String internalState;
                    @VisibleForTesting
                    public String externalState;

                    @VisibleForTesting
                    public String getInternalState() {
                        return internalState;
                    }

                    @VisibleForTesting
                    public String getExternalState() {
                         return externalState;
                    }
                }
                """,
              """
                package com.example.domain;

                import org.jetbrains.annotations.VisibleForTesting;

                public class Production {

                    @VisibleForTesting
                    public String internalState;
                    public String externalState;

                    @VisibleForTesting
                    public String getInternalState() {
                        return internalState;
                    }

                    public String getExternalState() {
                         return externalState;
                    }
                }
                """
            ),
            java(
              """
                package com.example.caller;

                import com.example.domain.Production;

                class ProductionCaller {
                    void call(Production production) {
                        String variableOne = production.externalState;
                        String variableTwo = production.getExternalState();
                    }
                }
                """
            )
          ),
          srcTestJava(
            java(
              """
                package com.example.test;

                import com.example.domain.Production;

                class ProductionTest {
                    void test(Production production) {
                        String variableOne = production.externalState;
                        String variableTwo = production.getExternalState();
                        String variableThree = production.internalState;
                        String variableFour = production.getInternalState();
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void constructor() {
        //language=java
        rewriteRun(
          srcMainJava(
            java(
              """
                package com.example.domain;

                import org.jetbrains.annotations.VisibleForTesting;

                public class Production {
                    @VisibleForTesting
                    public Production(){}
                    @VisibleForTesting
                    public Production(int debugLevel) {}
                }
                """,
              """
                package com.example.domain;

                import org.jetbrains.annotations.VisibleForTesting;

                public class Production {
                    public Production(){}
                    @VisibleForTesting
                    public Production(int debugLevel) {}
                }
                """
            ),
            java(
              """
                package com.example.caller;

                import com.example.domain.Production;

                class ProductionCaller {
                    void call() {
                        new Production();
                    }
                }
                """
            )
          ),
          srcTestJava(
            java(
              """
                package com.example.test;

                import com.example.domain.Production;

                class ProductionTest {
                    void test(Production production) {
                        new Production();
                        new Production(1);
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void fieldAccess() {
        //language=java
        rewriteRun(
          srcMainJava(
            java(
              """
                package com.example.domain;

                import org.jetbrains.annotations.VisibleForTesting;

                public class Production {
                    @VisibleForTesting
                    public String internalState;
                    @VisibleForTesting
                    public String externalState;
                }
                """,
              """
                package com.example.domain;

                import org.jetbrains.annotations.VisibleForTesting;

                public class Production {
                    @VisibleForTesting
                    public String internalState;
                    public String externalState;
                }
                """
            ),
            java(
              """
                package com.example.caller;

                import com.example.domain.Production;

                class ProductionCaller {
                    void call(Production production) {
                        String variableOne = production.externalState;
                    }
                }
                """
            )
          ),
          srcTestJava(
            java(
              """
                package com.example.test;

                import com.example.domain.Production;

                class ProductionTest {
                    void test(Production production) {
                        String variableOne = production.externalState;
                        String variableTwo = production.internalState;
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void fieldsAccess() {
        //language=java
        rewriteRun(
          srcMainJava(
            java(
              """
                package com.example.domain;

                import org.jetbrains.annotations.VisibleForTesting;

                public class Production {
                    @VisibleForTesting
                    public String externalState1, externalState2;
                    @VisibleForTesting
                    public String internalState1, externalState3;
                    @VisibleForTesting
                    public String internalState2, internalState3;
                }
                """,
              """
                package com.example.domain;

                import org.jetbrains.annotations.VisibleForTesting;

                public class Production {
                    public String externalState1, externalState2;
                    public String internalState1, externalState3;
                    @VisibleForTesting
                    public String internalState2, internalState3;
                }
                """
            ),
            java(
              """
                package com.example.caller;

                import com.example.domain.Production;

                class ProductionCaller {
                    void call(Production production) {
                        String variableOne = production.externalState1;
                        String variableTwo = production.externalState2;
                        String variableThree = production.externalState3;
                    }
                }
                """
            )
          ),
          srcTestJava(
            java(
              """
                package com.example.test;

                import com.example.domain.Production;

                class ProductionTest {
                    void test(Production production) {
                        String variableOne = production.externalState1;
                        String variableTwo = production.externalState2;
                        String variableThree = production.externalState3;
                        String variableFour = production.internalState1;
                        String variableFive = production.internalState2;
                        String variableSix = production.internalState3;
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void referenceConstant() {
        //language=java
        rewriteRun(
          srcMainJava(
            java(
              """
                package com.example.domain;

                import org.jetbrains.annotations.VisibleForTesting;

                public class Production {
                    @VisibleForTesting
                    public static final long INTERNAL_CONSTANT = 1L;
                    @VisibleForTesting
                    public static final long EXTERNAL_CONSTANT = 2L;
                }
                """,
              """
                package com.example.domain;

                import org.jetbrains.annotations.VisibleForTesting;

                public class Production {
                    @VisibleForTesting
                    public static final long INTERNAL_CONSTANT = 1L;
                    public static final long EXTERNAL_CONSTANT = 2L;
                }
                """
            ),
            java(
              """
                package com.example.caller;

                import com.example.domain.Production;

                class ProductionCaller {
                    void call(Production production) {
                        long variableOne = Production.EXTERNAL_CONSTANT;
                    }
                }
                """
            )
          ),
          srcTestJava(
            java(
              """
                package com.example.test;

                import com.example.domain.Production;

                class ProductionTest {
                    void test(Production production) {
                         long variableOne = Production.INTERNAL_CONSTANT;
                         long variableTwo = Production.EXTERNAL_CONSTANT;
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void methodInvocation() {
        //language=java
        rewriteRun(
          srcMainJava(
            java(
              """
                package com.example.domain;

                import org.jetbrains.annotations.VisibleForTesting;

                public class Production {
                    @VisibleForTesting
                    public void internalCall(){}
                    @VisibleForTesting
                    public void externalCall(){}
                }
                """,
              """
                package com.example.domain;

                import org.jetbrains.annotations.VisibleForTesting;

                public class Production {
                    @VisibleForTesting
                    public void internalCall(){}
                    public void externalCall(){}
                }
                """
            ),
            java(
              """
                package com.example.caller;

                import com.example.domain.Production;

                class ProductionCaller {
                    void call(Production production) {
                        production.externalCall();
                    }
                }
                """
            )
          ),
          srcTestJava(
            java(
              """
                package com.example.test;

                import com.example.domain.Production;

                class ProductionTest {
                    void test(Production production) {
                        production.internalCall();
                        production.externalCall();
                    }
                }
                """
            )
          )
        );
    }


    void genericMethodInvocation() {
        //language=java
        rewriteRun(
          srcMainJava(
            java(
              """
                package com.example.domain;

                import org.jetbrains.annotations.VisibleForTesting;

                public class Production {
                    @VisibleForTesting
                    public <T> void internalCall(T param){}
                    @VisibleForTesting
                    public <T> void externalCall(T param){}
                }
                """,
              """
                package com.example.domain;

                import org.jetbrains.annotations.VisibleForTesting;

                public class Production {
                    @VisibleForTesting
                    public <T> void internalCall(T param){}
                    public <T> void externalCall(T param){}
                }
                """
            ),
            java(
              """
                package com.example.caller;

                import com.example.domain.Production;

                class ProductionCaller {
                    void call(Production production, Object arg) {
                        production.externalCall(arg);
                    }
                }
                """
            )
          ),
          srcTestJava(
            java(
              """
                package com.example.test;

                import com.example.domain.Production;

                class ProductionTest {
                    void test(Production production, Object arg) {
                        production.internalCall(arg);
                        production.externalCall(arg);
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void methodReference() {
        //language=java
        rewriteRun(
          srcMainJava(
            java(
              """
                package com.example.domain;

                import org.jetbrains.annotations.VisibleForTesting;

                public class Production {
                    @VisibleForTesting
                    public String internalMethodReferenceWithInvocation(){}
                    @VisibleForTesting
                    public String internalMethodReferenceWithoutInvocation(){}
                    @VisibleForTesting
                    public String externalMethodReferenceWithInvocation(){}
                    @VisibleForTesting
                    public String externalMethodReferenceWithoutInvocation(){}
                }
                """,
              """
                package com.example.domain;

                import org.jetbrains.annotations.VisibleForTesting;

                public class Production {
                    @VisibleForTesting
                    public String internalMethodReferenceWithInvocation(){}
                    @VisibleForTesting
                    public String internalMethodReferenceWithoutInvocation(){}
                    public String externalMethodReferenceWithInvocation(){}
                    public String externalMethodReferenceWithoutInvocation(){}
                }
                """
            ),
            java(
              """
                package com.example.caller;

                import com.example.domain.Production;
                import java.util.function.Supplier;

                class ProductionCaller {
                    void call(Production production) {
                        Supplier<String> supplierOne = production::externalMethodReferenceWithInvocation;
                        Supplier<String> supplierTwo = production::externalMethodReferenceWithoutInvocation;
                        String supplierOneResult = supplierOne.get();
                    }
                }
                """
            )
          ),
          srcTestJava(
            java(
              """
                package com.example.test;

                import com.example.domain.Production;
                import java.util.function.Supplier;

                class ProductionTest {
                    void test(Production production) {
                        Supplier<String> supplierOne = production::externalMethodReferenceWithInvocation;
                        Supplier<String> supplierTwo = production::externalMethodReferenceWithoutInvocation;
                        String supplierOneResult = supplierOne.get();
                        Supplier<String> supplierThree = production::internalMethodReferenceWithInvocation;
                        Supplier<String> supplierFour = production::internalMethodReferenceWithoutInvocation;
                        String supplierThreeResult = supplierThree.get();
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void referenceStaticInnerClass() {
        //language=java
        rewriteRun(
          srcMainJava(
            java(
              """
                package com.example.domain;

                import org.jetbrains.annotations.VisibleForTesting;

                public class Production {
                    @VisibleForTesting
                    public static class InternalInner {
                        @VisibleForTesting
                        public static String internalState;
                    }

                    @VisibleForTesting
                    public static class ExternalInner {
                        @VisibleForTesting
                        public static String externalState;
                    }
                }
                """,
              """
                package com.example.domain;

                import org.jetbrains.annotations.VisibleForTesting;

                public class Production {
                    @VisibleForTesting
                    public static class InternalInner {
                        @VisibleForTesting
                        public static String internalState;
                    }

                    public static class ExternalInner {
                        public static String externalState;
                    }
                }
                """
            ),
            java(
              """
                package com.example.caller;

                import com.example.domain.Production;

                class ProductionCaller {
                    void call(Production production) {
                        String variableOne = Production.ExternalInner.externalState;
                    }
                }
                """
            )
          ),
          srcTestJava(
            java(
              """
                package com.example.test;

                import com.example.domain.Production;

                class ProductionTest {
                    void test(Production production) {
                        String variableOne = Production.ExternalInner.externalState;
                        String variableTwo = Production.InternalInner.internalState;
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void instantiateStaticInnerClass() {
        //language=java
        rewriteRun(
          srcMainJava(
            java(
              """
                package com.example.domain;

                import org.jetbrains.annotations.VisibleForTesting;

                public class Production {
                    @VisibleForTesting
                    public static class InternalInner {}

                    @VisibleForTesting
                    public static class ExternalInner {}
                }
                """,
              """
                package com.example.domain;

                import org.jetbrains.annotations.VisibleForTesting;

                public class Production {
                    @VisibleForTesting
                    public static class InternalInner {}

                    public static class ExternalInner {}
                }
                """
            ),
            java(
              """
                package com.example.caller;

                import com.example.domain.Production;

                class ProductionCaller {
                    void call(Production production) {
                        new Production.ExternalInner();
                    }
                }
                """
            )
          ),
          srcTestJava(
            java(
              """
                package com.example.test;

                import com.example.domain.Production;

                class ProductionTest {
                    void test(Production production) {
                        new Production.InternalInner();
                        new Production.ExternalInner();
                    }
                }
                """
            )
          )
        );
    }
}
