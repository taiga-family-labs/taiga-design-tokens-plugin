package org.taigaui.designtokens.project

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path

class ProjectStylesheetCoverageTest : BasePlatformTestCase() {
    private lateinit var workspaceRoot: Path

    override fun setUp() {
        super.setUp()
        workspaceRoot = Files.createTempDirectory("project-styles-coverage")
    }

    override fun tearDown() {
        try {
            workspaceRoot.toFile().deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    fun testPathResolverSupportsExternalPackagePartialAndIndexForms() {
        val partial = workspaceRoot.resolve("styles/_theme.scss")
        val index = workspaceRoot.resolve("styles/components/index.less")

        Files.createDirectories(partial.parent)
        Files.writeString(partial, ":root {}")
        Files.createDirectories(index.parent)
        Files.writeString(index, ":root {}")

        assertEquals(partial, ProjectStylesheetPathResolver.resolveSourceFile(workspaceRoot.resolve("styles/theme")))
        assertEquals(index, ProjectStylesheetPathResolver.resolveSourceFile(workspaceRoot.resolve("styles/components")))
        assertTrue(ProjectStylesheetPathResolver.isExternalImport("SASS:color"))
        assertTrue(ProjectStylesheetPathResolver.isExternalImport("DATA:text/css,body{}"))
        assertTrue(ProjectStylesheetPathResolver.isPackageImport("@taiga-ui/core/styles"))
        assertTrue(ProjectStylesheetPathResolver.isPackageImport("node_modules/pkg/styles.css"))
        assertFalse(ProjectStylesheetPathResolver.isStylesheet(Path.of("/")))
    }

    fun testConfigurationReaderFiltersExternalMissingEscapingAndNodeModulesEntries() {
        val projectRoot = workspaceRoot.resolve("apps/demo")
        val configFile = projectRoot.resolve("project.json")
        val localStyle = projectRoot.resolve("src/styles.css")
        val workspaceStyle = workspaceRoot.resolve("shared/theme.less")
        val nodeModulesStyle = workspaceRoot.resolve("node_modules/pkg/styles.css")

        Files.createDirectories(localStyle.parent)
        Files.writeString(localStyle, ":root {}")
        Files.createDirectories(workspaceStyle.parent)
        Files.writeString(workspaceStyle, ":root {}")
        Files.createDirectories(nodeModulesStyle.parent)
        Files.writeString(nodeModulesStyle, ":root {}")

        val content =
            """
            {
              "styles": [
                "src/styles.css",
                "../../shared/theme.less",
                "https://example.com/theme.css",
                "@taiga-ui/core/styles.css",
                "../../node_modules/pkg/styles.css",
                "../../../outside.css",
                "missing.scss"
              ]
            }
            """.trimIndent()
        val reader =
            ProjectStylesheetConfigurationReader(
                readText = { path -> content.takeIf { path == configFile } },
                parser = ProjectStylesheetJsonPsiParser(project),
            )

        assertEquals(
            listOf(listOf(localStyle, workspaceStyle)),
            reader.readStyleGroups(configFile, projectRoot, workspaceRoot),
        )
        assertEmpty(
            reader.readStyleGroups(
                configFile.resolveSibling("missing.json"),
                projectRoot,
                workspaceRoot,
            ),
        )
    }

    fun testJsonParserHandlesEmptyNestedAndMixedStyleArrays() {
        val parser = ProjectStylesheetJsonPsiParser(project)

        assertEmpty(parser.parseStyleGroups(""))
        assertEmpty(parser.parseStyleGroups("""{"styles": []}"""))
        assertEquals(
            listOf(listOf("a.css", "b.less", "c.scss")),
            parser.parseStyleGroups(
                """
                {
                  "styles": [
                    "README.md",
                    "a.css",
                    {"input": "b.less", "nested": ["c.scss", "ignore.txt"]},
                    42,
                    null
                  ]
                }
                """.trimIndent(),
            ),
        )
    }
}
