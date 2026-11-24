package com.miniichat.ui

private val slashCommand = Regex("/(?=(?:left|right|frac|sqrt|times|cdot)\\b)")
private val inlineMath = Regex(
    """\\\(.*?\\\)|\\\[.*?\\\]|\x24\x24.*?\x24\x24|\x24[^\x24\r\n]+\x24""",
    RegexOption.DOT_MATCHES_ALL
)
private val divisionSlash = Regex("""(?<!/)[ \t]*[/⁄][ \t]*(?!/)""")

internal fun readableMathSymbol(command: String): String? = symbols[command]

/** Converts common TeX notation into readable text on the small watch display. */
internal fun readableMath(source: String, mathBlock: Boolean = false): String {
    val normalized = slashCommand.replace(source) { "\\" }
    val spaced = if (mathBlock) normalized.replace(divisionSlash, " / ")
    else inlineMath.replace(normalized) { it.value.replace(divisionSlash, " / ") }
    return MathReader(spaced
        .replace("\\(", "").replace("\\)", "")
        .replace("\\[", "").replace("\\]", "")
        .replace(Regex("\\$([^$]+)\\$"), "$1")
        .replace("$$", "")
    ).read()
}

private class MathReader(private val source: String) {
    private var position = 0

    fun read(until: Char? = null): String = buildString {
        while (position < source.length) {
            val c = source[position++]
            if (c == until) break
            when (c) {
                '\\' -> append(command())
                '{' -> append(read('}'))
                '^', '_' -> {
                    val atom = atom()
                    val mapped = atom.map { symbol ->
                        (if (c == '^') superscripts else subscripts)[symbol]
                    }
                    if (mapped.all { it != null }) mapped.forEach { append(it) }
                    else append(if (c == '^') "^(" else "_(").append(atom).append(')')
                }
                else -> append(c)
            }
        }
    }

    private fun atom(): String {
        if (position >= source.length) return ""
        return when (source[position]) {
            '{' -> { position++; read('}') }
            '\\' -> { position++; command() }
            else -> source[position++].toString()
        }
    }

    private fun command(): String {
        if (position >= source.length) return "\\"
        if (source[position] == '\\') { position++; return "\n" }
        val start = position
        while (position < source.length && source[position].isLetter()) position++
        if (start == position) return source[position++].toString()
        val name = source.substring(start, position)
        return when (name) {
            "left", "right" -> ""
            "frac", "dfrac", "tfrac" -> {
                val numerator = atom()
                val denominator = atom()
                val top = if (numerator.length > 1) "($numerator)" else numerator
                val bottom = if (denominator.length > 1) "($denominator)" else denominator
                "$top / $bottom"
            }
            "sqrt" -> {
                val degree = if (position < source.length && source[position] == '[') {
                    position++; read(']')
                } else ""
                val value = atom()
                if (degree.isEmpty()) "√($value)" else "${degree}√($value)"
            }
            "text", "mathrm", "mathbf", "mathit", "operatorname" -> atom()
            "begin", "end" -> { atom(); "" }
            else -> symbols[name] ?: name
        }
    }
}

private val superscripts = mapOf(
    '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
    '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
    '+' to '⁺', '-' to '⁻', '=' to '⁼', 'n' to 'ⁿ', 'i' to 'ⁱ'
)
private val subscripts = mapOf(
    '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
    '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
    '+' to '₊', '-' to '₋', '=' to '₌', 'a' to 'ₐ', 'e' to 'ₑ',
    'i' to 'ᵢ', 'o' to 'ₒ', 'r' to 'ᵣ', 'u' to 'ᵤ', 'x' to 'ₓ'
)
private val symbols = mapOf(
    "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ",
    "epsilon" to "ε", "varepsilon" to "ε", "theta" to "θ", "lambda" to "λ",
    "mu" to "μ", "nu" to "ν", "pi" to "π", "rho" to "ρ",
    "sigma" to "σ", "phi" to "φ", "omega" to "ω", "Omega" to "Ω",
    "Delta" to "Δ", "infty" to "∞", "times" to "×", "cdot" to "·",
    "pm" to "±", "mp" to "∓", "leq" to "≤", "le" to "≤",
    "geq" to "≥", "ge" to "≥", "neq" to "≠", "approx" to "≈",
    "equiv" to "≡", "propto" to "∝", "sum" to "∑", "prod" to "∏",
    "int" to "∫", "partial" to "∂", "nabla" to "∇", "hbar" to "ℏ",
    "rightarrow" to "→", "leftarrow" to "←", "to" to "→",
    "degree" to "°", "circ" to "°", "ldots" to "…", "cdots" to "⋯",
    "in" to "∈", "notin" to "∉", "forall" to "∀", "exists" to "∃",
    "parallel" to "∥", "perp" to "⊥", "angle" to "∠"
)
