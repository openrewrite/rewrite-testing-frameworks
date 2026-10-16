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
package org.openrewrite.java.testing.junit5;

import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.openrewrite.SourceFile;
import org.openrewrite.test.SourceSpecs;

import java.io.File;
import java.io.PrintWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;

final class KotlinJUnit5ExecutionAssertions {
    private static final Map<String, String> sources = new ConcurrentHashMap<>();
    private static final String JVM_TARGET = System.getProperty("rewrite.kotlin.testJvmTarget", "17");

    private KotlinJUnit5ExecutionAssertions() {
    }

    static SourceSpecs kotlin(String before, String after) {
        String test = Arrays.stream(new Throwable().getStackTrace())
                .filter(frame -> frame.getClassName().startsWith("org.openrewrite.java.testing.") &&
                        frame.getClassName().endsWith("Test"))
                .findFirst().map(frame -> frame.getClassName() + "." + frame.getMethodName()).orElse("fixture");
        String name = test.substring(test.lastIndexOf('.') + 1) + "_" + Integer.toUnsignedString(before.hashCode(), 16);
        return org.openrewrite.kotlin.Assertions.kotlin(before, after, spec ->
                spec.afterRecipe(cu -> sources.put(name, cu.printAll())));
    }

    static void compileAndRun() throws Exception {
        if (sources.isEmpty()) {
            return;
        }
        Path directory = Files.createTempDirectory("junit-kotlin-parity-");
        try {
            Path classes = Files.createDirectory(directory.resolve("classes"));
            String classpath = withoutLegacyJUnit(runtimeClasspath());
            List<String> compiler = new ArrayList<>(Arrays.asList(java(), "-cp", classpath,
                    "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect",
                    "-jvm-target", JVM_TARGET, "-classpath", classpath, "-d", classes.toString()));
            List<String> packages = new ArrayList<>();
            for (Map.Entry<String, String> fixture : new TreeMap<>(sources).entrySet()) {
                String source = fixture.getValue();
                String packageName = "parity." + fixture.getKey();
                packages.add(packageName);
                Matcher originalPackage = Pattern.compile("(?m)^package ([^\\r\\n]+)").matcher(source);
                if (originalPackage.find()) {
                    source = source.replace(originalPackage.group(1) + ".", packageName + ".");
                }
                source = source.replaceFirst("(?m)^package [^\\r\\n]+[\\r\\n]+", "");
                source = source.replaceAll("(@MethodSource\\(\")([^.\"#]+#)", "$1" + packageName + ".$2");
                Path file = directory.resolve(fixture.getKey() + ".kt");
                Files.write(file, ("package " + packageName + "\n\n" + source).getBytes(StandardCharsets.UTF_8));
                compiler.add(file.toString());
            }
            run(compiler, directory.resolve("compiler.log"));
            List<String> launcher = new ArrayList<>(Arrays.asList(java(), "-cp",
                    classes + File.pathSeparator + classpath, JupiterLauncher.class.getName()));
            launcher.addAll(packages);
            run(launcher, directory.resolve("jupiter.log"));
        } finally {
            sources.clear();
            try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
                for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    static void compileAndRunParity(List<SourceFile> sources) throws Exception {
        Path directory = Files.createTempDirectory("junit-java-kotlin-parity-");
        try {
            Path classes = Files.createDirectory(directory.resolve("classes"));
            String classpath = withoutLegacyJUnit(runtimeClasspath());
            List<String> kotlin = new ArrayList<>(Arrays.asList(java(), "-cp", classpath,
                    "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect",
                    "-jvm-target", JVM_TARGET, "-classpath", classpath, "-d", classes.toString()));
            List<String> javac = new ArrayList<>(Arrays.asList(Paths.get(System.getProperty("java.home"),
                    "bin", "javac").toString(), "--release", JVM_TARGET, "-classpath", classpath, "-d", classes.toString()));
            for (SourceFile source : sources) {
                Path file = directory.resolve(source.getSourcePath().getFileName());
                Files.write(file, source.printAll().getBytes(StandardCharsets.UTF_8));
                (file.toString().endsWith(".kt") ? kotlin : javac).add(file.toString());
            }
            run(javac, directory.resolve("javac.log"));
            run(kotlin, directory.resolve("kotlinc.log"));
            run(Arrays.asList(java(), "-cp", classes + File.pathSeparator + classpath,
                    JupiterLauncher.class.getName(), "parity.java:14:1", "parity.kotlin:14:1"),
                    directory.resolve("jupiter.log"));
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
                for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static String java() {
        return Paths.get(System.getProperty("java.home"), "bin", "java").toString();
    }

    private static String runtimeClasspath() {
        return System.getProperty("rewrite.kotlin.executionClasspath", System.getProperty("java.class.path"));
    }

    private static String withoutLegacyJUnit(String classpath) {
        return String.join(File.pathSeparator, Arrays.stream(classpath.split(Pattern.quote(File.pathSeparator)))
                .filter(path -> !containsLegacyJUnit(Paths.get(path)))
                .toArray(String[]::new));
    }

    private static boolean containsLegacyJUnit(Path path) {
        String test = "org/junit/Test.class";
        String vintage = "org/junit/vintage/engine/VintageTestEngine.class";
        if (Files.isDirectory(path)) {
            return Files.exists(path.resolve(test)) || Files.exists(path.resolve(vintage));
        }
        if (Files.isRegularFile(path) && path.toString().endsWith(".jar")) {
            try (JarFile jar = new JarFile(path.toFile())) {
                return jar.getJarEntry(test) != null || jar.getJarEntry(vintage) != null;
            } catch (IOException e) {
                throw new IllegalStateException("Cannot inspect execution classpath: " + path, e);
            }
        }
        return false;
    }

    private static void run(List<String> command, Path log) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("Kotlin compilation or Jupiter execution timed out: " + command);
        }
        assertThat(process.exitValue()).withFailMessage("%s\n%s", log.getFileName(),
                new String(Files.readAllBytes(log), StandardCharsets.UTF_8)).isZero();
    }

    public static class JupiterLauncher {
        public static void main(String[] packages) {
            for (String legacy : Arrays.asList("org.junit.Test", "org.junit.vintage.engine.VintageTestEngine")) {
                try {
                    Class.forName(legacy);
                    throw new AssertionError("Legacy JUnit unexpectedly available: " + legacy);
                } catch (ClassNotFoundException expected) {
                    // The migrated fixtures must compile and execute without legacy JUnit.
                }
            }
            for (String selection : packages) {
                String[] expected = selection.split(":");
                String packageName = expected[0];
                SummaryGeneratingListener listener = new SummaryGeneratingListener();
                LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request()
                        .selectors(DiscoverySelectors.selectPackage(packageName)).build(), listener);
                TestExecutionSummary summary = listener.getSummary();
                if (summary.getTestsFoundCount() == 0 || summary.getTestsFailedCount() != 0 ||
                        summary.getContainersFailedCount() != 0 ||
                        expected.length == 3 && (summary.getTestsFoundCount() != Long.parseLong(expected[1]) ||
                                summary.getTestsSkippedCount() != Long.parseLong(expected[2]) ||
                                summary.getTestsSucceededCount() != Long.parseLong(expected[1]) - Long.parseLong(expected[2]))) {
                    summary.printTo(new PrintWriter(System.err, true));
                    summary.printFailuresTo(new PrintWriter(System.err, true));
                    throw new AssertionError("Migrated Kotlin tests failed discovery or execution: " + packageName);
                }
            }
        }
    }
}
