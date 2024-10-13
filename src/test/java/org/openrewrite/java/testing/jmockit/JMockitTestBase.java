package org.openrewrite.java.testing.jmockit;

import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.testing.jmockit.JMockitTestUtils.setDefaultParserSettings;

public class JMockitTestBase implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        setDefaultParserSettings(spec);
    }
}
