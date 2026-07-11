package com.carcam.platecheck.util

import java.util.regex.Pattern

object KoreanPlateRecognizer {
    // 대한민국 번호판 용도기호로 실제 쓰이는 한글만 모은 화이트리스트.
    // 자가용(가나다라마/거너더러머/버서어저/고노도로모/보소오조/구누두루무/부수우주),
    // 영업용(아자바사), 렌터카(하허호), 택배(배).
    // OCR이 이 목록 밖의 글자를 읽었다면 반드시 오인식이므로 교정 대상이다.
    private val VALID_MIDDLE = setOf(
        '가', '나', '다', '라', '마',
        '거', '너', '더', '러', '머',
        '버', '서', '어', '저',
        '고', '노', '도', '로', '모',
        '보', '소', '오', '조',
        '구', '누', '두', '루', '무',
        '부', '수', '우', '주',
        '아', '자', '바', '사',
        '하', '허', '호', '배'
    )

    // 앞 2~3자리 숫자 + 가운데 1~3문자 + 뒤 4자리 숫자.
    // 가운데를 한글로 한정하지 않는 이유: 오인식 시 라틴 문자('L', 'Of')나
    // 낱자모(ㄴ)로 읽히는 경우까지 잡아서 교정 단계로 넘기기 위함이다.
    // 단 한글/자모/라틴만 허용 — '-' 같은 구분 기호까지 허용하면 전화번호("67-5736")가
    // 번호판으로 오검출되는 것을 실측으로 확인했다.
    private val PLATE_SHAPE = Pattern.compile("([0-9]{2,3})([가-힣ㄱ-ㅎA-Za-z]{1,3})([0-9]{4})")

    // 가운데 한글이 통째로 소실되거나('러'→없음) 숫자로 오인식된('러'→'4', '조'→'2') 경우
    // 남는 것은 순수 숫자 7~8자리 연속열이다. 7자리 = 앞3+뒤4(소실) 또는 앞2+오인식1+뒤4,
    // 8자리 = 앞3+오인식1+뒤4. 좌우에 다른 숫자가 붙어 있으면 번호판이 아니므로 제외한다.
    private val BARE_DIGIT_RUN = Regex("(?<![0-9])[0-9]{7,8}(?![0-9])")
    // 구형: 숫자2 + 한글2 + 숫자4  예) 12가나1234 (지역명 병기형)
    private val OLD_PATTERN = Pattern.compile("[0-9]{2}[가-힣]{2}[0-9]{4}")
    // 외교/임시 특수번호판
    private val SPECIAL_PATTERN = Pattern.compile("[가-힣]{2}[0-9]{4}")

    // '너'(ㄴ+ㅓ)의 모음 세로획이 '1'/'l'/'I'로 오인식되면 "ㄴ1"처럼 자음+획으로 쪼개져 나온다.
    // 이 획은 실제 숫자 자릿수가 아니므로, 패턴 매칭 전에 제거해야 뒤의 진짜 숫자 4자리가 맞는다.
    private val JAMO_PLUS_STROKE = Regex("[ㄱ-ㅎ][1lI]")

    // 낱자모만 읽힌 경우 ㅓ 계열 음절로 조합. (ㅓ의 왼쪽 짧은 획이 소실되면 세로획만 남아
    // 모음 전체가 사라지기 쉬운데, 그 결과가 낱자모 단독 인식이다.)
    private val JAMO_TO_EO = mapOf(
        'ㄱ' to '거', 'ㄴ' to '너', 'ㄷ' to '더', 'ㄹ' to '러', 'ㅁ' to '머',
        'ㅂ' to '버', 'ㅅ' to '서', 'ㅇ' to '어', 'ㅈ' to '저'
    )

    // 한글과 모양이 비슷해 자주 오인식되는 라틴 문자 (단독 1글자로 읽힌 경우).
    private val LATIN_LOOKALIKE = mapOf(
        'L' to '너', 'l' to '너',   // ㄴ+세로획이 L로 뭉개진 경우
        'U' to '우',
        'O' to '오', 'o' to '오', 'Q' to '오',
        'H' to '허'
    )

    // 두 글자 이상으로 쪼개져 읽히는 대표 사례 (소문자로 정규화해서 대조).
    private val MULTI_CHAR_LOOKALIKE = mapOf(
        "of" to '아',   // ㅇ+ㅏ가 O+f로 분리 인식
        "lf" to '너',
        "ol" to '어'
    )

    fun extractPlateNumber(text: String): String? {
        val cleaned = clean(text)
        // 가운데가 화이트리스트에 있는 완전한 번호판만 통과.
        // '56년4895'처럼 한글이긴 하지만 번호판에 없는 글자는 여기서 걸러져 lenient 교정으로 넘어간다.
        val m = PLATE_SHAPE.matcher(cleaned)
        while (m.find()) {
            val mid = m.group(2)!!
            if (mid.length == 1 && mid[0] in VALID_MIDDLE) {
                return m.group(1) + mid + m.group(3)
            }
        }
        OLD_PATTERN.matcher(cleaned).let { if (it.find()) return it.group() }
        SPECIAL_PATTERN.matcher(cleaned).let { if (it.find()) return it.group() }
        return null
    }

    // extractPlateNumber 실패 시 폴백. 오인식된 가운데 글자를 화이트리스트 문자로 교정해서
    // 반환하므로 대부분 표시용으로도 정확하지만, 교정 불가 시 원문 그대로 반환할 수 있어
    // DB 조회는 반드시 digitsOnly() 정규화 비교를 병행해야 한다.
    fun extractPlateNumberLenient(text: String): String? {
        extractPlateNumber(text)?.let { return it }
        val cleaned = clean(text)

        // 자음+획 제거를 먼저 적용 — 원본 그대로 두면 그 획('1')이 숫자 자릿수로 오매칭되어
        // 뒤쪽 진짜 숫자 4자리를 놓치기 때문에 순서가 이래야 한다.
        val jamoFixed = JAMO_PLUS_STROKE.replace(cleaned) { it.value.take(1) }
        for (candidate in listOf(jamoFixed, cleaned).distinct()) {
            val m = PLATE_SHAPE.matcher(candidate)
            while (m.find()) {
                val repaired = repairMiddle(m.group(2)!!)
                if (repaired != null) {
                    return m.group(1) + repaired + m.group(3)
                }
            }
        }

        // 교정 실패 → 모양만 맞으면 원문 그대로 반환 (표시 + 숫자 기반 DB 대조용)
        val m = PLATE_SHAPE.matcher(jamoFixed)
        if (m.find()) return m.group()

        // 가운데 한글이 소실/숫자화된 경우: 순수 숫자 7~8자리 연속열을 후보로 반환.
        // DB 조회는 candidateDigitKeys()로 두 해석(소실/오인식)을 모두 대조한다.
        BARE_DIGIT_RUN.find(cleaned)?.let { return it.value }
        return null
    }

    // 숫자 기반 DB 대조에 쓸 후보 키 목록. 한글이 하나도 없는(=가운데가 소실/숫자화된) 스캔이면
    // 7자리는 "가운데 소실(그대로)"과 "앞2+오인식1+뒤4(3번째 제거)" 두 해석을,
    // 8자리는 "앞3+오인식1+뒤4(4번째 제거)" 해석을 추가로 시도한다.
    fun candidateDigitKeys(scanned: String): List<String> {
        val digits = digitsOnly(scanned)
        val keys = mutableListOf(digits)
        if (scanned.none { it in '가'..'힣' }) {
            when (digits.length) {
                7 -> keys.add(digits.removeRange(2, 3))
                8 -> keys.add(digits.removeRange(3, 4))
            }
        }
        return keys
    }

    // 오인식된 가운데 토큰을 화이트리스트 문자로 교정. 불가능하면 null.
    private fun repairMiddle(token: String): Char? {
        if (token.length == 1) {
            val c = token[0]
            if (c in VALID_MIDDLE) return c
            // '년'→'너': 받침이 덧붙어 읽힌 경우 받침을 떼고 재검사
            stripJongseong(c)?.let { if (it in VALID_MIDDLE) return it }
            LATIN_LOOKALIKE[c]?.let { return it }
            JAMO_TO_EO[c]?.let { return it }
            return null
        }
        return MULTI_CHAR_LOOKALIKE[token.lowercase()]
    }

    // 받침(종성)이 있는 음절이면 받침을 뗀 음절을 반환. 예: 년→너, 곤→고
    private fun stripJongseong(c: Char): Char? {
        if (c < '가' || c > '힣') return null
        val jong = (c - '가') % 28
        return if (jong == 0) null else c - jong
    }

    // 번호판의 숫자 부분(앞 2~3자리 + 뒤 4자리)만 추출. 가운데 한글 오인식에 영향받지 않고
    // DB의 등록 번호판과 비교하기 위한 정규화 키로 사용한다.
    fun digitsOnly(plate: String): String = plate.filter { it.isDigit() }

    // 숫자 기반 매칭의 오매칭 가드: 양쪽 다 가운데 글자가 화이트리스트의 '유효한' 한글로
    // 읽혔는데 서로 다르면(예: 스캔 56너4895 vs 등록 56더4895) 실제로 다른 차량일 가능성이
    // 높으므로 매칭을 거부한다. 한쪽이라도 유효하지 않은 글자면(오인식 확실) 숫자 일치를 신뢰한다.
    fun isMiddleCompatible(scanned: String, registered: String): Boolean {
        val a = middleCharOf(scanned) ?: return true
        val b = middleCharOf(registered) ?: return true
        if (a !in VALID_MIDDLE || b !in VALID_MIDDLE) return true
        return a == b
    }

    private fun middleCharOf(plate: String): Char? {
        val m = PLATE_SHAPE.matcher(clean(plate))
        if (m.find()) {
            val mid = m.group(2)!!
            if (mid.length == 1) return mid[0]
        }
        return null
    }

    fun isValidPlate(plate: String): Boolean {
        val cleaned = clean(plate)
        val m = PLATE_SHAPE.matcher(cleaned)
        if (m.find() && m.group() == cleaned) {
            val mid = m.group(2)!!
            if (mid.length == 1 && mid[0] in VALID_MIDDLE) return true
        }
        return OLD_PATTERN.matcher(cleaned).matches() ||
               SPECIAL_PATTERN.matcher(cleaned).matches()
    }

    private fun clean(text: String) = text.trim().replace(" ", "").replace("\n", "")
}
