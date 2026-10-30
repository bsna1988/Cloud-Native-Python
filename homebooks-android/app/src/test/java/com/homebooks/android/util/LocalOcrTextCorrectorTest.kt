package com.homebooks.android.util

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalOcrTextCorrectorTest {

    @Test
    fun correctsKnownFrenchOcrConfusionsAndPreservesCaseAndPunctuation() {
        assertEquals(
            "Contes magiques!",
            LocalOcrTextCorrector.correct("Eontes magiqugs!")
        )
        assertEquals("MES ORIGAMIS", LocalOcrTextCorrector.correct("MES ORIBAMIS"))
    }

    @Test
    fun leavesKnownAndUnrecognizedWordsUnchanged() {
        assertEquals("Vaisseau", LocalOcrTextCorrector.correct("Vaisseau"))
        assertEquals(
            "SIMPLISSIME MAGIE",
            LocalOcrTextCorrector.correct("SIMPLISSIME MAGIE")
        )
        assertEquals("Lettres", LocalOcrTextCorrector.correct("Lettres"))
        assertEquals("Xylqor", LocalOcrTextCorrector.correct("Xylqor"))
        assertEquals(
            "Burston/Harchy",
            LocalOcrTextCorrector.correct("Burston/Harchy")
        )
    }
}
