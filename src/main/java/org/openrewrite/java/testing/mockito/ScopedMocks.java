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
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.java.testing.mockito.MockitoUtils.maybeAddMethodWithAnnotation;

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
    private final Map<J.MethodDeclaration, Set<ScopedMock>> assignedPerMethod = new IdentityHashMap<>();
    private final TestFramework framework;
    private final AnnotationMatcher setUpMatcher;
    private final J.ClassDeclaration classDecl;
    private @Nullable String testGroups;

    ScopedMocks(J.ClassDeclaration classDecl, TestFramework framework) {
        this.classDecl = classDecl;
        this.framework = framework;
        this.setUpMatcher = new AnnotationMatcher(framework.setUpAnnotationSignature);
        for (Statement statement : classDecl.getBody().getStatements()) {
            if (statement instanceof J.VariableDeclarations) {
                for (J.VariableDeclarations.NamedVariable variable : ((J.VariableDeclarations) statement).getVariables()) {
                    fieldNames.add(variable.getSimpleName());
                    JavaType.Parameterized type = TypeUtils.asParameterized(variable.getType());
                    if (type == null || type.getTypeParameters().size() != 1) {
                        continue;
                    }
                    JavaType.FullyQualified mocked = TypeUtils.asFullyQualified(type.getTypeParameters().get(0));
                    for (String scopedMockType : new String[]{MOCKED_STATIC, MOCKED_CONSTRUCTION}) {
                        if (mocked != null && TypeUtils.isOfClassType(type, scopedMockType)) {
                            ScopedMock mock = new ScopedMock(scopedMockType, mocked, mocked.getClassName(), variable.getSimpleName());
                            mock.declare = false;
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
        J.Block initializer = site.firstEnclosing(J.Block.class);
        boolean isStatic = method == null ?
                initializer != null && initializer.isStatic() :
                method.hasModifier(J.Modifier.Type.Static);
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
        } else if (mock.declare) {
            mock.isStatic |= isStatic;
            mock.alwaysAssigned &= inSetUp;
            mock.assignedInSetUp |= inSetUp;
        }
        if (testGroups == null && method != null && framework == TestFramework.TESTNG) {
            testGroups = testNgGroups(method);
        }
        return mock;
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

    /// Whether the mock may still be open when it is (re)assigned at the site the cursor points to, as Mockito
    /// refuses to create a second scoped mock for the same type on the same thread.
    boolean mayAlreadyBeOpen(ScopedMock mock, Cursor site) {
        J.MethodDeclaration method = site.firstEnclosing(J.MethodDeclaration.class);
        if (method == null) {
            return false;
        }
        boolean inSetUp = method.getLeadingAnnotations().stream().anyMatch(setUpMatcher::matches);
        boolean assignedBefore = !assignedPerMethod.computeIfAbsent(method, m -> new HashSet<>()).add(mock);
        return assignedBefore || (mock.assignedInSetUp && !inSetUp);
    }

    J.Assignment assign(ScopedMock mock, Expression scopedMock, Space prefix) {
        J.Identifier field = mock.field();
        return new J.Assignment(randomId(), prefix, Markers.EMPTY, field,
                JLeftPadded.<Expression>build(scopedMock.withPrefix(Space.SINGLE_SPACE)).withBefore(Space.SINGLE_SPACE),
                field.getType());
    }

    J.MethodInvocation closeOnDemand(ScopedMock mock, Cursor site, J.MethodInvocation replaced, ExecutionContext ctx) {
        return JavaTemplate.builder("#{any(" + mock.scopedMockType + ")}.closeOnDemand()")
                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                .build()
                .<J.MethodInvocation>apply(site, replaced.getCoordinates().replace(), mock.field())
                .withPrefix(replaced.getPrefix().withComments(emptyList()));
    }

    J.ClassDeclaration declareFields(JavaVisitor<ExecutionContext> visitor, J.ClassDeclaration cd, ExecutionContext ctx) {
        List<ScopedMock> toDeclare = new ArrayList<>();
        for (ScopedMock mock : mocks.values()) {
            if (mock.declare && mock.field == null) {
                toDeclare.add(0, mock);
            }
        }
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
        for (Statement statement : cd.getBody().getStatements()) {
            if (statement instanceof J.VariableDeclarations) {
                for (J.VariableDeclarations.NamedVariable variable : ((J.VariableDeclarations) statement).getVariables()) {
                    for (ScopedMock mock : mocks.values()) {
                        if (mock.field == null && mock.fieldName.equals(variable.getSimpleName())) {
                            mock.field = variable.getName();
                        }
                    }
                }
            }
        }
        return cd;
    }

    J.ClassDeclaration closeAfterEachTest(JavaVisitor<ExecutionContext> visitor, J.ClassDeclaration cd,
                                          String tearDownName, ExecutionContext ctx) {
        StringBuilder closing = new StringBuilder();
        List<J.Identifier> fields = new ArrayList<>();
        for (ScopedMock mock : mocks.values()) {
            if (!mock.declare || mock.field == null) {
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

    private static String key(String scopedMockType, JavaType.FullyQualified mockedType) {
        return scopedMockType + "<" + mockedType.getFullyQualifiedName() + ">";
    }
}
