package org.taigaui.designtokens.completion

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DesignTokenCustomPropertyPreviewTest : BasePlatformTestCase() {
    fun testBuildsPreviewFromLookupPsiElementAndChoosesNearestDeclaration() {
        val token = "--tui-custom"
        myFixture.configureByText(
            "styles.css",
            """
            :root {
                $token: red;
            }

            .near {
                $token: calc(1rem + 2px);
                color: var($token);
            }
            """.trimIndent(),
        )
        val usageOffset = myFixture.file.text.lastIndexOf(token)
        val element = requireNotNull(myFixture.file.findElementAt(usageOffset))
        val lookup = LookupElementBuilder.create(element, token)

        val model = lookup.toCustomPropertyPreviewModel(token, project)
        val row =
            requireNotNull(model)
                .sections
                .single()
                .rows
                .single()

        assertEquals(token, model.tokenName)
        assertEquals("Project custom property", model.sections.single().packageName)
        assertEquals("calc(1rem + 2px)", row.resolvedValue)
        assertTrue(row.platform.startsWith("styles.css:"))
        val navigationTarget = requireNotNull(row.navigationTarget)

        assertTrue(navigationTarget.line > 1)
    }

    fun testFallsBackToProjectSearchAndSortsDeclarations() {
        val token = "--tui-project-search"
        myFixture.addFileToProject(
            "styles/z.scss",
            """
            :root {
                $token: 2rem;
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "styles/a.css",
            """
            :root {
                $token: #fff;
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "styles/ignored.txt",
            "$token: ignored;",
        )
        val lookup = LookupElementBuilder.create(token)

        val model = lookup.toCustomPropertyPreviewModel(token, project)
        val rows = requireNotNull(model).sections.single().rows

        assertEquals(2, rows.size)
        assertEquals(listOf("#fff", "2rem"), rows.map { row -> row.resolvedValue })
        assertTrue(rows.all { row -> row.navigationTarget != null })
    }

    fun testReturnsNullWhenLookupAndProjectContainNoDeclaration() {
        val lookup = LookupElementBuilder.create("--tui-missing")

        assertNull(lookup.toCustomPropertyPreviewModel("--tui-missing", project))
    }

    fun testExtractCustomPropertyValueRejectsEmptyAndNestedBlocks() {
        assertNull(extractCustomPropertyValue(":root { --tui-empty: ; }", "--tui-empty"))
        assertNull(
            extractCustomPropertyValue(
                ":root { color: red; } .next { --tui-empty: ; }",
                "--tui-empty",
            ),
        )
        assertEquals(
            "rgb(1 2 3 / 50%)",
            extractCustomPropertyValue(
                ":root { --tui-color: rgb(1 2 3 / 50%); }",
                "--tui-color",
            ),
        )
    }
}
