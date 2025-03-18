package org.openrewrite.java.testing.mockito;

import org.openrewrite.*;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.openrewrite.Tree.randomId;

public class PowerMockitoWhenNewToMockito extends Recipe {

    private static final MethodMatcher PM_WHEN_NEW = new MethodMatcher("org.powermock.api.mockito.PowerMockito whenNew(..)");
    private static final MethodMatcher WITH_NO_ARGUMENTS = new MethodMatcher("*..* withNoArguments(..)");
    private static final MethodMatcher THEN_RETURN = new MethodMatcher("org.mockito.stubbing.OngoingStubbing thenReturn(..)");

    @Override
    public String getDisplayName() {
        return "Replace `PowerMockito.whenNew` with Mockito counterpart";
    }

    @Override
    public String getDescription() {
        return "Replaces `PowerMockito.whenNew` calls with respective `Mockito.whenConstructed` calls.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesMethod<>(PM_WHEN_NEW),
                new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                        if (THEN_RETURN.matches(method) && method.getSelect() instanceof J.MethodInvocation) {
                            J.MethodInvocation select1 = (J.MethodInvocation) method.getSelect();
                            if (WITH_NO_ARGUMENTS.matches(select1) && select1.getSelect() instanceof J.MethodInvocation) {
                                J.MethodInvocation select2 = (J.MethodInvocation) select1.getSelect();
                                if (PM_WHEN_NEW.matches(select2)) {
                                    maybeRemoveImport("org.powermock.api.mockito.PowerMockito");
                                    maybeAddImport("org.mockito.Mockito", "whenConstructed");

                                    Cursor c = getCursor();
                                    while (c != null && !(c.getValue() instanceof J.MethodDeclaration)) {
                                        c = c.getParent();
                                    }
                                    if (c != null) {
                                        c.putMessage("POWERMOCKITO_WHEN_NEW_REPLACED", select2.getArguments());
                                        c.putMessage("POWERMOCKITO_WHEN_NEW_CURSOR", getCursor());
                                        return null;
                                    }
                                }
                            }
                        }
                        return super.visitMethodInvocation(method, executionContext);
                    }

                    @Override
                    public J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
                        J ret = super.visitMethodDeclaration(method, executionContext);
                        List<Expression> mockArguments = getCursor().getMessage("POWERMOCKITO_WHEN_NEW_REPLACED");
                        if (mockArguments != null && ret instanceof J.MethodDeclaration) {
                            J.MethodDeclaration retM = (J.MethodDeclaration) ret;
                            J.Block originalBody = retM.getBody();

                            // onlyIfReferenced=false as `maybeAddImport` doesn't seem to find the type referred to in a try statement
                            maybeAddImport("org.mockito.MockedConstruction", false);

                            List<JRightPadded<J.Try.Resource>> resources = mockArguments.stream().map(arg -> {
                                // TODO "m" is not a good name
                                J.Identifier name = new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, Collections.emptyList(), "m", null, null);
                                JavaType typeMockito = JavaType.buildType("org.mockito.Mockito");
                                JRightPadded<Expression> mockitoSelect = JRightPadded.build(new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, Collections.emptyList(), "Mockito", typeMockito, null));
                                J.Identifier mockConstructionIdentifier = new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, Collections.emptyList(), "mockConstruction", null, null);
                                Expression expr = new J.MethodInvocation(randomId(), Space.EMPTY, Markers.EMPTY, mockitoSelect, null, mockConstructionIdentifier, JContainer.build(Collections.singletonList(JRightPadded.build(arg))), null);
                                J.VariableDeclarations.NamedVariable varr = new J.VariableDeclarations.NamedVariable(randomId(), Space.EMPTY, Markers.EMPTY, name, Collections.emptyList(), JLeftPadded.build(expr), null);
                                JContainer<Expression> lhsTypeParameter = JContainer.build(Collections.singletonList(JRightPadded.build(new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, Collections.emptyList(), "Generator", null, null))));
                                NameTree todoIdentifier = new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, Collections.emptyList(), "MockedConstruction", null, null);
                                TypeTree typeexpr = new J.ParameterizedType(randomId(), Space.EMPTY, Markers.EMPTY, todoIdentifier, lhsTypeParameter, null);
                                TypedTree vd = new J.VariableDeclarations(randomId(), Space.EMPTY, Markers.EMPTY, Collections.emptyList(), Collections.emptyList(), typeexpr, null, Collections.emptyList(), Collections.singletonList(JRightPadded.build(varr)));
                                J.Try.Resource r = new J.Try.Resource(randomId(), Space.EMPTY, Markers.EMPTY, vd, false);
                                return JRightPadded.build(r);
                            }).collect(Collectors.toList());
                            J.Try tryy = new J.Try(randomId(), Space.EMPTY, Markers.EMPTY, JContainer.build(resources), originalBody, Collections.emptyList(), null);
                            return autoFormat(retM.withBody(originalBody.withStatements(Collections.singletonList(tryy))), executionContext);
                        }
                        return ret;
                    }
                });
    }
}
