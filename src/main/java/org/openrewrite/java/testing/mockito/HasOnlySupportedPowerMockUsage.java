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

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.marker.JavaVersion;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.Plugin;
import org.openrewrite.maven.tree.ResolvedPom;

import java.util.concurrent.atomic.AtomicBoolean;

public class HasOnlySupportedPowerMockUsage extends ScanningRecipe<AtomicBoolean> {

    @Getter
    final String displayName = "Repository has only PowerMock usage that can be migrated to Mockito";

    @Getter
    final String description = "Matches all source files of a repository whose PowerMock usage can be migrated to " +
            "Mockito in full. Repositories that use PowerMock features without a Mockito equivalent, or that compile to " +
            "a Java version older than 8, where the Mockito replacements need lambdas, do not match, so that they can be " +
            "left on PowerMock rather than partially migrated. The decision is made per repository rather than per " +
            "module, as modules typically share PowerMock versions managed by a parent.";

    @Override
    public AtomicBoolean getInitialValue(ExecutionContext ctx) {
        return new AtomicBoolean();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(AtomicBoolean unsupported) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                if (unsupported.get()) {
                    return tree;
                }
                if (tree instanceof JavaSourceFile) {
                    JavaSourceFile sourceFile = (JavaSourceFile) tree;
                    if (usesPowerMock(sourceFile) &&
                        (compilesToJavaBefore8(sourceFile) || !UnsupportedPowerMockUsage.find(sourceFile, ctx).isEmpty())) {
                        unsupported.set(true);
                    }
                } else {
                    tree.getMarkers().findFirst(MavenResolutionResult.class).ifPresent(mrr -> {
                        if (compilesToJavaBefore8(mrr.getPom())) {
                            unsupported.set(true);
                        }
                    });
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(AtomicBoolean unsupported) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                return unsupported.get() ? tree : SearchResult.found(tree);
            }
        };
    }

    private static boolean usesPowerMock(JavaSourceFile sourceFile) {
        for (JavaType type : sourceFile.getTypesInUse().getTypesInUse()) {
            if (UnsupportedPowerMockUsage.isPowerMock(TypeUtils.asFullyQualified(type))) {
                return true;
            }
        }
        return false;
    }

    private static boolean compilesToJavaBefore8(JavaSourceFile sourceFile) {
        return sourceFile.getMarkers().findFirst(JavaVersion.class)
                .map(version -> isBefore8(version.getMajorVersion()))
                .orElse(false);
    }

    private static boolean compilesToJavaBefore8(ResolvedPom pom) {
        for (String property : new String[]{"maven.compiler.source", "maven.compiler.target", "maven.compiler.release"}) {
            if (isBefore8(pom.getProperties().get(property), pom)) {
                return true;
            }
        }
        for (Plugin plugin : pom.getPlugins()) {
            if ("maven-compiler-plugin".equals(plugin.getArtifactId()) && plugin.getConfiguration() != null) {
                for (String setting : new String[]{"source", "target", "release"}) {
                    JsonNode value = plugin.getConfiguration().get(setting);
                    if (value != null && isBefore8(value.asText(), pom)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isBefore8(@Nullable String version, ResolvedPom pom) {
        String resolved = version == null ? null : pom.getValue(version);
        if (resolved == null) {
            return false;
        }
        String major = resolved.trim();
        if (major.startsWith("1.")) {
            major = major.substring(2);
        }
        try {
            return isBefore8(Integer.parseInt(major));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isBefore8(int majorVersion) {
        return 0 < majorVersion && majorVersion < 8;
    }
}
