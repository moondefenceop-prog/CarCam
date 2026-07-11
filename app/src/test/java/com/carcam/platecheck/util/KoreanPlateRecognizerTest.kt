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
    fun `middle Hangul dropped entirely leaves a bare 7-digit run that is still a candidate`() {
        // "154러7070": ML Kit sometimes swallows "러" whole, returning "1547070".
        // The bare digit run must survive as a candidate so the digit-key DB lookup can match.
        val result = KoreanPlateRecognizer.extractPlateNumberLenient("1547070")
        assertEquals("1547070", result)
        // Interpretation (a): middle dropped, all 7 digits real — matches 154러7070's key.
        assert(KoreanPlateRecognizer.candidateDigitKeys(result!!).contains("1547070"))
    }

    @Test
    fun `middle Hangul misread as a digit leaves an 8-digit run repaired via key reinterpretation`() {
        // "154러7070" grayscale read: "러" -> "4", producing "15447070". Dropping the 4th digit
        // (the misread middle) recovers the true key "1547070".
        val result = KoreanPlateRecognizer.extractPlateNumberLenient("15447070")
        assertEquals("15447070", result)
        assert(KoreanPlateRecognizer.candidateDigitKeys(result!!).contains("1547070"))
    }

    @Test
    fun `candidateDigitKeys adds no reinterpretations when scan contains Hangul`() {
        assertEquals(listOf("569876"), KoreanPlateRecognizer.candidateDigitKeys("56너9876"))
    }

    @Test
    fun `phone numbers with separators are not plate candidates`() {
        // Observed real false positive: "L67-5736" (street sign) used to match with '-' as the
        // middle character. The middle charset now excludes separators, and the dash also splits
        // the digits so no 7-8 digit bare run forms.
        assertNull(KoreanPlateRecognizer.extractPlateNumberLenient("L67-5736"))
        assertNull(KoreanPlateRecognizer.extractPlateNumberLenient("TEL 627-9336"))
    }

    @Test
    fun `digit runs adjacent to more digits are not plate candidates`() {
        // 9+ consecutive digits can't be a plate reading; lookarounds must reject.
        assertNull(KoreanPlateRecognizer.extractPlateNumberLenient("123456789"))
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
