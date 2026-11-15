package org.futo.inputmethod.event.combiners.vietnamese

import android.text.TextUtils
import org.futo.inputmethod.event.Combiner
import org.futo.inputmethod.event.Event
import org.futo.inputmethod.latin.common.Constants
import java.util.ArrayList

class VNICombiner: Combiner {
    private val buffer = StringBuilder() // holds a single Vietnamese word/syllable

    override fun processEvent(
        previousEvents: ArrayList<Event?>?,
        event: Event?
    ): Event {
        if (event == null) return Event.createNotHandledEvent()
        if (event.eventType != Event.EVENT_TYPE_INPUT_KEYPRESS) return event

        val keypress = event.mCodePoint.toChar()

        // The normal ASCII digits are left untouched by the combiner and always result in digits
        // being committed to the output. On the other hand, fullwidth digits are intercepted by
        // this combiner, converted into ASCII digits, and sent to the VNI converter.
        // This lets the user explicitly enter numbers that will not get converted into diacritics.
        // For example, if ASCII '1' (U+0031 DIGIT ONE) is given to this combiner, it will always
        // output an ASCII '1' (U+0031).
        // But if a fullwidth '１' (U+FF11 FULLWIDTH DIGIT ONE) is given to this combiner, it will be
        // converted to an ASCII '1' (U+0031) and given to the VNI converter, where it might result
        // in an acute accent being placed over a letter.
        // So, the input sequence [V][i][e][t][U+FF15][U+FF16] will result in the output "Việt"
        if (keypress.code in 0xFF10..0xFF19) {
            buffer.append((keypress.code - 0xFEE0).toChar())
            return Event.createConsumedEvent(event)
        }

        if (!(keypress in 'A'..'Z' || keypress in 'a'..'z')) {
            if (!TextUtils.isEmpty(buffer)) {
                if (event.mKeyCode == Constants.CODE_DELETE) {
                    smartBackspace()
                    return Event.createConsumedEvent(event)
                }
            }

            if(!event.isFunctionalKeyEvent) return Event.createResetEvent(event)
            return event
        }

        buffer.append(keypress)
        return Event.createConsumedEvent(event)
    }

    /**
     * Backspace behaves like UniKey: it deletes the last displayed character (one code point),
     * then rebuilds the raw VNI keys for the remaining word so it can still be edited.
     * For example "chào" -> "chà" -> "ch", and "đường" -> "đườn" -> "đườ".
     */
    private fun smartBackspace() {
        val text = try {
            VNI.convert(buffer.toString()).text
        } catch (e: Exception) {
            null
        }

        if (text == null) {
            if (buffer.isNotEmpty()) buffer.deleteCharAt(buffer.length - 1)
            return
        }
        if (text.isEmpty()) {
            buffer.clear()
            return
        }

        val lastCodePoint = text.codePointBefore(text.length)
        val newText = text.substring(0, text.length - Character.charCount(lastCodePoint))
        buffer.setLength(0)
        buffer.append(VNI.vietnameseToVNI(newText))
    }

    override fun getCombiningStateFeedback(): CharSequence? =
        try{
            VNI.VNIToVietnamese(buffer.toString())
        } catch(e: Exception) {
            buffer
        }

    override fun reset() {
        buffer.clear()
    }
}
