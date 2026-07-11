package com.carcam.platecheck.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KoreanPlateRecognizerTest {

    @Test
    fun `strict match on clean new-style plate`() {
        assertEquals("123가4567", KoreanPlateRecognizer.extractPlateNumber("123가4567"))
    }

    @Test
    fun `strict match on old-style plate`() {
        assertEquals("12가나1234", KoreanPlateRecognizer.extractPlateNumber("12가나1234"))
    }

    @Test
    fun `lenient falls back to single-char substitution`() {
        // "허" misread as a single Latin letter
        assertEquals("04E3456", KoreanPlateRecognizer.extractPlateNumberLenient("04E 3456"))
    }

    @Test
    fun `lenient falls back to multi-char substitution`() {
        // "아" misread as two Latin characters
        assertEquals("80Of7890", KoreanPlateRecognizer.extractPlateNumberLenient("부산 80\nOf 7890"))
    }

    @Test
    fun `lenient recovers from jamo-plus-1 vowel misread`() {
        // "너" (ㄴ+ㅓ) misread as bare jamo "ㄴ" + digit "1" from the vowel's vertical stroke,
        // which otherwise corrupts the digit count and breaks both regex and digit matching.
        val result = KoreanPlateRecognizer.extractPlateNumberLenient("56ㄴ19876")
        assertEquals("56ㄴ9876", result)
        assertEquals("569876", KoreanPlateRecognizer.digitsOnly(result!!))
    }

    @Test
    fun `jamo-plus-1 fix does not fire without the digit run needing it`() {
        // Should not spuriously alter text where strict pattern already matches.
        assertEquals("12가3456", KoreanPlateRecognizer.extractPlateNumberLenient("12가3456"))
    }

    @Test
    fun `misread as a different but structurally valid syllable still digit-matches`() {
        // "너" misread as "년" (a real, different Hangul syllable) — the strict pattern
        // already accepts it since it's a valid single syllable; digitsOnly() is what makes
        // the DB lookup still find the right car despite the wrong displayed character.
        val result = KoreanPlateRecognizer.extractPlateNumber("56년9876")
        assertEquals("56년9876", result)
        assertEquals("569876", KoreanPlateRecognizer.digitsOnly(result!!))
    }

    @Test
    fun `misread as bare Latin letter still digit-matches`() {
        // "너" misread as a single Latin letter "L" instead of splitting into jamo+digit.
        val result = KoreanPlateRecognizer.extractPlateNumberLenient("56L9876")
        assertEquals("56L9876", result)
        assertEquals("569876", KoreanPlateRecognizer.digitsOnly(result!!))
    }

    @Test
    fun `digitsOnly strips everything but digits`() {
        assertEquals("569876", KoreanPlateRecognizer.digitsOnly("56너9876"))
        assertEquals("569876", KoreanPlateRecognizer.digitsOnly("56E9876"))
    }

    @Test
    fun `no match when digits are insufficient`() {
        assertNull(KoreanPlateRecognizer.extractPlateNumberLenient("1가2345"))
    }
}
