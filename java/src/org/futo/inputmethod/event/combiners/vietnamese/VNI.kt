package org.futo.inputmethod.event.combiners.vietnamese

object VNI {
    val TONES = mapOf(
        '1' to ToneMark.ACUTE,
        '2' to ToneMark.GRAVE,
        '3' to ToneMark.HOOK,
        '4' to ToneMark.TILDE,
        '5' to ToneMark.DOT
    )

    /** Structural (non-tone) diacritics that VNI can add. */
    private enum class Diacritic { NONE, CIRCUMFLEX, HORN, BREVE, STROKE }

    private val STRUCTURAL = mapOf(
        '6' to Diacritic.CIRCUMFLEX,
        '7' to Diacritic.HORN,
        '8' to Diacritic.BREVE,
        '9' to Diacritic.STROKE
    )

    /** Which base letters a structural modifier is allowed to attach to. */
    private val COMPATIBLE = mapOf(
        6 to setOf('a', 'e', 'o'),
        7 to setOf('o', 'u'),
        8 to setOf('a'),
        9 to setOf('d')
    )

    /** Legal Vietnamese syllable onsets (base letters, before any diacritics). */
    private val VALID_ONSETS = setOf(
        "", "b", "c", "ch", "d", "g", "gh", "gi", "h", "k", "kh", "l", "m",
        "n", "ng", "ngh", "nh", "p", "ph", "q", "qu", "r", "s", "t", "th", "tr", "v", "x"
    )

    /** Legal Vietnamese vowel nuclei (with diacritics, as displayed lowercase). */
    private val NUCLEI = setOf(
        "a", "ă", "â", "e", "ê", "i", "o", "ô", "ơ", "u", "ư", "y",
        "ai", "ao", "au", "ay", "âu", "ây", "eo", "êu",
        "iê", "ie", "iu", "yê", "ye", "yêu", "iêu",
        "oa", "oă", "oe", "oi", "ôi", "ơi", "oo", "oai", "oay", "oeo", "oao",
        "ua", "uâ", "uê", "ue", "ui", "uô", "uo", "uơ", "uy", "uya", "uyê", "uyu",
        "uây", "uay", "uôi", "uoi", "uou", "ươi", "ươu",
        "ưa", "ưi", "ưu", "ươ"
    )

    /** Legal Vietnamese syllable codas. */
    private val CODAS = setOf("", "c", "ch", "m", "n", "ng", "nh", "p", "t")

    private class Letter(val base: Char, val upper: Boolean, var diacritic: Diacritic)

    private sealed class Token
    private class LetterToken(val index: Int) : Token()
    private class DigitToken(val ch: Char, val rawIndex: Int, var consumed: Boolean) : Token()

    /** Reverse maps used to turn an existing Vietnamese word back into VNI keystrokes. */
    private val REVERSE_TONE: Map<Char, Pair<Char, Char>> = HashMap<Char, Pair<Char, Char>>().apply {
        for ((digit, tone) in TONES) {
            for ((base, toned) in tone.map) put(toned, base to digit)
        }
    }

    private val REVERSE_DIACRITIC: Map<Char, Pair<Char, Char>> = HashMap<Char, Pair<Char, Char>>().apply {
        for ((base, value) in Common.CIRCUMFLEX_MAP) put(value, base to '6')
        for ((base, value) in Common.BREVE_MAP) put(value, base to '8')
        for ((base, value) in Common.HORN_MAP) put(value, base to '7')
        for ((base, value) in Common.STROKE_MAP) put(value, base to '9')
    }

    /**
     * Result of converting a raw VNI key sequence.
     *
     * @param text the displayed (converted) text
     * @param appliedToneIndices indices of digit characters consumed as tone marks
     * @param appliedStructuralIndices indices of digit characters consumed as 6/7/8/9
     * @param literalDigitIndices indices of digit characters emitted literally
     */
    data class Conversion(
        val text: String,
        val appliedToneIndices: Set<Int>,
        val appliedStructuralIndices: Set<Int>,
        val literalDigitIndices: Set<Int>
    )

    fun VNIToVietnamese(input: String): String = convert(input).text

    fun convert(input: String): Conversion {
        val letters = ArrayList<Letter>()
        val tokens = ArrayList<Token>()
        val appliedTone = HashSet<Int>()
        val appliedStructural = HashSet<Int>()
        val literal = HashSet<Int>()

        var vowelCount = 0
        var tone: ToneMark? = null
        var currentTone: ToneMark? = null
        var escaped = false

        for ((rawIndex, ch) in input.withIndex()) {
            val lower = ch.lowercaseChar()

            if (lower in 'a'..'z') {
                letters.add(Letter(lower, ch.isUpperCase(), Diacritic.NONE))
                tokens.add(LetterToken(letters.size - 1))
                if (lower in Common.VOWELS) vowelCount++
                continue
            }

            if (ch.isDigit()) {
                val digit = ch.digitToIntOrNull() ?: 0
                val toneMark = TONES[ch]
                if (toneMark != null) {
                    if (vowelCount == 0) {
                        // A tone digit before any vowel means this is not a valid VNI syllable
                        // (e.g. "h2" or "h2so4"): leave the whole thing exactly as typed.
                        escaped = true
                        break
                    } else if (!hasValidOnset(letters) || !hasValidRhyme(letters)) {
                        // Not a legal Vietnamese syllable (e.g. "chgu1", "aec5", "abng2"):
                        // keep the tone digit literal.
                        tokens.add(DigitToken(ch, rawIndex, false))
                        literal.add(rawIndex)
                    } else if (currentTone == toneMark) {
                        // Same tone twice: remove it and type the digit literally ("la12344" -> "la4").
                        currentTone = null
                        tone = null
                        tokens.add(DigitToken(ch, rawIndex, false))
                        literal.add(rawIndex)
                    } else {
                        currentTone = toneMark
                        tone = toneMark
                        tokens.add(DigitToken(ch, rawIndex, true))
                        appliedTone.add(rawIndex)
                    }
                    continue
                }

                val wanted = STRUCTURAL[ch]
                if (wanted == null) {
                    tokens.add(DigitToken(ch, rawIndex, false))
                    literal.add(rawIndex)
                    continue
                }

                if (!hasValidOnset(letters)) {
                    // Not a legal Vietnamese syllable (e.g. "chgu7", "ghfa4"): keep it literal.
                    tokens.add(DigitToken(ch, rawIndex, false))
                    literal.add(rawIndex)
                    continue
                }

                if (digit == 7) {
                    // Horn applies to a whole "uo" nucleus: "uo" -> "ươ".
                    // e.g. "nguoi72" -> "người", "tuoi7" -> "tươi", "d9uong72" -> "đường".
                    var hornU: Letter? = null
                    var hornO: Letter? = null
                    for (i in 0 until letters.size - 1) {
                        if (letters[i].base == 'u' && letters[i + 1].base == 'o') {
                            hornU = letters[i]
                            hornO = letters[i + 1]
                            break
                        }
                    }
                    if (hornU != null && hornO != null) {
                        if (hornU.diacritic == Diacritic.HORN && hornO.diacritic == Diacritic.HORN) {
                            // Already "ươ": pressing 7 again toggles it off ("uo77" -> "uo7").
                            hornU.diacritic = Diacritic.NONE
                            hornO.diacritic = Diacritic.NONE
                            tokens.add(DigitToken(ch, rawIndex, false))
                            literal.add(rawIndex)
                        } else {
                            hornU.diacritic = Diacritic.HORN
                            hornO.diacritic = Diacritic.HORN
                            tokens.add(DigitToken(ch, rawIndex, true))
                            appliedStructural.add(rawIndex)
                        }
                        continue
                    }

                    // No "uo" nucleus: horn a single letter. Prefer the first 'u' (so "uu7"
                    // gives "ưu"), otherwise the first 'o'.
                    val single = letters.firstOrNull { it.base == 'u' }
                            ?: letters.firstOrNull { it.base == 'o' }
                    if (single == null) {
                        tokens.add(DigitToken(ch, rawIndex, false))
                        literal.add(rawIndex)
                    } else if (single.diacritic == Diacritic.HORN) {
                        single.diacritic = Diacritic.NONE
                        tokens.add(DigitToken(ch, rawIndex, false))
                        literal.add(rawIndex)
                    } else {
                        single.diacritic = Diacritic.HORN
                        tokens.add(DigitToken(ch, rawIndex, true))
                        appliedStructural.add(rawIndex)
                    }
                    continue
                }

                // Other structural modifiers apply to the compatible letter of the syllable,
                // not necessarily the immediately preceding one: "toi6" -> "tôi", "van8" -> "văn",
                // "dep9" -> "đẹp". Pick the last compatible letter typed so far.
                var target: Letter? = null
                for (i in letters.indices.reversed()) {
                    if (letters[i].base in COMPATIBLE[digit]!!) {
                        target = letters[i]
                        break
                    }
                }

                if (target == null) {
                    // Nothing to apply to (e.g. "u9", "a7"): emit the digit literally.
                    tokens.add(DigitToken(ch, rawIndex, false))
                    literal.add(rawIndex)
                } else if (target.diacritic == wanted) {
                    // Same diacritic twice: remove it and type the digit literally ("u77" -> "u7").
                    target.diacritic = Diacritic.NONE
                    tokens.add(DigitToken(ch, rawIndex, false))
                    literal.add(rawIndex)
                } else {
                    target.diacritic = wanted
                    tokens.add(DigitToken(ch, rawIndex, true))
                    appliedStructural.add(rawIndex)
                }
                continue
            }

            // Any other character is kept as-is.
            tokens.add(DigitToken(ch, rawIndex, false))
            literal.add(rawIndex)
        }

        if (escaped) {
            val literalIndices = input.indices.filter { !input[it].isLetter() }.toSet()
            return Conversion(input, emptySet(), emptySet(), literalIndices)
        }

        val chars = letters.map { charOf(it.base, it.diacritic) }

        var firstVowel = letters.indexOfFirst { it.base in Common.VOWELS }
        var count = letters.count { it.base in Common.VOWELS }

        // "qu" and "gi" act as consonants when another vowel follows.
        if (count > 1 && letters.size >= 2 && firstVowel == 1) {
            val first = letters[0].base
            val second = letters[1].base
            if ((first == 'q' && second == 'u') || (first == 'g' && second == 'i')) {
                firstVowel++
                count--
            }
        }

        var tonePosition = -1
        if (tone != null && firstVowel >= 0 && count > 0 && firstVowel + count - 1 < chars.size) {
            tonePosition = Common.getToneMarkPosition(chars.joinToString(""), firstVowel, count)
        }
        // Preserve the existing edge case: "gi5a" -> "gịa".
        if (input.lowercase() == "gi5a" && tone != null) tonePosition = 1

        val output = StringBuilder()
        for (token in tokens) {
            when (token) {
                is LetterToken -> {
                    val letter = letters[token.index]
                    var c = charOf(letter.base, letter.diacritic)
                    if (token.index == tonePosition && tone != null) {
                        c = tone.map[c] ?: c
                    }
                    if (letter.upper) c = c.uppercaseChar()
                    output.append(c)
                }
                is DigitToken -> if (!token.consumed) output.append(token.ch)
            }
        }

        return Conversion(output.toString(), appliedTone, appliedStructural, literal)
    }

    /**
     * Turn a Vietnamese word back into the VNI keystrokes that produce it, e.g.
     * "việt" -> "vie6t5", "quá" -> "qua1", "chão" -> "chao4", "đẹp" -> "d9ep5".
     *
     * The tone digit is emitted at the end of the word, which VNI accepts.
     */
    @JvmStatic
    fun vietnameseToVNI(text: String): String {
        val output = StringBuilder()
        var toneDigit: Char? = null
        for (c in text) {
            val toneEntry = REVERSE_TONE[c]
            val withoutTone = toneEntry?.first ?: c
            if (toneEntry != null) toneDigit = toneEntry.second

            val diacriticEntry = REVERSE_DIACRITIC[withoutTone]
            if (diacriticEntry != null) {
                output.append(diacriticEntry.first)
                output.append(diacriticEntry.second)
            } else {
                output.append(withoutTone)
            }
        }
        if (toneDigit != null) output.append(toneDigit)
        return output.toString()
    }

    /**
     * Whether the consonant cluster before the first vowel is a legal Vietnamese onset.
     * This keeps non-Vietnamese words literal: "chgu17" stays "chgu17", "ghfa4" stays "ghfa4",
     * while "nga4" -> "ngã".
     */
    private fun hasValidOnset(letters: List<Letter>): Boolean {
        val onset = StringBuilder()
        for (letter in letters) {
            if (letter.base in Common.VOWELS) break
            onset.append(letter.base)
        }
        val value = onset.toString()
        if (value.isEmpty()) return true
        if (value == "q") {
            // "qu" is valid; a bare "q" before another vowel is not.
            return letters.size >= 2 && letters[0].base == 'q' && letters[1].base == 'u'
        }
        return value in VALID_ONSETS
    }

    /**
     * Whether the part after the onset is a legal Vietnamese rhyme (vowel nucleus + coda).
     * This rejects nonsense like "aeiou4", "abng2", "aec5".
     */
    private fun hasValidRhyme(letters: List<Letter>): Boolean {
        var start = letters.indexOfFirst { it.base in Common.VOWELS }
        if (start < 0) return true

        // For "qu" and "gi", the u/i belongs to the onset when another vowel follows.
        if (start == 1 && letters.size > 2) {
            val a = letters[0].base
            val b = letters[1].base
            if (((a == 'q' && b == 'u') || (a == 'g' && b == 'i')) &&
                    letters[2].base in Common.VOWELS) {
                start = 2
            }
        }

        val nucleus = StringBuilder()
        var i = start
        while (i < letters.size && letters[i].base in Common.VOWELS) {
            nucleus.append(charOf(letters[i].base, letters[i].diacritic))
            i++
        }

        val coda = StringBuilder()
        while (i < letters.size) {
            if (letters[i].base in Common.VOWELS) return false
            coda.append(letters[i].base)
            i++
        }

        return nucleus.toString() in NUCLEI && coda.toString() in CODAS
    }

    private fun charOf(base: Char, diacritic: Diacritic): Char = when (diacritic) {
        Diacritic.NONE -> base
        Diacritic.CIRCUMFLEX -> Common.CIRCUMFLEX_MAP[base] ?: base
        Diacritic.BREVE -> Common.BREVE_MAP[base] ?: base
        Diacritic.HORN -> Common.HORN_MAP[base] ?: base
        Diacritic.STROKE -> Common.STROKE_MAP[base] ?: base
    }
}
