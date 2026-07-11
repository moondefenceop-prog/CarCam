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
    fun `lenient repairs multi-char substitution to the real character`() {
        // "아" misread as two Latin characters "Of" — repaired via MULTI_CHAR_LOOKALIKE.
        assertEquals("80아7890", KoreanPlateRecognizer.extractPlateNumberLenient("부산 80\nOf 7890"))
    }

    @Test
    fun `lenient repairs jamo-plus-stroke vowel misread to the real syllable`() {
        // "너" (ㄴ+ㅓ) misread as bare jamo "ㄴ" + digit "1" from the vowel's vertical stroke,
        // which otherwise corrupts the digit count. Repaired via JAMO_TO_EO after stripping the
        // spurious digit.
        val result = KoreanPlateRecognizer.extractPlateNumberLenient("56ㄴ19876")
        assertEquals("56너9876", result)
        assertEquals("569876", KoreanPlateRecognizer.digitsOnly(result!!))
    }

    @Test
    fun `jamo-plus-stroke fix does not fire without the digit run needing it`() {
        // Should not spuriously alter text where strict pattern already matches.
        assertEquals("12가3456", KoreanPlateRecognizer.extractPlateNumberLenient("12가3456"))
    }

    @Test
    fun `misread as a different non-whitelisted syllable still digit-matches even unrepaired`() {
        // "너" (ㄴ+ㅓ) misread as "년" (ㄴ+ㅕ+ㄴ) — a different vowel plus a trailing consonant,
        // not just a jongseong-only variant, so no repair rule recovers the exact character.
        // The strict pattern rejects it since "년" isn't a real plate designation character;
        // the lenient path can't repair it either and passes it through unrepaired — but
        // digitsOnly() still lets the DB lookup find the right car regardless.
        assertNull(KoreanPlateRecognizer.extractPlateNumber("56년9876"))
        val result = KoreanPlateRecognizer.extractPlateNumberLenient("56년9876")
        assertEquals("56년9876", result)
        assertEquals("569876", KoreanPlateRecognizer.digitsOnly(result!!))
    }

    @Test
    fun `misread as bare Latin letter lookalike is repaired to the real syllable`() {
        // "너" misread as a single Latin letter "L" instead of splitting into jamo+digit.
        val result = KoreanPlateRecognizer.extractPlateNumberLenient("56L9876")
        assertEquals("56너9876", result)
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
