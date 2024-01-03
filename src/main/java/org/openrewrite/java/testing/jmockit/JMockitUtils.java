package org.openrewrite.java.testing.jmockit;

import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

class JMockitUtils {
    static boolean isExpectationsNewClassStatement(Statement s) {
        if (!(s instanceof J.NewClass)) {
            return false;
        }
        J.NewClass nc = (J.NewClass) s;
        if (!(nc.getClazz() instanceof J.Identifier)) {
            return false;
        }
        J.Identifier clazz = (J.Identifier) nc.getClazz();
        if (!TypeUtils.isAssignableTo("mockit.Expectations", clazz.getType())) {
            return false;
        }
        // empty Expectations block is considered invalid
        assert nc.getBody() != null
                && !nc.getBody().getStatements().isEmpty() : "Expectations block is empty";
        // Expectations block should be composed of a block within another block
        assert nc.getBody().getStatements().size() == 1 : "Expectations block is malformed";

        return true;
    }

}
