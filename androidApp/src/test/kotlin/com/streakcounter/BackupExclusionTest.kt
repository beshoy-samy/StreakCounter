package com.streakcounter

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isNotNull
import com.streakcounter.feature.streaks.data.STREAK_FILE_NAME
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The streak file is encrypted with a Keystore key that never leaves the device, so a restored copy
 * could never be read. The merged manifest must point at rules that exclude it from every backup and
 * transfer path.
 *
 * A JVM test, not an instrumented one: Robolectric does not populate the backup attributes, and the
 * merged manifest is a plain file whose path AGP publishes in `test_config.properties`. The rule files
 * are read from the app's own `res/xml` (no library module ships backup rules).
 */
class BackupExclusionTest {
    private val excluded = "datastore/$STREAK_FILE_NAME"
    private val android = "http://schemas.android.com/apk/res/android"

    private fun parse(file: File): Element =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(file).documentElement

    private fun mergedApplication(): Element {
        val config = Properties().apply {
            checkNotNull(BackupExclusionTest::class.java.classLoader.getResourceAsStream("com/android/tools/test_config.properties")) {
                "AGP test_config.properties missing: is unitTests.isIncludeAndroidResources on?"
            }.use(::load)
        }
        val manifest = File(config.getProperty("android_merged_manifest"))
        return parse(manifest).getElementsByTagName("application").item(0) as Element
    }

    /** `@xml/name` attribute of `<application>` as the rule file in the app's res/xml. */
    private fun ruleFile(attribute: String): File {
        val ref = mergedApplication().getAttributeNS(android, attribute)
        assertThat(ref.takeIf { it.startsWith("@xml/") }).isNotNull()
        return File("src/main/res/xml/${ref.removePrefix("@xml/")}.xml")
    }

    private fun excludedFilePaths(section: Element): List<String> =
        (0 until section.getElementsByTagName("exclude").length).map {
            section.getElementsByTagName("exclude").item(it) as Element
        }.filter { it.getAttribute("domain") == "file" }.map { it.getAttribute("path") }

    private fun section(root: Element, name: String): Element =
        root.getElementsByTagName(name).item(0) as Element

    @Test
    fun datastore_file_excluded_from_backup() {
        val rules = parse(ruleFile("dataExtractionRules"))

        assertThat(excludedFilePaths(section(rules, "cloud-backup"))).contains(excluded)
        assertThat(excludedFilePaths(section(rules, "device-transfer"))).contains(excluded)
    }

    @Test
    fun datastore_file_excluded_from_legacy_full_backup() {
        val rules = parse(ruleFile("fullBackupContent"))

        assertThat(excludedFilePaths(rules)).contains(excluded)
    }
}
