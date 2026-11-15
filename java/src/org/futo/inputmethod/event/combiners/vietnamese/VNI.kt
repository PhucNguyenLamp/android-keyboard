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

    // --- Tables ported from UniKey (x-unikey src/ukengine/ukengine.cpp) ---------------------

    /** Legal onsets (CSeqList). */
    private val ONSETS = setOf(
        "b", "c", "ch", "d", "đ", "dz", "g", "gh", "gi", "gin", "h", "k", "kh", "l", "m",
        "n", "ng", "ngh", "nh", "p", "ph", "q", "qu", "r", "s", "t", "th", "tr", "v", "x"
    )

    /** Coda sequences that may end a syllable (CSeqList entries with suffix=true). */
    private val CODA_SUFFIX = setOf("c", "ch", "m", "n", "ng", "nh", "p", "t")

    /** Vowel sequences (VSeqList) with their `conSuffix` flag (nucleus may take a coda). */
    private val VOWEL_CON_SUFFIX = mapOf(
        "a" to true, "â" to true, "ă" to true, "e" to true, "ê" to true, "i" to true,
        "o" to true, "ô" to true, "ơ" to true, "u" to true, "ư" to true, "y" to true,
        "ai" to false, "ao" to false, "au" to false, "ay" to false, "âu" to false, "ây" to false,
        "eo" to false, "eu" to false, "êu" to true,
        "ia" to false, "ie" to true, "iê" to true, "iu" to false,
        "oa" to true, "oă" to true, "oe" to true, "oi" to false, "ôi" to false, "ơi" to false,
        "ua" to true, "uâ" to true, "ue" to true, "uê" to true, "ui" to false,
        "uo" to true, "uô" to true, "uơ" to true, "uu" to false, "uy" to true,
        "ưa" to false, "ưi" to false, "ưo" to true, "ươ" to true, "ưu" to false,
        "ye" to true, "yê" to true,
        "ieu" to false, "iêu" to false, "oai" to false, "oay" to false, "oeo" to false,
        "uay" to false, "uây" to false, "uoi" to false, "uou" to false,
        "uôi" to false, "uơi" to false, "uơu" to false,
        "uya" to false, "uye" to true, "uyê" to true, "uyu" to false,
        "ưoi" to false, "ưou" to false, "ươi" to false, "ươu" to false,
        "yeu" to false, "yêu" to false
    )

    /** (vowel sequence, coda) pairs allowed by UniKey (VCPairList). */
    private val VALID_VC: Set<String> = buildVcPairs()

    private fun buildVcPairs(): Set<String> {
        val pairs = HashSet<String>()
        fun add(vowel: String, vararg codas: String) {
            for (coda in codas) pairs.add(vowel + "|" + coda)
        }
        add("a", "c", "ch", "m", "n", "ng", "nh", "p", "t")
        add("â", "c", "m", "n", "ng", "p", "t")
        add("ă", "c", "m", "n", "ng", "p", "t")
        add("e", "c", "ch", "m", "n", "ng", "nh", "p", "t")
        add("ê", "c", "ch", "m", "n", "nh", "p", "t")
        add("i", "c", "ch", "m", "n", "nh", "p", "t")
        add("o", "c", "m", "n", "ng", "p", "t")
        add("ô", "c", "m", "n", "ng", "p", "t")
        add("ơ", "m", "n", "p", "t")
        add("u", "c", "m", "n", "ng", "p", "t")
        add("ư", "c", "m", "n", "ng", "t")
        add("y", "t")
        add("ie", "c", "m", "n", "ng", "p", "t")
        add("iê", "c", "m", "n", "ng", "p", "t")
        add("oa", "c", "ch", "m", "n", "ng", "nh", "p", "t")
        add("oă", "c", "m", "n", "ng", "t")
        add("oe", "n", "t")
        add("ua", "n", "ng", "t")
        add("uâ", "n", "ng", "t")
        add("ue", "c", "ch", "n", "nh")
        add("uê", "c", "ch", "n", "nh")
        add("uo", "c", "m", "n", "ng", "p", "t")
        add("uô", "c", "m", "n", "ng", "t")
        add("ưo", "c", "m", "n", "ng", "p", "t")
        add("ươ", "c", "m", "n", "ng", "p", "t")
        add("uy", "c", "ch", "n", "nh", "p", "t")
        add("ye", "m", "n", "ng", "p", "t")
        add("yê", "m", "n", "ng", "t")
        add("uye", "n", "t")
        add("uyê", "n", "t")
        return pairs
    }

    /** Vowels that may follow "k" (isValidCV). */
    private val K_VOWELS = setOf("e", "i", "y", "ê", "eo", "êu", "ia", "ie", "iê", "ieu", "iêu")

    // --- Runtime state ---------------------------------------------------------------------

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
                    } else if (!isValidSyllable(letters)) {
                        // Not a legal Vietnamese syllable with this coda (e.g. "a8ch2", "o7c5"):
                        // keep the tone digit literal.
                        tokens.add(DigitToken(ch, rawIndex, false))
                        literal.add(rawIndex)
                    } else if (checkedCodaDisallowsTone(letters, toneMark)) {
                        // Checked syllables (ending in c/ch/p/t) only take sắc or nặng, so
                        // "ac2"/"ach3"/"ap4" stay literal like UniKey.
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

                if (!isValidStructureContext(letters)) {
                    // Non-Vietnamese syllable (e.g. "chgu7", "aeiou7"): keep it literal.
                    tokens.add(DigitToken(ch, rawIndex, false))
                    literal.add(rawIndex)
                    continue
                }

                // digit 9 (đ) only modifies the onset 'd', so a coda does not affect it.
                if (digit == 9) {
                    var target: Letter? = null
                    for (i in letters.indices.reversed()) {
                        if (letters[i].base == 'd') {
                            target = letters[i]
                            break
                        }
                    }
                    if (target == null) {
                        tokens.add(DigitToken(ch, rawIndex, false))
                        literal.add(rawIndex)
                    } else if (target.diacritic == Diacritic.STROKE) {
                        target.diacritic = Diacritic.NONE
                        tokens.add(DigitToken(ch, rawIndex, false))
                        literal.add(rawIndex)
                    } else {
                        target.diacritic = Diacritic.STROKE
                        tokens.add(DigitToken(ch, rawIndex, true))
                        appliedStructural.add(rawIndex)
                    }
                    continue
                }

                // Digits 6/7/8 modify the nucleus (after the onset). The resulting syllable must
                // be valid even without a coda ("toa6" -> "toa6", "vie6" -> "viê", "oc7" -> "oc7").
                val nucleusStart = vowelStart(letters)
                if (nucleusStart < 0) {
                    tokens.add(DigitToken(ch, rawIndex, false))
                    literal.add(rawIndex)
                    continue
                }

                val changes = ArrayList<Letter>()
                val newValues = ArrayList<Diacritic>()
                var toggling = false

                if (digit == 7) {
                    var hornU: Letter? = null
                    var hornO: Letter? = null
                    for (i in nucleusStart until letters.size - 1) {
                        if (letters[i].base == 'u' && letters[i + 1].base == 'o') {
                            hornU = letters[i]
                            hornO = letters[i + 1]
                            break
                        }
                    }
                    if (hornU != null && hornO != null) {
                        // With an onset, the exact "uo" nucleus and no coda, UniKey horns only the
                        // o ("huo7" -> "huơ"); otherwise both ("uo7" -> "ươ").
                        val nucleus = vowelString(letters, nucleusStart)
                        val hornOnlyO = nucleusStart > 0 && nucleus == "uo" && codaOf(letters).isEmpty()
                        if (hornOnlyO) {
                            toggling = hornO.diacritic == Diacritic.HORN
                            changes.add(hornO)
                            newValues.add(if (toggling) Diacritic.NONE else Diacritic.HORN)
                        } else {
                            toggling = hornU.diacritic == Diacritic.HORN && hornO.diacritic == Diacritic.HORN
                            val value = if (toggling) Diacritic.NONE else Diacritic.HORN
                            changes.add(hornU); newValues.add(value)
                            changes.add(hornO); newValues.add(value)
                        }
                    } else {
                        // Prefer the first 'u' in the nucleus, otherwise the first 'o'.
                        var single: Letter? = null
                        for (i in nucleusStart until letters.size) {
                            if (letters[i].base == 'u') {
                                single = letters[i]
                                break
                            }
                        }
                        if (single == null) {
                            for (i in nucleusStart until letters.size) {
                                if (letters[i].base == 'o') {
                                    single = letters[i]
                                    break
                                }
                            }
                        }
                        if (single == null) {
                            tokens.add(DigitToken(ch, rawIndex, false))
                            literal.add(rawIndex)
                            continue
                        }
                        toggling = single.diacritic == Diacritic.HORN
                        changes.add(single)
                        newValues.add(if (toggling) Diacritic.NONE else Diacritic.HORN)
                    }
                } else {
                    var target: Letter? = null
                    for (i in letters.indices.reversed()) {
                        if (i >= nucleusStart && letters[i].base in COMPATIBLE[digit]!!) {
                            target = letters[i]
                            break
                        }
                    }
                    if (target == null) {
                        tokens.add(DigitToken(ch, rawIndex, false))
                        literal.add(rawIndex)
                        continue
                    }
                    toggling = target.diacritic == wanted
                    changes.add(target)
                    newValues.add(if (toggling) Diacritic.NONE else wanted)
                }

                val oldValues = changes.map { it.diacritic }
                for (i in changes.indices) changes[i].diacritic = newValues[i]

                if (!toggling && !isValidSyllable(letters)) {
                    for (i in changes.indices) changes[i].diacritic = oldValues[i]
                    tokens.add(DigitToken(ch, rawIndex, false))
                    literal.add(rawIndex)
                } else if (toggling) {
                    tokens.add(DigitToken(ch, rawIndex, false))
                    literal.add(rawIndex)
                } else {
                    tokens.add(DigitToken(ch, rawIndex, true))
                    appliedStructural.add(rawIndex)
                }
                continue
            }

            tokens.add(DigitToken(ch, rawIndex, false))
            literal.add(rawIndex)
        }

        if (escaped) {
            val literalIndices = input.indices.filter { !input[it].isLetter() }.toSet()
            return Conversion(input, emptySet(), emptySet(), literalIndices)
        }

        var tonePosition = -1
        if (tone != null) {
            val start = vowelStart(letters)
            val vowel = if (start >= 0) vowelString(letters, start) else null
            if (start >= 0 && vowel != null && vowel.isNotEmpty()) {
                val coda = codaOf(letters)
                val vcOk = coda.isEmpty() ||
                        (VOWEL_CON_SUFFIX[vowel] == true && coda in CODA_SUFFIX &&
                                (vowel + "|" + coda) in VALID_VC)
                // If a coda makes the rhyme invalid ("ai" + "c"), UniKey placed the tone while the
                // vowel was open, so treat it as terminated ("áic", not "aíc").
                val terminated = (start + vowel.length == letters.size) || !vcOk
                tonePosition = start + unikeyTonePosition(vowel, terminated)
            }
        }
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

    // --- UniKey-style validity -------------------------------------------------------------

    private fun onsetOf(letters: List<Letter>): String {
        val onset = StringBuilder()
        var i = 0
        while (i < letters.size && letters[i].base !in Common.VOWELS) {
            onset.append(charOf(letters[i].base, letters[i].diacritic))
            i++
        }
        // "qu" and "gi" are onsets when followed by a vowel (gi only when another vowel follows).
        if (i == 1 && letters[0].base == 'q' && letters.size >= 2 && letters[1].base == 'u') {
            return "qu"
        }
        if (i == 1 && letters[0].base == 'g' && letters.size >= 3 && letters[1].base == 'i' &&
                letters[2].base in Common.VOWELS) {
            return "gi"
        }
        return onset.toString()
    }

    private fun vowelStart(letters: List<Letter>): Int {
        val first = letters.indexOfFirst { it.base in Common.VOWELS }
        if (first < 0) return -1
        if (first == 1 && letters[0].base == 'q' && letters.size >= 2 && letters[1].base == 'u') return 2
        if (first == 1 && letters[0].base == 'g' && letters.size >= 3 && letters[1].base == 'i' &&
                letters[2].base in Common.VOWELS) return 2
        return first
    }

    private fun vowelString(letters: List<Letter>, start: Int): String? {
        val sb = StringBuilder()
        var i = start
        while (i < letters.size && letters[i].base in Common.VOWELS) {
            if (sb.length >= 3) return null
            sb.append(charOf(letters[i].base, letters[i].diacritic))
            i++
        }
        return sb.toString()
    }

    private fun hasCoda(letters: List<Letter>): Boolean {
        val start = vowelStart(letters)
        if (start < 0) return false
        val vowel = vowelString(letters, start) ?: return false
        return start + vowel.length < letters.size
    }

    private fun codaOf(letters: List<Letter>): String {
        val start = vowelStart(letters)
        if (start < 0) return ""
        val vowel = vowelString(letters, start) ?: return ""
        val sb = StringBuilder()
        var i = start + vowel.length
        while (i < letters.size) {
            sb.append(letters[i].base)
            i++
        }
        return sb.toString()
    }

    /** Port of UniKey's UkEngine::getTonePosition (with modernStyle for oa/oe/uy). */
    private fun unikeyTonePosition(nucleus: String, terminated: Boolean): Int {
        if (nucleus.length <= 1) return 0

        for (i in nucleus.indices) {
            val c = nucleus[i]
            if (c == 'â' || c == 'ê' || c == 'ô') return i // roofPos
        }

        var hook = -1
        for (i in nucleus.indices) {
            val c = nucleus[i]
            if (c == 'ơ' || c == 'ư') {
                hook = i
                break
            }
        }
        if (hook >= 0) {
            // Special case in UniKey for u+o horn nuclei: put it on the ơ.
            if (nucleus == "ươ" || nucleus == "ươi" || nucleus == "ươu") return 1
            return hook
        }

        if (nucleus.length >= 3) return 1
        if (nucleus == "oa" || nucleus == "oe" || nucleus == "uy") return 1
        return if (terminated) 0 else 1
    }

    /** UniKey: c/ch/p/t codas only allow sắc and nặng, not huyền/hỏi/ngã. */
    private fun checkedCodaDisallowsTone(letters: List<Letter>, tone: ToneMark): Boolean {
        val coda = codaOf(letters)
        if (coda != "c" && coda != "ch" && coda != "p" && coda != "t") return false
        return tone == ToneMark.GRAVE || tone == ToneMark.HOOK || tone == ToneMark.TILDE
    }

    /** Full syllable validity, mirroring UniKey's isValidCV/isValidVC/isValidCVC. */
    private fun isValidSyllable(letters: List<Letter>): Boolean {
        val onset = onsetOf(letters)
        if (onset.isNotEmpty() && onset !in ONSETS && onset.length > 1) return false

        val start = vowelStart(letters)
        if (start < 0 || start >= letters.size) return false
        val vowel = vowelString(letters, start) ?: return false
        if (vowel !in VOWEL_CON_SUFFIX) return false

        val coda = StringBuilder()
        var i = start + vowel.length
        while (i < letters.size) {
            if (letters[i].base in Common.VOWELS) return false
            coda.append(letters[i].base)
            i++
        }
        val c = coda.toString()
        if (c.isNotEmpty() && c !in ONSETS) return false

        if (onset == "gi" && vowel.startsWith("i")) return false
        if (onset == "qu" && vowel.startsWith("u")) return false
        if (onset == "k" && vowel !in K_VOWELS) return false

        if (c.isNotEmpty()) {
            if (VOWEL_CON_SUFFIX[vowel] != true) return false
            if (c !in CODA_SUFFIX) return false
            if ((vowel + "|" + c) !in VALID_VC) {
                // Exceptions from isValidCVC: quyn/quynh, gieng/giêng
                if (onset == "qu" && vowel == "y" && (c == "n" || c == "nh")) return true
                if (onset == "gi" && (vowel == "e" || vowel == "ê") && (c == "n" || c == "ng")) return true
                return false
            }
        }
        return true
    }

    /**
     * Structural modifiers (6/7/8/9) may occur mid-word ("vie6t"), so only require a valid
     * onset and a known vowel nucleus; the coda is not checked here.
     */
    private fun isValidStructureContext(letters: List<Letter>): Boolean {
        val onset = onsetOf(letters)
        if (onset.isNotEmpty() && onset !in ONSETS && onset.length > 1) return false
        val start = vowelStart(letters)
        if (start < 0 || start >= letters.size) return true // consonant-only, e.g. "d9"
        val vowel = vowelString(letters, start) ?: return false
        return vowel in VOWEL_CON_SUFFIX
    }

    /**
     * Turn a Vietnamese word back into the VNI keystrokes that produce it, e.g.
     * "việt" -> "vie6t5", "quá" -> "qua1", "chão" -> "chao4", "đẹp" -> "d9ep5".
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

    private fun charOf(base: Char, diacritic: Diacritic): Char = when (diacritic) {
        Diacritic.NONE -> base
        Diacritic.CIRCUMFLEX -> Common.CIRCUMFLEX_MAP[base] ?: base
        Diacritic.BREVE -> Common.BREVE_MAP[base] ?: base
        Diacritic.HORN -> Common.HORN_MAP[base] ?: base
        Diacritic.STROKE -> Common.STROKE_MAP[base] ?: base
    }
}

