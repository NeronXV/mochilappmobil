package com.mochilapp.mobile.ui.screens

fun formatMxn(amount: Double): String {
    val hasCents = amount % 1.0 != 0.0
    val formatted = java.text.NumberFormat.getNumberInstance(java.util.Locale("es", "MX")).apply {
        maximumFractionDigits = if (hasCents) 2 else 0
        minimumFractionDigits = if (hasCents) 2 else 0
    }.format(amount)
    return "$$formatted"
}

// Capitaliza ubicaciones capturadas a mano por los negocios ("la paz, bcs" -> "La Paz, BCS")
fun displayLocation(raw: String): String {
    val particles = setOf("de", "del", "la", "las", "los", "el", "y", "en")
    val abbreviations = setOf("bcs", "bc", "cdmx", "qroo", "edomex", "nl")
    return raw.trim().split(Regex("\\s+")).mapIndexed { index, word ->
        val suffix = word.takeLastWhile { !it.isLetterOrDigit() }
        val core = word.dropLast(suffix.length)
        val lower = core.lowercase()
        val cased = when {
            lower in abbreviations -> lower.uppercase()
            index > 0 && lower in particles -> lower
            else -> lower.replaceFirstChar { it.titlecase() }
        }
        cased + suffix
    }.joinToString(" ")
}
