package com.streakcounter.feature.streaks.presentation.i18n

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import java.io.File
import kotlin.test.Test

/** Reads the resource files straight from the module (host tests run with it as working directory). */
class StringResourcesTest {
    private val root = File("src/commonMain/composeResources")

    private class Entries(val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>)

    private fun load(folder: String): Entries {
        val xml = File(root, "$folder/strings.xml").readText()
        val strings = Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml).associate { it.groupValues[1] to it.groupValues[2] }
        val plurals = Regex("""<plurals name="([^"]+)">(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml).associate { plural ->
                plural.groupValues[1] to Regex("""<item quantity="([^"]+)">(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)
                    .findAll(plural.groupValues[2]).associate { it.groupValues[1] to it.groupValues[2] }
            }
        return Entries(strings, plurals)
    }

    private fun placeholders(text: String) = Regex("%\\d\\\$s").findAll(text).map { it.value }.toSortedSet().toList()

    private val en = load("values")
    private val others = mapOf("de" to load("values-de"), "ar" to load("values-ar"))

    @Test
    fun allLocalesHaveSameKeys() {
        others.forEach { (tag, entries) ->
            assertThat(entries.strings.keys, "$tag strings").containsExactlyInAnyOrder(*en.strings.keys.toTypedArray())
            assertThat(entries.plurals.keys, "$tag plurals").containsExactlyInAnyOrder(*en.plurals.keys.toTypedArray())
        }
    }

    @Test
    fun placeholdersMatchTheEnglishCopy() {
        others.forEach { (tag, entries) ->
            en.strings.forEach { (key, text) ->
                assertThat(placeholders(entries.strings.getValue(key)), "$tag $key").isEqualTo(placeholders(text))
            }
            en.plurals.forEach { (key, items) ->
                val wanted = items.values.flatMap { placeholders(it) }.toSortedSet().toList()
                // "One check-in left" may drop the number, so only the set over the whole plural counts.
                val got = entries.plurals.getValue(key).values.flatMap { placeholders(it) }.toSortedSet().toList()
                assertThat(got, "$tag $key").isEqualTo(wanted)
            }
        }
    }

    @Test
    fun pluralsCoverEveryQuantityOfTheLanguage() {
        val western = listOf("one", "other")
        val arabic = listOf("zero", "one", "two", "few", "many", "other")
        en.plurals.values.forEach { assertThat(it.keys, "en").containsExactlyInAnyOrder(*western.toTypedArray()) }
        others.getValue("de").plurals.forEach { (key, items) ->
            assertThat(items.keys, "de $key").containsExactlyInAnyOrder(*western.toTypedArray())
        }
        others.getValue("ar").plurals.forEach { (key, items) ->
            assertThat(items.keys, "ar $key").containsExactlyInAnyOrder(*arabic.toTypedArray())
        }
    }

    /**
     * Keys whose German or Arabic text may equal the English one: only templates made of
     * placeholders, plus words that are the same in German. Anything else must be translated.
     */
    private val sameAsEnglish: Map<String, Set<String>> = mapOf(
        "de" to setOf(
            "subtitle", "celebrate_heading", "celebrate_amount", "detail_since", // placeholder-only templates
            "type_check_in", "card_check_in_label", // "Check-in" is the German word too
            "field_name", "field_name_optional", // "Name", "Name (optional)"
        ),
        "ar" to setOf("subtitle", "celebrate_heading", "celebrate_amount", "detail_since"),
    )

    @Test
    fun noEnglishLeftInTranslations() {
        others.forEach { (tag, entries) ->
            val allowed = sameAsEnglish.getValue(tag)
            en.strings.forEach { (key, text) ->
                if (key !in allowed) assertThat(entries.strings.getValue(key) != text, "$tag $key is still English").isEqualTo(true)
            }
            en.plurals.forEach { (key, items) ->
                items.forEach { (quantity, text) ->
                    if (key !in allowed && tag == "de") {
                        val translated = entries.plurals.getValue(key).getValue(quantity)
                        assertThat(translated != text, "$tag $key/$quantity is still English").isEqualTo(true)
                    }
                    if (key !in allowed && tag == "ar") {
                        assertThat(entries.plurals.getValue(key).values.none { it == text }, "$tag $key is still English").isEqualTo(true)
                    }
                }
            }
        }
    }
}
