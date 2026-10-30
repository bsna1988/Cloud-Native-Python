package com.homebooks.android.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.homebooks.android.data.model.RecognizedBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.ceil

@RunWith(AndroidJUnit4::class)
class OnDeviceRecognitionEngineTest {

    @Test
    fun recognizeShelfLocallyReadsKnownSpineTextFromFixture() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val imageFile = copyShelfFixtureToCache(context.cacheDir)

        try {
            val result = OnDeviceRecognitionEngine.recognizeShelfLocally(
                context,
                listOf(imageFile)
            )
            assertTrue(
                "Shelf OCR failed: ${result.exceptionOrNull()?.message}",
                result.isSuccess
            )
            val books = result.getOrThrow()
            val recognizedText = books.mapNotNull(RecognizedBook::title)
            val rawRecognizedText = books.map { it.raw_title ?: it.title.orEmpty() }
            val matchedTitles = expectedTitles.filter { expected ->
                recognizedText.any { candidate -> titleMatches(expected, candidate) }
            }
            val rawMatchedTitles = expectedTitles.filter { expected ->
                rawRecognizedText.any { candidate -> titleMatches(expected, candidate) }
            }
            val exactMatchedTitles = expectedTitles.filter { expected ->
                recognizedText.any { candidate -> normalize(expected) == normalize(candidate) }
            }
            val rawExactMatchedTitles = expectedTitles.filter { expected ->
                rawRecognizedText.any { candidate -> normalize(expected) == normalize(candidate) }
            }
            val changedTitles = books.filter { !it.raw_title.isNullOrBlank() }
            val fixedTitleTokens = changedTitles.sumOf { book ->
                val rawTokens = normalize(book.raw_title.orEmpty()).split(" ").toSet()
                val correctedTokens = normalize(book.title.orEmpty()).split(" ").toSet()
                (correctedTokens - rawTokens).count { correctedToken ->
                    expectedTitles.any { expected ->
                        correctedToken in normalize(expected).split(" ")
                    }
                }
            }
            val changedToNonReferenceTokens = changedTitles.sumOf { book ->
                val rawTokens = normalize(book.raw_title.orEmpty()).split(" ").toSet()
                val correctedTokens = normalize(book.title.orEmpty()).split(" ").toSet()
                (correctedTokens - rawTokens).count { correctedToken ->
                    expectedTitles.none { expected ->
                        correctedToken in normalize(expected).split(" ")
                    }
                }
            }
            android.util.Log.i(
                "ShelfOcrBenchmark",
                "Approximate titles: raw ${rawMatchedTitles.size}/${expectedTitles.size}, " +
                    "corrected ${matchedTitles.size}/${expectedTitles.size}; " +
                    "exact titles: raw ${rawExactMatchedTitles.size}/${expectedTitles.size}, " +
                    "corrected ${exactMatchedTitles.size}/${expectedTitles.size}; " +
                    "changed title candidates: ${changedTitles.size}; " +
                    "changed tokens matching reference: $fixedTitleTokens; " +
                    "changed tokens absent from reference: $changedToNonReferenceTokens. " +
                    "Raw candidates: $rawRecognizedText. Corrected candidates: $recognizedText"
            )

            assertTrue(
                "Correction must not reduce fixture title matches: raw " +
                    "${rawMatchedTitles.size}, corrected ${matchedTitles.size}.",
                matchedTitles.size >= rawMatchedTitles.size
            )
            assertTrue(
                "Correction must not reduce exact fixture title matches: raw " +
                    "${rawExactMatchedTitles.size}, corrected ${exactMatchedTitles.size}.",
                exactMatchedTitles.size >= rawExactMatchedTitles.size
            )
            assertTrue(
                "Matched ${matchedTitles.size}/${expectedTitles.size} reference titles " +
                    "(${matchedTitles.size * 100 / expectedTitles.size}%). " +
                    "Matched: $matchedTitles. OCR candidates: $recognizedText",
                matchedTitles.size >= MIN_MATCHED_TITLES
            )
            assertTrue(
                "OCR must not guess authors",
                books.all { it.author.isNullOrBlank() }
            )
            assertTrue(
                "Corrected OCR must retain the original text for review",
                books.all { it.raw_title == null || it.raw_title != it.title }
            )
            assertEquals("OCR should not return duplicate text candidates", books.size, recognizedText.distinct().size)
            assertTrue(
                "Candidate text must come from the image, not its filename",
                books.none { it.title.equals("bookshelf", ignoreCase = true) }
            )
        } finally {
            imageFile.delete()
        }
    }

    private fun copyShelfFixtureToCache(cacheDir: File): File {
        val context = InstrumentationRegistry.getInstrumentation().context
        val destination = File(cacheDir, "bookshelf-recognition-test.jpg")
        context.assets.open(SHELF_FIXTURE_ASSET).use { input ->
            destination.outputStream().use(input::copyTo)
        }
        return destination
    }

    private fun titleMatches(expectedTitle: String, candidate: String): Boolean {
        val expectedWords = normalize(expectedTitle).split(" ").filter(String::isNotBlank)
        val candidateWords = normalize(candidate).split(" ").filter(String::isNotBlank).toSet()
        if (expectedWords.isEmpty()) return false

        val matchedWordCount = expectedWords.count(candidateWords::contains)
        val requiredWordCount = when {
            expectedWords.size == 1 -> 1
            expectedWords.size == 2 -> 2
            else -> ceil(expectedWords.size * MIN_TITLE_TOKEN_RECALL).toInt().coerceAtLeast(2)
        }
        return matchedWordCount >= requiredWordCount
    }

    private fun normalize(value: String): String =
        java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    private companion object {
        const val SHELF_FIXTURE_ASSET = "bookshelf.jpg"
        const val MIN_MATCHED_TITLES = 15
        const val MIN_TITLE_TOKEN_RECALL = 0.6
        val expectedTitles = listOf(
            "Планета міркувань (3 клас)",
            "Vingt mille lieues sous les mers",
            "Le petit vampire qui avait peur du noir",
            "Les guerres mondiale",
            "Simplissime Magie",
            "UKR MIKH",
            "A B C D E",
            "Le Vaisseau aux 100 Pièces",
            "Burston/Harchy",
            "Mes Origamis",
            "Beethoven",
            "Le petit écheveau",
            "Les enfants qui rêvaient de voler",
            "La moufle",
            "Nouvel Atlas Mondial",
            "Planète en guerre, planète en paix",
            "Journal d'un Noob - Pirates",
            "Origami",
            "Mes comptines préférées au piano",
            "100 Jeux",
            "15 recettes de Geoffroy faciles à réussir",
            "La cuisine sans feu",
            "Mes recettes de p'tit chef",
            "Guide to Modern Tanks and Fighting Vehicles",
            "Pourquoi les hommes mentent et les femmes pleurent",
            "Je sais le faire",
            "Petits Gourmets",
            "Lettres et mots",
            "1000 Questions et Réponses",
            "Construire un monde magique",
            "Encyclopédie de la correspondance",
            "Conjugaison progressive du français"
        )
    }
}
