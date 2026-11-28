package dev.mymouse.core.resources

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Comprobaciones de los recursos de la app (están en el módulo `app`; los tests de `core` corren con `core/` como carpeta de trabajo). */
class AppResourcesTest {
    private val app = File("../app/src")

    private fun stringKeys(file: File): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) }
            .filter { it.attributes.getNamedItem("translatable")?.nodeValue != "false" }
            .map { it.attributes.getNamedItem("name").nodeValue }
            .toSet()
    }

    @Test
    fun spanishAndEnglishHaveTheSameKeys() {
        val en = stringKeys(File(app, "main/res/values/strings.xml"))
        val es = stringKeys(File(app, "main/res/values-es/strings.xml"))
        assertTrue(en.size > 100, "faltan textos en inglés")
        assertEquals(emptySet(), en - es, "sin traducir al español")
        assertEquals(emptySet(), es - en, "sobran en español")
    }

    @Test
    fun placeholdersMatchInBothLanguages() {
        fun placeholders(file: File): Map<String, List<String>> {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val nodes = doc.getElementsByTagName("string")
            return (0 until nodes.length).map { nodes.item(it) }.associate {
                it.attributes.getNamedItem("name").nodeValue to Regex("""%\d\$[sd]""").findAll(it.textContent).map { m -> m.value }.sorted().toList()
            }
        }
        val en = placeholders(File(app, "main/res/values/strings.xml"))
        val es = placeholders(File(app, "main/res/values-es/strings.xml"))
        for ((key, list) in en) assertEquals(list, es[key], key)
    }

    @Test
    fun playVariantHasNoUpdaterNorDeviceAdmin() {
        val main = File(app, "main/AndroidManifest.xml").readText()
        assertFalse(main.contains("REQUEST_INSTALL_PACKAGES"))
        assertFalse(main.contains("AdminReceiver"))
        assertFalse(main.contains("FileProvider"))
        assertFalse(File(app, "play/AndroidManifest.xml").exists() && File(app, "play/AndroidManifest.xml").readText().contains("REQUEST_INSTALL_PACKAGES"))
        val github = File(app, "github/AndroidManifest.xml").readText()
        assertTrue(github.contains("REQUEST_INSTALL_PACKAGES"))
        assertTrue(github.contains("AdminReceiver"))
        for (name in listOf("Updater.kt", "ApkInstaller.kt", "AdminReceiver.kt")) {
            assertFalse(File(app, "main/kotlin/dev/mymouse/$name").exists(), "$name debe estar solo en src/github")
            assertFalse(File(app, "play/kotlin/dev/mymouse/$name").exists(), "$name no debe estar en play")
        }
    }
}
