package com.carcam.platecheck.util

import android.graphics.Rect
import com.google.mlkit.vision.text.Text

/**
 * Shared plate-extraction logic used by both the live camera pipeline (MainActivity)
 * and the instrumented benchmark test, so benchmark results reflect production behavior.
 */
object PlateOcrEngine {
    fun extractPlates(visionText: Text): List<Pair<Rect?, String>> {
        val results = mutableListOf<Pair<Rect?, String>>()
        val blocks = visionText.textBlocks

        for (block in blocks) {
            val candidate = KoreanPlateRecognizer.extractPlateNumberLenient(block.text)
            if (candidate != null) {
                results.add(block.boundingBox to candidate)
            }
        }

        if (results.isEmpty()) {
            // 초록/구형 번호판은 지역명+숫자(윗줄)와 한글+숫자(아랫줄)가 서로 다른 블록으로 인식되는
            // 경우가 많다. 단일 블록에서 못 찾았으면 위-아래로 쌓인 블록 쌍을 합쳐서 한 번 더 시도한다.
            for ((boxUnion, mergedText) in stackedLinePairs(blocks)) {
                val candidate = KoreanPlateRecognizer.extractPlateNumberLenient(mergedText)
                if (candidate != null) {
                    results.add(boxUnion to candidate)
                }
            }
        }

        return results
    }

    // 마지막까지 번호판으로 파싱되지 않은, 그러나 숫자가 충분히 들어있어서 잘림/저해상도로 번호판을
    // 놓쳤을 가능성이 있는 영역. 단일 블록뿐 아니라 위아래로 쌓인 블록 쌍(초록 2단 번호판)도 포함한다.
    // crop+zoom 재인식 패스의 후보 영역으로 쓰인다.
    fun findAmbiguousDigitBlocks(visionText: Text): List<Rect> {
        val blocks = visionText.textBlocks

        val single = blocks.mapNotNull { block ->
            val box = block.boundingBox ?: return@mapNotNull null
            val digitCount = block.text.count { it.isDigit() }
            if (digitCount >= 4 && KoreanPlateRecognizer.extractPlateNumberLenient(block.text) == null) {
                box
            } else {
                null
            }
        }

        val stacked = stackedLinePairs(blocks).mapNotNull { (boxUnion, mergedText) ->
            val digitCount = mergedText.count { it.isDigit() }
            if (digitCount >= 4 && KoreanPlateRecognizer.extractPlateNumberLenient(mergedText) == null) {
                boxUnion
            } else {
                null
            }
        }

        return single + stacked
    }

    // 수직으로 인접하고 수평으로 겹치는 블록 쌍을 위→아래 순서로 합쳐서 (위 블록 텍스트 + 아래 블록
    // 텍스트) 반환한다. 같은 번호판의 두 줄일 가능성이 있는 조합만 후보로 남긴다.
    private fun stackedLinePairs(blocks: List<Text.TextBlock>): List<Pair<Rect, String>> {
        val pairs = mutableListOf<Pair<Rect, String>>()
        for (i in blocks.indices) {
            val boxA = blocks[i].boundingBox ?: continue
            for (j in blocks.indices) {
                if (i == j) continue
                val boxB = blocks[j].boundingBox ?: continue
                if (boxA.top >= boxB.top) continue // A가 항상 위쪽 줄
                if (!areStackedLines(boxA, boxB)) continue
                val union = Rect(boxA)
                union.union(boxB)
                pairs.add(union to (blocks[i].text + blocks[j].text))
            }
        }
        return pairs
    }

    private fun areStackedLines(a: Rect, b: Rect): Boolean {
        val overlapLeft = maxOf(a.left, b.left)
        val overlapRight = minOf(a.right, b.right)
        val overlapWidth = overlapRight - overlapLeft
        val minWidth = minOf(a.width(), b.width())
        if (minWidth <= 0 || overlapWidth < minWidth * 0.5) return false

        val verticalGap = b.top - a.bottom
        val avgHeight = (a.height() + b.height()) / 2
        return verticalGap in (-avgHeight / 2)..(avgHeight * 2)
    }
}
