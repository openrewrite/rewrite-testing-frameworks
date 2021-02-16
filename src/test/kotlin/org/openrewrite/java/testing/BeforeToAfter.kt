/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing

import org.openrewrite.InMemoryExecutionContext
import org.openrewrite.java.JavaParser
import org.openrewrite.loadRecipeFromClasspath
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors

fun main(args: Array<String>) {
    val beforeDir = Paths.get(args[0]).resolve("before")
    val afterDir = Paths.get(args[0]).resolve("after")
    val parser: JavaParser = JavaParser.fromJavaVersion()
            .classpath("mockito-all", "junit")
            .build()

    val recipe = loadRecipeFromClasspath(
            "org.openrewrite.java.testing.JUnit5Migration",
            "org.openrewrite.java.testing.Mockito1to3Migration"
    )

    val sources = parser.parse(listJavaSources(beforeDir), beforeDir, InMemoryExecutionContext())
    recipe.run(sources).map {
        val outputFile = afterDir.resolve(it.after!!.sourcePath.toString()).toFile()
        if (!outputFile.parentFile.exists()) {
            outputFile.parentFile.mkdirs()
        }
        outputFile.writeText(it.after!!.printTrimmed())
    }
}

private fun listJavaSources(sourceDirectory: Path): List<Path> {
    val sourceDirectoryFile = sourceDirectory.toFile()
    if (!sourceDirectoryFile.exists()) {
        return emptyList()
    }
    val sourceRoot = sourceDirectoryFile.toPath()
    return Files.walk(sourceRoot)
            .filter { f: Path -> !Files.isDirectory(f) && f.toFile().name.endsWith(".java") }
            .collect(Collectors.toList())
}
