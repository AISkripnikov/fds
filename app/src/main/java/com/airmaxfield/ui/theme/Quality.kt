package com.airmaxfield.ui.theme

enum class Quality { GOOD, FAIR, BAD, UNKNOWN }

/** Сигнал, dBm: ≥ −65 хорошо, −65…−75 средне, < −75 плохо. */
fun signalQuality(dbm: Int?): Quality = when {
    dbm == null -> Quality.UNKNOWN
    dbm >= -65 -> Quality.GOOD
    dbm >= -75 -> Quality.FAIR
    else -> Quality.BAD
}

/** SNR, dB: ≥ 25 хорошо, 15…25 средне, < 15 плохо. */
fun snrQuality(db: Int?): Quality = when {
    db == null -> Quality.UNKNOWN
    db >= 25 -> Quality.GOOD
    db >= 15 -> Quality.FAIR
    else -> Quality.BAD
}

/** airMAX качество / ёмкость / CCQ, %: ≥ 80 хорошо, 50…80 средне, < 50 плохо. */
fun percentQuality(p: Int?): Quality = when {
    p == null -> Quality.UNKNOWN
    p >= 80 -> Quality.GOOD
    p >= 50 -> Quality.FAIR
    else -> Quality.BAD
}
