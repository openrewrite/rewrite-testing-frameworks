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
                """)
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
                """)
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
                """)
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
                """)
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
                """)
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
                """)
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
                """)
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
                """)
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
}
