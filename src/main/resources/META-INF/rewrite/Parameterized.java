/*
 * Copyright 2015-2021 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * https://www.eclipse.org/legal/epl-v20.html
 */
package org.junit.jupiter.params;

import java.util.List;
import java.util.Optional;

public @interface ParameterizedTest {
    String DISPLAY_NAME_PLACEHOLDER = "{displayName}";
    String INDEX_PLACEHOLDER = "{index}";
    String ARGUMENTS_PLACEHOLDER = "{arguments}";
    String ARGUMENTS_WITH_NAMES_PLACEHOLDER = "{argumentsWithNames}";
    String DEFAULT_DISPLAY_NAME = "[{index}] {argumentsWithNames}";
    String name() default "[{index}] {argumentsWithNames}";
}

---

        package org.junit.jupiter.params.provider;

        import org.junit.jupiter.params.ParameterizedTest;
        import org.junit.jupiter.api.extension.ExtensionContext;
        import java.util.stream.Stream;
        import java.lang.reflect.Method;

public @interface ArgumentsSource {
    Class<? extends ArgumentsProvider> value();
}


@ArgumentsSource(MethodArgumentsProvider.class)
public @interface MethodSource {
    String[] value() default "";
}

class MethodArgumentsProvider implements ArgumentsProvider {
    public void accept(MethodSource annotation) {
    }
    public Stream<Arguments> provideArguments(ExtensionContext context) {
        return (Stream<Arguments>) (Object) null;
    }
    private Method getMethod(ExtensionContext context, String factoryMethodName) {
        return (Method) (Object) null;
    }
    private Method getMethodByFullyQualifiedName(String fullyQualifiedMethodName) {
        return (Method) (Object) null;
    }
    private Class<?> loadRequiredClass(String className) {
        return (Class<?>) (Object) null;
    }
    private static Arguments toArguments(Object item) { return (Arguments) (Object) null; }
}

public interface Arguments {
    Object[] get();
    static Arguments of(Object... arguments) {
        return () -> arguments;
    }
    static Arguments arguments(Object... arguments) {
        return of(arguments);
    }
}

public interface ArgumentsProvider {
    Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception;
}

---

        package org.junit.jupiter.api.extension;

        import org.junit.jupiter.api.TestInstance;
        import org.junit.jupiter.api.TestInstance.Lifecycle;
        import java.lang.reflect.AnnotatedElement;
        import java.lang.reflect.Method;
        import java.util.ArrayList;
        import java.util.Arrays;
        import java.util.Collections;
        import java.util.List;
        import java.util.Map;
        import java.util.Optional;
        import java.util.Set;
        import java.util.function.Function;

public interface ExtensionContext {

    Optional<ExtensionContext> getParent();
    ExtensionContext getRoot();
    String getUniqueId();
    String getDisplayName();
    Set<String> getTags();
    Optional<AnnotatedElement> getElement();
    Optional<Class<?>> getTestClass();
    default Class<?> getRequiredTestClass() {
        return (Class<?>) (Object) null;
    }
    Optional<Lifecycle> getTestInstanceLifecycle();
    Optional<Object> getTestInstance();
    default Object getRequiredTestInstance() {
        return null;
    }
    Optional<TestInstances> getTestInstances();
    default TestInstances getRequiredTestInstances() {
        return (TestInstances) (Object) null;
    }
    Optional<Method> getTestMethod();
    default Method getRequiredTestMethod() {
        return (Method) (Object) null;
    }
    Optional<Throwable> getExecutionException();
    Optional<String> getConfigurationParameter(String key);
    <T> Optional<T> getConfigurationParameter(String key, Function<String, T> transformer);
    void publishReportEntry(Map<String, String> map);
    default void publishReportEntry(String key, String value) {
    }
    default void publishReportEntry(String value) {
    }
    Store getStore(Namespace namespace);
    interface Store {
        interface CloseableResource {
            void close() throws Throwable;
        }
        Object get(Object key);
        <V> V get(Object key, Class<V> requiredType);
        default <V> V getOrDefault(Object key, Class<V> requiredType, V defaultValue) {
            return (V) (Object) null;
        }
        default <V> V getOrComputeIfAbsent(Class<V> type) {
            return (V)(Object)null;
        }
        <K, V> Object getOrComputeIfAbsent(K key, Function<K, V> defaultCreator);
        <K, V> V getOrComputeIfAbsent(K key, Function<K, V> defaultCreator, Class<V> requiredType);
        void put(Object key, Object value);
        Object remove(Object key);
        <V> V remove(Object key, Class<V> requiredType);

    }

    class Namespace {
        public static final Namespace GLOBAL = Namespace.create(new Object());
        public static Namespace create(Object... parts) {
            return (Namespace) (Object) null;
        }
        @Override
        public boolean equals(Object o) {
            return true;
        }
    }
}

public interface TestInstances {
    Object getInnermostInstance();

    List<Object> getEnclosingInstances();

    List<Object> getAllInstances();

    <T> Optional<T> findInstance(Class<T> var1);
}

---

        package org.junit.jupiter.api;

public @interface TestInstance {
    enum Lifecycle {
        PER_CLASS,
        PER_METHOD;
    }
    Lifecycle value();
}
