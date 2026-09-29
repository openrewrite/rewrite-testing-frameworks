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
package org.openrewrite.java.testing.mockito;

import lombok.Getter;
import org.openrewrite.Recipe;
import org.openrewrite.java.ChangeMethodTargetToStatic;
import org.openrewrite.java.RemoveAnnotation;

import java.util.List;

import static java.util.Arrays.asList;

public class ReplacePowerMockitoUsages extends Recipe {

    private static final String POWER_MOCKITO = "org.powermock.api.mockito.PowerMockito ";
    private static final String ANNOTATIONS = "@org.powermock.core.classloader.annotations.";

    @Getter
    final String displayName = "Replace PowerMock usages with Mockito";

    @Getter
    final String description = "Replaces the PowerMock API, runners, rules and annotations used in Java sources with " +
            "their Mockito counterparts, and PowerMock `Whitebox` with Java reflection. Build files are left unchanged.";

    @Override
    public List<Recipe> getRecipeList() {
        return asList(
                new RemoveAnnotation(ANNOTATIONS + "PowerMockIgnore"),
                new RemoveAnnotation(ANNOTATIONS + "SuppressStaticInitializationFor"),
                new PowerMockRunnerDelegateToRunWith(),
                new PowerMockitoMockStaticToMockito(),
                new ChangeMethodTargetToStatic(POWER_MOCKITO + "mockStatic(..)", "org.mockito.Mockito", "org.mockito.MockedStatic", null),
                new PowerMockitoDoStubbingToMockito(),
                new ChangeMethodTargetToStatic(POWER_MOCKITO + "do*(..)", "org.mockito.Mockito", null, null),
                new ChangeMethodTargetToStatic(POWER_MOCKITO + "mock(..)", "org.mockito.Mockito", null, null),
                new ChangeMethodTargetToStatic(POWER_MOCKITO + "spy(..)", "org.mockito.Mockito", null, null),
                new ChangeMethodTargetToStatic(POWER_MOCKITO + "when(..)", "org.mockito.Mockito", null, null),
                new ChangeMethodTargetToStatic(POWER_MOCKITO + "verifyNoMoreInteractions(..)", "org.mockito.Mockito", null, null),
                new ChangeMethodTargetToStatic(POWER_MOCKITO + "verifyZeroInteractions(..)", "org.mockito.Mockito", null, null),
                new RemovePowerMockRule(),
                new RemovePowerMockClassExtensions(),
                // Removes what PowerMockitoMockStaticToMockito leaves, such as `@PrepareForTest(fullyQualifiedNames = ...)`
                new RemoveAnnotation(ANNOTATIONS + "PrepareForTest"),
                new RemoveAnnotation(ANNOTATIONS + "PrepareOnlyThisForTest"),
                new RemoveAnnotation(ANNOTATIONS + "PrepareEverythingForTest"),
                new PowerMockitoWhenNewToMockito(),
                new PowerMockWhiteboxSetInternalStateToJavaReflection(),
                new PowerMockWhiteboxGetInternalStateToJavaReflection(),
                new PowerMockWhiteboxInvokeMethodToJavaReflection(),
                new PowerMockWhiteboxGetFieldToJavaReflection(),
                new PowerMockWhiteboxGetMethodToJavaReflection(),
                new PowerMockWhiteboxInvokeConstructorToJavaReflection(),
                new CleanupPowerMockImports()
        );
    }
}
