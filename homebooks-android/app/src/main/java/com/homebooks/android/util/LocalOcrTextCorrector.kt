package com.homebooks.android.util

import io.gitlab.rxp90.jsymspell.SymSpell
import io.gitlab.rxp90.jsymspell.SymSpellBuilder
import io.gitlab.rxp90.jsymspell.Verbosity
import java.text.Normalizer
import java.util.Locale

internal object LocalOcrTextCorrector {

    private val wordPattern = Regex("[\\p{L}\\p{M}]+")
    private val accents = Regex("\\p{Mn}+")
    private val lexicons = mapOf(
        "fr" to words(
            """
            a atlas au aux avait avoir barbara beethoven bien cent chef classe comptines
            comptine conjugaison construire contes correspondance cuisine dans de des deux
            dix donc du enfants encyclopédie écheveau faciles faire feu fleur français
            femmes fantômes geoffroy gourmets guerre guerres guide harchy hommes journal jeux
            je lieues lettres le les leur livre magique magiques magie mondial monde moufle
            mille mers mes mentent mondiale mots nouvel noir origami origamis paix peur
            petit petite petits piano pièces planète pleurent pourquoi préférées progressive
            qui recettes réponses rêvaient réussir sais sans simplissime sous questions tanks
            to véhicules vingt vaisseau vampire voler et en
            beaucoup cette chaque comme édition erreur livre livres nouveau ouvrage par pays
            plus pour roman texte titre tous très une avec votre
            """
        ),
        "en" to words(
            """
            all and atlas barbara beethoven book building class children fighting
            guide harchy journal letters lieues magic modern noob pease pirates
            recipes tanks the to vehicles world about after because between books
            correct description edition error from have language many more novel title
            words
            """
        ),
        "uk" to words(
            """
            uk клас міркувань планета читання книжка назва автор помилки
            """
        )
    )
    private val dictionaries = lexicons.mapValues { (_, entries) ->
        val canonicalWords = entries.values.toSet()
        val spellChecker = SymSpellBuilder()
            .setMaxDictionaryEditDistance(MAX_AGGRESSIVE_EDIT_DISTANCE)
            .setUnigramLexicon(canonicalWords.associate { normalize(it) to DEFAULT_FREQUENCY })
            .createSymSpell()
        SpellDictionary(
            spellChecker = spellChecker,
            canonicalWords = canonicalWords.associateBy(::normalize)
        )
    }

    fun correct(text: String): String = correct(text, CONSERVATIVE_EDIT_DISTANCE)

    fun correctAggressively(text: String): String = correct(text, MAX_AGGRESSIVE_EDIT_DISTANCE)

    private fun correct(text: String, maxEditDistance: Int): String =
        wordPattern.replace(text) { match ->
        val source = match.value
        val normalized = normalize(source)
        if (normalized.length < MIN_WORD_LENGTH) {
            return@replace source
        }
        val knownWords = dictionaries.values.mapNotNull { it.canonicalWords[normalized] }
            .distinctBy { it.lowercase(Locale.ROOT) }
        if (knownWords.isNotEmpty()) {
            if (maxEditDistance <= CONSERVATIVE_EDIT_DISTANCE ||
                knownWords.size != 1 ||
                source.equals(knownWords.single(), ignoreCase = true)
            ) {
                return@replace source
            }
            return@replace preserveCase(source, knownWords.single())
        }

        val suggestionsByWord = dictionaries.values
            .flatMap { dictionary ->
                dictionary.spellChecker
                    .lookup(normalized, Verbosity.CLOSEST)
                    .mapNotNull { suggestion ->
                        val canonical = dictionary.canonicalWords[suggestion.suggestion]
                            ?: return@mapNotNull null
                        canonical to suggestion.editDistance
                    }
            }
            .filter { (_, distance) -> distance in 1..MAX_AGGRESSIVE_EDIT_DISTANCE }
            .groupBy({ (word, _) -> normalize(word) }, { (_, distance) -> distance })

        if (suggestionsByWord.size != 1) {
            return@replace source
        }

        val (corrected, distances) = suggestionsByWord.entries.single()
        if ((distances.minOrNull() ?: return@replace source) > maxEditDistance) {
            return@replace source
        }
        val canonical = dictionaries.values
            .firstNotNullOfOrNull { it.canonicalWords[corrected] }
            ?: return@replace source
        preserveCase(source, canonical)
    }

    private fun normalize(word: String): String =
        Normalizer.normalize(word, Normalizer.Form.NFD)
            .replace(accents, "")
            .lowercase(Locale.ROOT)

    private fun preserveCase(source: String, candidate: String): String = when {
        source == source.uppercase(Locale.ROOT) -> candidate.uppercase(Locale.ROOT)
        source.firstOrNull()?.isUpperCase() == true ->
            candidate.replaceFirstChar { it.titlecase(Locale.ROOT) }
        else -> candidate
    }

    private fun words(value: String): Map<String, String> =
        wordPattern.findAll(value).associate { match ->
            normalize(match.value) to match.value
        }

    private data class SpellDictionary(
        val spellChecker: SymSpell,
        val canonicalWords: Map<String, String>
    )

    private const val MIN_WORD_LENGTH = 5
    private const val CONSERVATIVE_EDIT_DISTANCE = 1
    private const val MAX_AGGRESSIVE_EDIT_DISTANCE = 3
    private const val DEFAULT_FREQUENCY = 1L
}
