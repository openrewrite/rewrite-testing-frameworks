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

import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;

import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.java.testing.mockito.MockitoUtils.maybeAddMethodWithAnnotation;
import static org.openrewrite.java.testing.mockito.MockitoUtils.separateAddedMembers;

/// Mockito's `MockedStatic` and `MockedConstruction` stay active on the current thread until closed, whereas
/// PowerMock resets its mocks after every test, including those created in static setup methods. Migrated mocks are
/// therefore assigned to a field at the very spot PowerMock created them, and that field is closed after each test,
/// which keeps the mock active for exactly the same part of the test as before.
final class ScopedMocks {
    static final String MOCKED_STATIC = "org.mockito.MockedStatic";
    static final String MOCKED_CONSTRUCTION = "org.mockito.MockedConstruction";

    private static final AnnotationMatcher TESTNG_TEST = new AnnotationMatcher("@org.testng.annotations.Test");

    static final class ScopedMock {
        final String scopedMockType;
        final JavaType.FullyQualified mockedType;
        final String className;
        String fieldName;
        J.@Nullable Identifier field;
        boolean declare = true;
        boolean isStatic;
        boolean alwaysAssigned = true;
        boolean assignedInSetUp;
        boolean assigned;

        ScopedMock(String scopedMockType, JavaType.FullyQualified mockedType, String className, String fieldName) {
            this.scopedMockType = scopedMockType;
            this.mockedType = mockedType;
            this.className = className;
            this.fieldName = fieldName;
        }

        J.Identifier field() {
            return requireNonNull(field).withId(randomId()).withPrefix(Space.EMPTY);
        }
    }

    private final Map<String, ScopedMock> mocks = new LinkedHashMap<>();
    private final Set<String> fieldNames = new HashSet<>();
    private final Map<J.MethodDeclaration, Map<ScopedMock, Boolean>> openedUnconditionallyPerMethod = new IdentityHashMap<>();
    private final Map<String, Boolean> reassignmentMayFindNull = new HashMap<>();
    private final TestFramework framework;
    private final AnnotationMatcher setUpMatcher;
    private final J.ClassDeclaration classDecl;
    private @Nullable String testGroups;

    ScopedMocks(J.ClassDeclaration classDecl, TestFramework framework) {
        this.classDecl = classDecl;
        this.framework = framework;
        this.setUpMatcher = new AnnotationMatcher(framework.setUpAnnotationSignature);
        new JavaIsoVisitor<Set<String>>() {
            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, Set<String> names) {
                names.add(variable.getSimpleName());
                return super.visitVariable(variable, names);
            }
        }.visit(classDecl, fieldNames);
        for (Statement statement : classDecl.getBody().getStatements()) {
            if (statement instanceof J.VariableDeclarations) {
                for (J.VariableDeclarations.NamedVariable variable : ((J.VariableDeclarations) statement).getVariables()) {
                    JavaType.Parameterized type = TypeUtils.asParameterized(variable.getType());
                    if (type == null || type.getTypeParameters().size() != 1) {
                        continue;
                    }
                    JavaType.FullyQualified mocked = TypeUtils.asFullyQualified(type.getTypeParameters().get(0));
                    for (String scopedMockType : new String[]{MOCKED_STATIC, MOCKED_CONSTRUCTION}) {
                        if (mocked != null && TypeUtils.isOfClassType(type, scopedMockType)) {
                            ScopedMock mock = new ScopedMock(scopedMockType, mocked, mocked.getClassName(), variable.getSimpleName());
                            mock.declare = false;
                            mock.alwaysAssigned = false;
                            mock.field = variable.getName();
                            mocks.put(key(scopedMockType, mocked), mock);
                        }
                    }
                }
            }
        }
    }

    boolean isEmpty() {
        return mocks.isEmpty();
    }

    @Nullable
    ScopedMock get(String scopedMockType, @Nullable JavaType mockedType) {
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(mockedType);
        return fq == null ? null : mocks.get(key(scopedMockType, fq));
    }

    /// Registers a mock created at the statement the cursor points to, reusing an existing field where possible.
    ScopedMock register(String scopedMockType, JavaType.FullyQualified mockedType, String fieldPrefix, Cursor site) {
        J.MethodDeclaration method = site.firstEnclosing(J.MethodDeclaration.class);
        boolean isStatic = isStaticContext(site);
        boolean inSetUp = method != null && method.getLeadingAnnotations().stream().anyMatch(setUpMatcher::matches);

        ScopedMock mock = mocks.get(key(scopedMockType, mockedType));
        if (mock == null) {
            String className = relativeClassName(mockedType);
            String fieldName = fieldPrefix + className.replace(".", "_");
            while (fieldNames.contains(fieldName)) {
                fieldName += "_";
            }
            fieldNames.add(fieldName);
            mock = new ScopedMock(scopedMockType, mockedType, className, fieldName);
            mock.isStatic = isStatic;
            mock.alwaysAssigned = inSetUp;
            mock.assignedInSetUp = inSetUp;
            mocks.put(key(scopedMockType, mockedType), mock);
        } else {
            mock.isStatic |= isStatic;
            mock.alwaysAssigned &= inSetUp;
            mock.assignedInSetUp |= inSetUp;
        }
        mock.assigned = true;
        if (method != null) {
            Map<ScopedMock, Boolean> opened = openedUnconditionallyPerMethod.computeIfAbsent(method, m -> new HashMap<>());
            Boolean openedUnconditionally = opened.get(mock);
            if (openedUnconditionally != null || isInLoop(site)) {
                reassignmentMayFindNull.put(siteKey(site.getValue(), mock), !Boolean.TRUE.equals(openedUnconditionally));
            }
            opened.merge(mock, site.getParentTreeCursor().getValue() == method.getBody(), Boolean::logicalOr);
        }
        if (testGroups == null && method != null && framework == TestFramework.TESTNG) {
            testGroups = testNgGroups(method);
        }
        return mock;
    }

    void requireStatic(String scopedMockType, JavaType.FullyQualified mockedType) {
        ScopedMock mock = mocks.get(key(scopedMockType, mockedType));
        if (mock != null) {
            mock.isStatic = true;
        }
    }

    static boolean isStaticContext(Cursor site) {
        J.MethodDeclaration method = site.firstEnclosing(J.MethodDeclaration.class);
        if (method != null) {
            return method.hasModifier(J.Modifier.Type.Static);
        }
        J.Block initializer = site.firstEnclosing(J.Block.class);
        return initializer != null && initializer.isStatic();
    }

    private static boolean isInLoop(Cursor site) {
        for (Cursor c = site.getParent(); c != null && !(c.getValue() instanceof J.MethodDeclaration); c = c.getParent()) {
            Object value = c.getValue();
            if (value instanceof J.ForLoop || value instanceof J.ForEachLoop ||
                value instanceof J.WhileLoop || value instanceof J.DoWhileLoop) {
                return true;
            }
        }
        return false;
    }

    /// The class name as referenced from within the test class, which can omit the test class itself as the
    /// outer class of a nested type.
    private String relativeClassName(JavaType.FullyQualified type) {
        String className = type.getClassName();
        String outer = classDecl.getType() == null ? null : classDecl.getType().getClassName() + ".";
        return outer != null && className.startsWith(outer) ? className.substring(outer.length()) : className;
    }

    private static @Nullable String testNgGroups(J.MethodDeclaration method) {
        for (J.Annotation annotation : method.getLeadingAnnotations()) {
            if (TESTNG_TEST.matches(annotation) && annotation.getArguments() != null) {
                for (Expression argument : annotation.getArguments()) {
                    if (argument instanceof J.Assignment &&
                        ((J.Assignment) argument).getVariable() instanceof J.Identifier &&
                        "groups".equals(((J.Identifier) ((J.Assignment) argument).getVariable()).getSimpleName())) {
                        return "(" + argument.toString().trim() + ", alwaysRun = true)";
                    }
                }
            }
        }
        return null;
    }

    /// Closes the mock where it may still be open when it is (re)assigned at the `replaced` statement, as Mockito
    /// refuses to create a second scoped mock for the same type on the same thread. The field is only known to be
    /// set where the mock was assigned in the set-up method or unconditionally earlier in the same method.
    @Nullable
    Statement closeIfOpen(ScopedMock mock, Cursor site, Statement replaced, ExecutionContext ctx) {
        J.MethodDeclaration method = site.firstEnclosing(J.MethodDeclaration.class);
        if (method == null) {
            return null;
        }
        boolean inSetUp = method.getLeadingAnnotations().stream().anyMatch(setUpMatcher::matches);
        boolean openedInSetUp = mock.assignedInSetUp && !inSetUp;
        Boolean mayFindNull = reassignmentMayFindNull.get(siteKey(replaced, mock));
        if (!openedInSetUp && mayFindNull == null) {
            return null;
        }
        boolean guarded = !openedInSetUp && mayFindNull;
        String any = "#{any(" + mock.scopedMockType + ")}";
        return JavaTemplate.builder(guarded ?
                        "if (" + any + " != null) {\n" + any + ".closeOnDemand();\n}" :
                        any + ".closeOnDemand();")
                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                .build()
                .apply(site, replaced.getCoordinates().replace(),
                        guarded ? new Object[]{mock.field(), mock.field()} : new Object[]{mock.field()});
    }

    J.Assignment assign(ScopedMock mock, Expression scopedMock, Space prefix) {
        J.Identifier field = mock.field();
        return new J.Assignment(randomId(), prefix, Markers.EMPTY, field,
                JLeftPadded.<Expression>build(scopedMock.withPrefix(Space.SINGLE_SPACE)).withBefore(Space.SINGLE_SPACE),
                field.getType());
    }

    /// The template declaring a field does not see the mocked type when it is declared in another source file, so
    /// its type parameter is attributed here, for recipes that look for an existing `MockedStatic` of that type.
    private static J.VariableDeclarations withMockedType(J.VariableDeclarations declarations, JavaType.FullyQualified mockedType) {
        JavaType.Parameterized declaredType = TypeUtils.asParameterized(declarations.getType());
        if (declaredType == null) {
            return declarations;
        }
        JavaType.Parameterized fieldType = declaredType.withTypeParameters(singletonList(mockedType));
        TypeTree typeExpression = declarations.getTypeExpression();
        if (typeExpression instanceof J.ParameterizedType) {
            J.ParameterizedType parameterized = (J.ParameterizedType) typeExpression;
            typeExpression = parameterized.withType(fieldType).withTypeParameters(ListUtils.map(parameterized.getTypeParameters(),
                    typeParameter -> typeParameter instanceof J.Identifier ? ((J.Identifier) typeParameter).withType(mockedType) : typeParameter));
        }
        return declarations.withTypeExpression(typeExpression).withVariables(ListUtils.map(declarations.getVariables(),
                variable -> variable
                        .withName(variable.getName().withType(fieldType).withFieldType(
                                variable.getName().getFieldType() == null ? null : variable.getName().getFieldType().withType(fieldType)))
                        .withVariableType(variable.getVariableType() == null ? null : variable.getVariableType().withType(fieldType))));
    }

    J.ClassDeclaration declareFields(JavaVisitor<ExecutionContext> visitor, J.ClassDeclaration cd, ExecutionContext ctx) {
        List<ScopedMock> toDeclare = new ArrayList<>();
        for (ScopedMock mock : mocks.values()) {
            if (mock.declare && mock.field == null) {
                toDeclare.add(0, mock);
            }
        }
        J.ClassDeclaration original = cd;
        for (ScopedMock mock : toDeclare) {
            String simpleName = mock.scopedMockType.substring(mock.scopedMockType.lastIndexOf('.') + 1);
            cd = JavaTemplate.builder("private " + (mock.isStatic ? "static " : "") +
                                      simpleName + "<" + mock.className + "> " + mock.fieldName + ";")
                    .contextSensitive()
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                    .imports(mock.scopedMockType)
                    .build()
                    .apply(new Cursor(visitor.getCursor().getParentOrThrow(), cd), cd.getBody().getCoordinates().firstStatement());
            visitor.maybeAddImport(mock.scopedMockType);
        }
        cd = separateAddedMembers(original, cd);
        return cd.withBody(cd.getBody().withStatements(ListUtils.map(cd.getBody().getStatements(), statement -> {
            if (!(statement instanceof J.VariableDeclarations)) {
                return statement;
            }
            J.VariableDeclarations declarations = (J.VariableDeclarations) statement;
            for (ScopedMock mock : mocks.values()) {
                if (mock.field == null && declarations.getVariables().size() == 1 &&
                    mock.fieldName.equals(declarations.getVariables().get(0).getSimpleName())) {
                    declarations = withMockedType(declarations, mock.mockedType);
                    mock.field = declarations.getVariables().get(0).getName();
                }
            }
            return declarations;
        })));
    }

    J.ClassDeclaration closeAfterEachTest(JavaVisitor<ExecutionContext> visitor, J.ClassDeclaration cd,
                                          String tearDownName, ExecutionContext ctx) {
        StringBuilder closing = new StringBuilder();
        List<J.Identifier> fields = new ArrayList<>();
        for (ScopedMock mock : mocks.values()) {
            if (!mock.assigned || mock.field == null) {
                continue;
            }
            String any = "#{any(" + mock.scopedMockType + ")}";
            if (mock.alwaysAssigned) {
                closing.append(any).append(".closeOnDemand();\n");
                fields.add(mock.field());
            } else {
                closing.append("if (").append(any).append(" != null) {\n").append(any).append(".closeOnDemand();\n}\n");
                fields.add(mock.field());
                fields.add(mock.field());
            }
        }
        if (fields.isEmpty()) {
            return cd;
        }
        cd = maybeAddMethodWithAnnotation(visitor, cd, ctx, framework.publicMethods, tearDownName,
                framework.tearDownAnnotationSignature, framework.tearDownAnnotation, framework.classpathResource,
                framework.tearDownImport, testGroups == null ? framework.tearDownAnnotationParameters : testGroups);
        return appendToMethodAnnotatedWith(visitor, cd, new AnnotationMatcher(framework.tearDownAnnotationSignature),
                closing.toString(), fields.toArray(), ctx);
    }

    private static J.ClassDeclaration appendToMethodAnnotatedWith(JavaVisitor<ExecutionContext> visitor, J.ClassDeclaration cd,
                                                                  AnnotationMatcher matcher, String code, Object[] parameters,
                                                                  ExecutionContext ctx) {
        Cursor bodyCursor = new Cursor(new Cursor(visitor.getCursor().getParentOrThrow(), cd), cd.getBody());
        List<Statement> statements = cd.getBody().getStatements();
        for (int i = 0; i < statements.size(); i++) {
            if (!(statements.get(i) instanceof J.MethodDeclaration)) {
                continue;
            }
            J.MethodDeclaration m = (J.MethodDeclaration) statements.get(i);
            if (m.getBody() != null && m.getLeadingAnnotations().stream().anyMatch(matcher::matches)) {
                m = JavaTemplate.builder(code)
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                        .build()
                        .apply(new Cursor(bodyCursor, m), m.getBody().getCoordinates().lastStatement(), parameters);
                List<Statement> updated = new ArrayList<>(statements);
                updated.set(i, m);
                return cd.withBody(cd.getBody().withStatements(updated));
            }
        }
        return cd;
    }

    private static String siteKey(J site, ScopedMock mock) {
        return site.getId() + key(mock.scopedMockType, mock.mockedType);
    }

    private static String key(String scopedMockType, JavaType.FullyQualified mockedType) {
        return scopedMockType + "<" + mockedType.getFullyQualifiedName() + ">";
    }
}
