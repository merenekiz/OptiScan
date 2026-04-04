package com.optiscan.analysis.models

data class ScoringConfig(
    val questionCount: Int,
    val correctPoint: Float,
    val wrongPenalty: Float,
    val answerKey: List<String>     // ["A","B","C",...]
) {
    fun maxScore(): Float = questionCount * correctPoint
}
