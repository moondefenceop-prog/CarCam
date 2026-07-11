package com.carcam.platecheck.util

import java.util.regex.Pattern

object KoreanPlateRecognizer {
    // 신형 (2019~): 숫자2~3 + 한글1 + 숫자4  예) 123가4567
    private val NEW_PATTERN = Pattern.compile("[0-9]{2,3}[가-힣][0-9]{4}")
    // 구형: 숫자2 + 한글2 + 숫자4  예) 12가나1234
    private val OLD_PATTERN = Pattern.compile("[0-9]{2}[가-힣]{2}[0-9]{4}")
    // 외교/임시 특수번호판
    private val SPECIAL_PATTERN = Pattern.compile("[가-힣]{2}[0-9]{4}")
    // 저해상도/흐림 등으로 가운데 한글이 비슷한 라틴 문자나 기호로 오인식되는 경우를 위한 완화 패턴.
    // 숫자 부분만 맞으면 되므로 가운데는 숫자가 아닌 임의 문자를 1~3개까지 허용한다
    // (예: "아"가 한 글자가 아니라 "Of" 두 글자로 오인식되는 경우도 있음).
    private val NEW_PATTERN_LENIENT = Pattern.compile("[0-9]{2,3}[^0-9\\s]{1,3}[0-9]{4}")
    // 자음(ㄱ-ㅎ)만 단독으로 나오는 건 정상적인 번호판에는 없다 (항상 완성된 음절만 쓰임).
    // "너"(ㄴ+ㅓ)처럼 모음의 세로 획이 숫자 '1'로 오인식되어 자음+숫자로 쪼개져 나오는 경우,
    // 그 '1'은 실제로는 숫자 자릿수가 아니므로 제거해야 뒤따르는 진짜 숫자 4자리가 맞아떨어진다.
    private val JAMO_PLUS_ONE = Regex("[ㄱ-ㅎ]1")

    fun extractPlateNumber(text: String): String? {
        val cleaned = text.replace(" ", "").replace("\n", "")
        NEW_PATTERN.matcher(cleaned).let { if (it.find()) return it.group() }
        OLD_PATTERN.matcher(cleaned).let { if (it.find()) return it.group() }
        SPECIAL_PATTERN.matcher(cleaned).let { if (it.find()) return it.group() }
        return null
    }

    // extractPlateNumber가 실패했을 때 폴백으로 사용. 가운데 글자가 한글이 아닐 수 있으므로
    // 화면 표시용으로만 쓰고, DB 조회는 반드시 digitsOnly()로 정규화해서 비교해야 한다.
    fun extractPlateNumberLenient(text: String): String? {
        extractPlateNumber(text)?.let { return it }
        val cleaned = text.replace(" ", "").replace("\n", "")

        // 자음+1 오인식 교정판을 먼저 시도한다 — 원본 그대로 두면 완화 패턴이 그 '1'을 진짜 숫자로
        // 오매칭해서 뒤쪽 진짜 숫자 4자리를 놓치기 때문에, 순서가 이래야 한다.
        val jamoFixed = JAMO_PLUS_ONE.replace(cleaned) { it.value.take(1) }
        if (jamoFixed != cleaned) {
            NEW_PATTERN.matcher(jamoFixed).let { if (it.find()) return it.group() }
            NEW_PATTERN_LENIENT.matcher(jamoFixed).let { if (it.find()) return it.group() }
        }

        NEW_PATTERN_LENIENT.matcher(cleaned).let { if (it.find()) return it.group() }
        return null
    }

    // 번호판의 숫자 부분(앞 2~3자리 + 뒤 4자리)만 추출. 가운데 한글 오인식에 영향받지 않고
    // DB의 등록 번호판과 비교하기 위한 정규화 키로 사용한다.
    fun digitsOnly(plate: String): String = plate.filter { it.isDigit() }

    fun isValidPlate(plate: String): Boolean {
        val cleaned = plate.trim().replace(" ", "")
        return NEW_PATTERN.matcher(cleaned).matches() ||
               OLD_PATTERN.matcher(cleaned).matches() ||
               SPECIAL_PATTERN.matcher(cleaned).matches()
    }
}
