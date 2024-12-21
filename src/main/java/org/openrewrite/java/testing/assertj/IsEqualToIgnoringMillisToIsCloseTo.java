/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.testing.assertj;

import java.util.Date;

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import com.google.errorprone.refaster.annotation.UseImportPolicy;
import org.openrewrite.java.template.RecipeDescriptor;

import static com.google.errorprone.refaster.ImportPolicy.STATIC_IMPORT_ALWAYS;
import static org.assertj.core.api.Assertions.assertThat;

@RecipeDescriptor(
        name = "Replace `AbstractDateAssert#isEqualToIgnoringMillis(java.util.Date)` by `by isCloseTo(Date, long)`",
        description = "`isEqualToIgnoringMillis()` is deprecated in favor of `isCloseTo()`."
)
@SuppressWarnings({"java:S1874", "deprecation"})
public class IsEqualToIgnoringMillisToIsCloseTo {

    @BeforeTemplate
    void isEqualToIgnoringMillisBefore(Date date1, Date date2) {
        assertThat(date1).isEqualToIgnoringMillis(date2);
    }

    @AfterTemplate
    @UseImportPolicy(value = STATIC_IMPORT_ALWAYS)
    void isCloseToAfter(Date date1, Date date2) {
        assertThat(date1).isCloseTo(date2, 1000L);
    }
}
