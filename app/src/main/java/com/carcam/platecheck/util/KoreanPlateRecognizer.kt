package com.carcam.platecheck.util

import java.util.regex.Pattern

object KoreanPlateRecognizer {
    // 신형 (2019~): 숫자2~3 + 한글1 + 숫자4  예) 123가4567
    private val NEW_PATTERN = Pattern.compile("[0-9]{2,3}[가-힣][0-9]{4}")
    // 구형: 숫자2 + 한글2 + 숫자4  예) 12가나1234
    private val OLD_PATTERN = Pattern.compile("[0-9]{2}[가-힣]{2}[0-9]{4}")
    // 외교/임시 특수번호판
    private val SPECIAL_PATTERN = Pattern.compile("[가-힣]{2}[0-9]{4}")

    fun extractPlateNumber(text: String): String? {
        val cleaned = text.replace(" ", "").replace("\n", "")
        NEW_PATTERN.matcher(cleaned).let { if (it.find()) return it.group() }
        OLD_PATTERN.matcher(cleaned).let { if (it.find()) return it.group() }
        SPECIAL_PATTERN.matcher(cleaned).let { if (it.find()) return it.group() }
        return null
    }

    fun isValidPlate(plate: String): Boolean {
        val cleaned = plate.trim().replace(" ", "")
        return NEW_PATTERN.matcher(cleaned).matches() ||
               OLD_PATTERN.matcher(cleaned).matches() ||
               SPECIAL_PATTERN.matcher(cleaned).matches()
    }
}
