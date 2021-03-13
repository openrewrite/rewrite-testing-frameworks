package org.openrewrite.java.testing

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.openrewrite.InMemoryExecutionContext
import org.openrewrite.Parser
import org.openrewrite.config.Environment
import org.openrewrite.java.JavaParser
import org.openrewrite.maven.MavenParser
import org.openrewrite.maven.cache.LocalMavenArtifactCache
import org.openrewrite.maven.cache.MapdbMavenPomCache
import org.openrewrite.maven.cache.ReadOnlyLocalMavenArtifactCache
import org.openrewrite.maven.internal.MavenParsingException
import org.openrewrite.maven.utilities.MavenArtifactDownloader
import org.openrewrite.maven.utilities.MavenProjectParser
import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.Consumer

class RunOnMavenProjectOnDisk {
    @Test
    @EnabledIfEnvironmentVariable(named = "rewrite.project", matches = ".*")
    fun junitBestPractices() {
        val recipe = Environment.builder()
            .scanClasspath(emptyList())
            .build()
            .activateRecipes("org.openrewrite.java.testing.junit5.JUnit5BestPractices")

        val projectDir = Paths.get(System.getenv("rewrite.project"))

        val errorConsumer = Consumer<Throwable> { t ->
            if (t is MavenParsingException) {
                println("  ${t.message}")
            } else {
                t.printStackTrace()
            }
        }

        val onParse = object : Parser.Listener {
            var n = 1
            override fun onParseSucceeded(sourcePath: Path) {
                println("${n++} SUCCESS - $sourcePath")
            }

            override fun onParseFailed(sourcePath: Path) {
                println("${n++} FAILED - $sourcePath")
            }
        }

        val downloader = MavenArtifactDownloader(
            ReadOnlyLocalMavenArtifactCache.MAVEN_LOCAL.orElse(
                LocalMavenArtifactCache(Paths.get(System.getProperty("user.home"), ".rewrite-cache", "artifacts"))
            ),
            null,
            errorConsumer
        )

        val pomCache = MapdbMavenPomCache(
            Paths.get(System.getProperty("user.home"), ".rewrite-cache", "poms").toFile(),
            null
        )

        val mavenParserBuilder = MavenParser.builder()
            .doOnParse(onParse)
            .cache(pomCache)
            .resolveOptional(false)
            .mavenConfig(projectDir.resolve(".mvn/maven.config"))

        val parser = MavenProjectParser(
            downloader,
            mavenParserBuilder,
            JavaParser.fromJavaVersion(),
            InMemoryExecutionContext(errorConsumer)
        )

        val sources = parser.parse(projectDir)
        val results = recipe.run(sources)

        for (result in results) {
            println(result.before!!.sourcePath)
            println("-----------------------------------------")
            println(result.diff())
        }
    }
}
